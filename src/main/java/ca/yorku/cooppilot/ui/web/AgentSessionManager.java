package ca.yorku.cooppilot.ui.web;

import ca.yorku.cooppilot.agent.AgentResult;
import ca.yorku.cooppilot.app.CoopPilotFacade;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Runs agent sessions in the background for the web GUI and streams their events to browsers over
 * Server-Sent Events. Events are buffered so a page that connects late (or reconnects) sees all of them.
 */
@Component
public class AgentSessionManager {

    /** One event sent to the page: type is thinking | tool | result | question | done | error. */
    public record Event(String type, Map<String, Object> data) {}

    static final class WebRun {
        final String id;
        final String postingId;
        final List<Event> events = new CopyOnWriteArrayList<>();
        final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
        final LinkedBlockingQueue<String> answers = new LinkedBlockingQueue<>();
        volatile String pendingQuestion;
        volatile AgentResult result;

        WebRun(String id, String postingId) {
            this.id = id;
            this.postingId = postingId;
        }
    }

    private final CoopPilotFacade facade;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Map<String, WebRun> runs = new ConcurrentHashMap<>();
    private final long questionTimeoutSeconds;

    @org.springframework.beans.factory.annotation.Autowired
    public AgentSessionManager(CoopPilotFacade facade) {
        this(facade, 300);
    }

    AgentSessionManager(CoopPilotFacade facade, long questionTimeoutSeconds) {
        this.facade = facade;
        this.questionTimeoutSeconds = questionTimeoutSeconds;
    }

    /** Starts the agent for a posting; returns the run id the page subscribes to. */
    public String start(String postingId) {
        facade.posting(postingId); // fail fast on unknown ids
        WebRun run = new WebRun(UUID.randomUUID().toString(), postingId);
        runs.put(run.id, run);
        executor.submit(() -> {
            try {
                AgentResult r = facade.tailorResume(postingId, new WebQuestionHandler(this, run, questionTimeoutSeconds),
                        new SseProgressPublisher(this, run));
                run.result = r;
            } catch (RuntimeException e) {
                publish(run, new Event("error", Map.of("message", String.valueOf(e.getMessage()))));
            } finally {
                run.emitters.forEach(SseEmitter::complete);
            }
        });
        return run.id;
    }

    public SseEmitter subscribe(String runId) {
        WebRun run = find(runId);
        SseEmitter emitter = new SseEmitter(0L);
        for (Event e : run.events) send(emitter, e);
        if (run.result != null || run.events.stream().anyMatch(e -> e.type().equals("error"))) {
            emitter.complete();
            return emitter;
        }
        run.emitters.add(emitter);
        emitter.onCompletion(() -> run.emitters.remove(emitter));
        emitter.onTimeout(() -> run.emitters.remove(emitter));
        return emitter;
    }

    /** Delivers the student's answer to the question the agent is waiting on. */
    public boolean answer(String runId, String answer) {
        WebRun run = find(runId);
        if (run.pendingQuestion == null) return false;
        run.answers.offer(answer == null ? "" : answer);
        return true;
    }

    void publish(WebRun run, Event e) {
        run.events.add(e);
        for (SseEmitter em : run.emitters) send(em, e);
    }

    Optional<String> waitForAnswer(WebRun run, String question, long timeoutSeconds) {
        run.pendingQuestion = question;
        publish(run, new Event("question", Map.of("question", question)));
        try {
            String a = run.answers.poll(timeoutSeconds, TimeUnit.SECONDS);
            return Optional.ofNullable(a).map(String::trim).filter(s -> !s.isEmpty());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } finally {
            run.pendingQuestion = null;
        }
    }

    public List<Event> events(String runId) {
        return new ArrayList<>(find(runId).events);
    }

    public Optional<AgentResult> result(String runId) {
        return Optional.ofNullable(find(runId).result);
    }

    public String postingOf(String runId) {
        return find(runId).postingId;
    }

    private WebRun find(String runId) {
        WebRun r = runs.get(runId);
        if (r == null) throw new IllegalArgumentException("No agent run " + runId);
        return r;
    }

    private static void send(SseEmitter emitter, Event e) {
        try {
            emitter.send(SseEmitter.event().name(e.type()).data(e.data()));
        } catch (IOException | IllegalStateException ex) {
            emitter.completeWithError(ex);
        }
    }
}
