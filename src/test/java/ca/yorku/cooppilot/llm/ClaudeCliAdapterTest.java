package ca.yorku.cooppilot.llm;

import ca.yorku.cooppilot.agent.Message;
import ca.yorku.cooppilot.agent.tools.ToolCall;
import ca.yorku.cooppilot.agent.tools.ToolSchema;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Checks the CLI adapter's prompt and reply translation with a fake CLI (no process is started). */
class ClaudeCliAdapterTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void toolLoopGoesThroughTheTranscriptAndComesBackAsToolCalls() throws Exception {
        List<List<String>> commands = new ArrayList<>();
        List<String> inputs = new ArrayList<>();
        ClaudeCliAdapter adapter = new ClaudeCliAdapter("claude", "claude-test", "", "", (cmd, env, stdin) -> {
            commands.add(cmd);
            inputs.add(stdin);
            return """
                    {"type":"result","is_error":false,"result":"","usage":{"input_tokens":10,"output_tokens":5},
                     "structured_output":{"text":"Searching.","tool_calls":[{"name":"search_vault","args":{"query":"java"}}]}}""";
        });
        LlmRequest req = new LlmRequest("You are CoopPilot.",
                List.of(Message.user("Tailor my resume"),
                        Message.assistant("", List.of(new ToolCall("call_1", "keyword_scan", json.createObjectNode()))),
                        Message.toolResult("call_1", "coverage 40%", false)),
                List.of(new ToolSchema("search_vault", "Search the vault", json.createObjectNode().put("type", "object"))), 512);

        LlmResponse r = adapter.complete(req);

        assertTrue(commands.get(0).contains("--json-schema"));
        assertTrue(inputs.get(0).contains("[tool result #call_1]"));
        assertTrue(inputs.get(0).contains("coverage 40%"));
        assertEquals("Searching.", r.text());
        assertEquals(1, r.toolCalls().size());
        assertEquals("search_vault", r.toolCalls().get(0).name());
        assertEquals("java", r.toolCalls().get(0).args().get("query").asText());
        assertEquals("tool_use", r.stopReason());
        assertEquals(15, r.inputTokens() + r.outputTokens());
    }

    @Test
    void plainPromptReturnsTheResultText() {
        ClaudeCliAdapter adapter = new ClaudeCliAdapter("claude", "m", "", "", (cmd, env, stdin) -> {
            assertFalse(cmd.contains("--json-schema"));
            assertEquals("Analyze this posting", stdin);
            return "{\"is_error\":false,\"result\":\"{\\\"company\\\":\\\"Acme\\\"}\",\"usage\":{}}";
        });
        LlmResponse r = adapter.complete(LlmRequest.simple("sys", "Analyze this posting", 100));
        assertEquals("{\"company\":\"Acme\"}", r.text());
        assertFalse(r.hasToolCalls());
    }

    @Test
    void cliErrorBecomesLlmException() {
        ClaudeCliAdapter adapter = new ClaudeCliAdapter("claude", "m", "", "",
                (cmd, env, stdin) -> "{\"is_error\":true,\"result\":\"Not logged in · Please run /login\"}");
        LlmException e = assertThrows(LlmException.class, () -> adapter.complete(LlmRequest.simple("s", "p", 10)));
        assertTrue(e.getMessage().contains("Not logged in"));
    }
}
