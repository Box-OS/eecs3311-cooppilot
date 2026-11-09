package ca.yorku.cooppilot.ui.web;

import ca.yorku.cooppilot.agent.AgentEventListener;
import ca.yorku.cooppilot.agent.AgentResult;
import ca.yorku.cooppilot.agent.tools.ToolCall;
import ca.yorku.cooppilot.agent.tools.ToolResult;

import java.util.LinkedHashMap;
import java.util.Map;

/** Observer pattern — concrete observer: turns agent events into Server-Sent Events for the browser. */
public class SseProgressPublisher implements AgentEventListener {

    private final AgentSessionManager manager;
    private final AgentSessionManager.WebRun run;

    SseProgressPublisher(AgentSessionManager manager, AgentSessionManager.WebRun run) {
        this.manager = manager;
        this.run = run;
    }

    @Override
    public void onThinking(String text) {
        manager.publish(run, new AgentSessionManager.Event("thinking", Map.of("text", text)));
    }

    @Override
    public void onToolCalled(ToolCall call) {
        manager.publish(run, new AgentSessionManager.Event("tool",
                Map.of("name", call.name(), "args", String.valueOf(call.args()))));
    }

    @Override
    public void onToolResult(ToolResult result) {
        String text = result.forModel();
        manager.publish(run, new AgentSessionManager.Event("result", Map.of("name", String.valueOf(result.toolName()),
                "ok", result.ok(), "text", text.length() > 1500 ? text.substring(0, 1500) + "…" : text)));
    }

    @Override
    public void onFinished(AgentResult r) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("status", r.status().name());
        d.put("summary", r.summary());
        d.put("steps", r.steps());
        d.put("resumeId", r.resumeOpt().map(x -> x.getId()).orElse(""));
        d.put("resumeName", r.resumeOpt().map(x -> x.getName()).orElse(""));
        manager.publish(run, new AgentSessionManager.Event("done", d));
    }
}
