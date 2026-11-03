package ca.yorku.cooppilot.agent;

import ca.yorku.cooppilot.agent.tools.ToolCall;
import ca.yorku.cooppilot.agent.tools.ToolResult;

/** Observer pattern — Observer. Gets live agent progress (the web SSE publisher, the CLI printer, tests). */
public interface AgentEventListener {

    default void onThinking(String text) {}

    void onToolCalled(ToolCall call);

    void onToolResult(ToolResult result);

    void onFinished(AgentResult result);
}
