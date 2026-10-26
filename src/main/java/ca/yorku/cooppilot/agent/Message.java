package ca.yorku.cooppilot.agent;

import ca.yorku.cooppilot.agent.tools.ToolCall;

import java.util.List;

/**
 * One turn of the agent conversation, provider-neutral.
 * ASSISTANT turns may carry tool calls; TOOL turns carry the result for one call ({@code toolCallId}).
 */
public record Message(Role role, String content, List<ToolCall> toolCalls, String toolCallId, boolean error) {

    public enum Role { USER, ASSISTANT, TOOL }

    public Message {
        content = content == null ? "" : content;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public static Message user(String text) {
        return new Message(Role.USER, text, List.of(), null, false);
    }

    public static Message assistant(String text, List<ToolCall> calls) {
        return new Message(Role.ASSISTANT, text, calls, null, false);
    }

    public static Message toolResult(String toolCallId, String content, boolean error) {
        return new Message(Role.TOOL, content, List.of(), toolCallId, error);
    }
}
