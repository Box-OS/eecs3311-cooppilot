package ca.yorku.cooppilot.agent.tools;

/** What a tool returns to the agent. Errors go back to the LLM so it can correct itself. */
public record ToolResult(String callId, String toolName, boolean ok, String content, String error) {

    public static ToolResult ok(String content) {
        return new ToolResult(null, null, true, content, null);
    }

    public static ToolResult error(String error) {
        return new ToolResult(null, null, false, null, error);
    }

    public ToolResult forCall(ToolCall call) {
        return new ToolResult(call.id(), call.name(), ok, content, error);
    }

    /** Text sent back to the model. */
    public String forModel() {
        return ok ? content : "ERROR: " + error;
    }
}
