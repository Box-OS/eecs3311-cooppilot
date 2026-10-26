package ca.yorku.cooppilot.llm;

import ca.yorku.cooppilot.agent.Message;
import ca.yorku.cooppilot.agent.tools.ToolCall;
import ca.yorku.cooppilot.agent.tools.ToolSchema;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Checks the request/response translation without calling the real API. */
class ClaudeAdapterTest {

    private final ObjectMapper json = new ObjectMapper();
    private final ClaudeAdapter adapter = new ClaudeAdapter("test-key", "claude-test");

    @Test
    void translatesToolLoopConversation() throws Exception {
        JsonNode args = json.readTree("{\"query\":\"java\"}");
        ToolCall call = new ToolCall("toolu_1", "search_vault", args);
        ObjectNode schema = json.createObjectNode().put("type", "object");

        LlmRequest req = new LlmRequest("You are CoopPilot.",
                List.of(Message.user("Tailor my resume"),
                        Message.assistant("Searching the vault.", List.of(call)),
                        Message.toolResult("toolu_1", "2 entries found", false)),
                List.of(new ToolSchema("search_vault", "Search the experience vault", schema)), 512);

        ObjectNode body = adapter.toProvider(req);
        assertEquals("claude-test", body.get("model").asText());
        assertEquals("You are CoopPilot.", body.get("system").asText());

        JsonNode msgs = body.get("messages");
        assertEquals(3, msgs.size());
        assertEquals("assistant", msgs.get(1).get("role").asText());
        assertEquals("tool_use", msgs.get(1).get("content").get(1).get("type").asText());
        assertEquals("java", msgs.get(1).get("content").get(1).get("input").get("query").asText());
        assertEquals("user", msgs.get(2).get("role").asText());
        assertEquals("toolu_1", msgs.get(2).get("content").get(0).get("tool_use_id").asText());
        assertEquals("search_vault", body.get("tools").get(0).get("name").asText());
    }

    @Test
    void mergesConsecutiveToolResultsIntoOneUserTurn() {
        LlmRequest req = new LlmRequest(null,
                List.of(Message.user("go"),
                        Message.assistant("", List.of(new ToolCall("a", "t1", null), new ToolCall("b", "t2", null))),
                        Message.toolResult("a", "ok", false),
                        Message.toolResult("b", "bad args", true)),
                List.of(), 100);
        JsonNode msgs = adapter.toProvider(req).get("messages");
        assertEquals(3, msgs.size(), "roles must alternate");
        assertEquals(2, msgs.get(2).get("content").size());
        assertTrue(msgs.get(2).get("content").get(1).get("is_error").asBoolean());
    }

    @Test
    void parsesTextAndToolUseFromResponse() throws Exception {
        JsonNode body = json.readTree("""
                {"content":[{"type":"text","text":"Let me check."},
                            {"type":"tool_use","id":"toolu_9","name":"keyword_scan","input":{"resumeId":"r1"}}],
                 "stop_reason":"tool_use","usage":{"input_tokens":120,"output_tokens":30}}""");
        LlmResponse r = adapter.fromProvider(body);
        assertEquals("Let me check.", r.text());
        assertTrue(r.hasToolCalls());
        assertEquals("keyword_scan", r.toolCalls().get(0).name());
        assertEquals("r1", r.toolCalls().get(0).args().get("resumeId").asText());
        assertEquals("tool_use", r.stopReason());
        assertEquals(120, r.inputTokens());
    }

    @Test
    void refusesToStartWithoutKey() {
        assertThrows(IllegalStateException.class, () -> new ClaudeAdapter("", "m"));
    }

    @Test
    void mockReturnsQueuedResponsesThenFallback() {
        MockLlmClient mock = new MockLlmClient().enqueueText("first");
        LlmRequest req = LlmRequest.simple(null, "hi", 10);
        assertEquals("first", mock.complete(req).text());
        assertFalse(mock.complete(req).text().isBlank());
        assertEquals(2, mock.received().size());
    }
}
