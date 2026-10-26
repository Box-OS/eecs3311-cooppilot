package ca.yorku.cooppilot.llm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Offline LLM for tests and running without a key. Returns queued responses in order; when the queue
 * is empty it answers with a fixed text so the app never crashes. Records every request it receives.
 */
public class MockLlmClient implements LLMClient {

    private final Deque<LlmResponse> queued = new ArrayDeque<>();
    private final List<LlmRequest> received = new ArrayList<>();

    public MockLlmClient enqueue(LlmResponse response) {
        queued.add(response);
        return this;
    }

    public MockLlmClient enqueueText(String text) {
        return enqueue(new LlmResponse(text, List.of(), "end_turn", 0, 0));
    }

    @Override
    public synchronized LlmResponse complete(LlmRequest request) {
        received.add(request);
        LlmResponse next = queued.poll();
        return next != null ? next
                : new LlmResponse("(mock LLM: no key configured, so no real answer)", List.of(), "end_turn", 0, 0);
    }

    @Override
    public String modelName() {
        return "mock";
    }

    public List<LlmRequest> received() {
        return List.copyOf(received);
    }
}
