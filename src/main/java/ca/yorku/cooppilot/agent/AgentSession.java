package ca.yorku.cooppilot.agent;

import ca.yorku.cooppilot.agent.tools.ToolCall;
import ca.yorku.cooppilot.agent.tools.ToolResult;
import ca.yorku.cooppilot.domain.Resume;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Observer pattern — Subject. One agent run: the conversation, the current draft, the tool log and
 * scratch state the tools share. Listeners are notified of every tool call, result and the finish.
 */
public class AgentSession {

    private final AgentGoal goal;
    private final QuestionHandler questions;
    private final List<Message> messages = new ArrayList<>();
    private final List<ToolCall> toolLog = new ArrayList<>();
    private final List<AgentEventListener> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, Object> scratch = new HashMap<>();
    private Resume draft;
    private Resume saved;
    private int stepCount;

    public AgentSession(AgentGoal goal, QuestionHandler questions) {
        this.goal = goal;
        this.questions = questions == null ? QuestionHandler.NONE : questions;
    }

    public void addListener(AgentEventListener l) {
        if (l != null) listeners.add(l);
    }

    public void addMessage(Message m) {
        messages.add(m);
    }

    public void updateDraft(Resume r) {
        this.draft = r;
    }

    public void markSaved(Resume r) {
        this.saved = r;
        this.draft = r;
    }

    void nextStep() {
        stepCount++;
    }

    void notifyThinking(String text) {
        if (text != null && !text.isBlank()) listeners.forEach(l -> l.onThinking(text));
    }

    void notifyToolCalled(ToolCall c) {
        toolLog.add(c);
        listeners.forEach(l -> l.onToolCalled(c));
    }

    void notifyToolResult(ToolResult r) {
        listeners.forEach(l -> l.onToolResult(r));
    }

    void notifyFinished(AgentResult r) {
        listeners.forEach(l -> l.onFinished(r));
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String key) {
        return Optional.ofNullable((T) scratch.get(key));
    }

    public void put(String key, Object value) {
        scratch.put(key, value);
    }

    public AgentGoal goal() { return goal; }
    public QuestionHandler questions() { return questions; }
    public List<Message> messages() { return List.copyOf(messages); }
    public List<ToolCall> toolLog() { return List.copyOf(toolLog); }
    public Optional<Resume> draft() { return Optional.ofNullable(draft); }
    public Optional<Resume> saved() { return Optional.ofNullable(saved); }
    public int stepCount() { return stepCount; }
}
