package ca.yorku.cooppilot.llm;

import ca.yorku.cooppilot.agent.Message;
import ca.yorku.cooppilot.agent.tools.ToolSchema;

import java.util.List;

/** Provider-neutral request: system prompt, conversation, available tools and an output limit. */
public record LlmRequest(String system, List<Message> messages, List<ToolSchema> tools, int maxTokens) {

    public LlmRequest {
        if (messages == null || messages.isEmpty()) throw new IllegalArgumentException("at least one message is required");
        messages = List.copyOf(messages);
        tools = tools == null ? List.of() : List.copyOf(tools);
        if (maxTokens <= 0) throw new IllegalArgumentException("maxTokens must be positive");
    }

    /** Single-shot prompt with no tools (used by the LlmTask template methods). */
    public static LlmRequest simple(String system, String prompt, int maxTokens) {
        return new LlmRequest(system, List.of(Message.user(prompt)), List.of(), maxTokens);
    }
}
