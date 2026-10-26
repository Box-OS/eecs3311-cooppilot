package ca.yorku.cooppilot.llm;

import ca.yorku.cooppilot.agent.tools.ToolCall;

import java.util.List;

/** Provider-neutral response: any text, any tool calls, why the model stopped, and token usage. */
public record LlmResponse(String text, List<ToolCall> toolCalls, String stopReason, int inputTokens, int outputTokens) {

    public LlmResponse {
        text = text == null ? "" : text;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }
}
