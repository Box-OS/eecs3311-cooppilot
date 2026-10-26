package ca.yorku.cooppilot.llm;

import ca.yorku.cooppilot.agent.Message;
import ca.yorku.cooppilot.agent.tools.ToolCall;
import ca.yorku.cooppilot.agent.tools.ToolSchema;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Adapter pattern — Adapter. Translates the provider-neutral {@link LlmRequest}/{@link LlmResponse}
 * to and from the Anthropic Messages API (with tool use), over Java's built-in HTTP client.
 */
public class ClaudeAdapter implements LLMClient {

    static final String ENDPOINT = "https://api.anthropic.com/v1/messages";
    static final String API_VERSION = "2023-06-01";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient http;
    private final String apiKey;
    private final String model;

    public ClaudeAdapter(String apiKey, String model) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build(), apiKey, model);
    }

    ClaudeAdapter(HttpClient http, String apiKey, String model) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("No API key: set cooppilot.llm.api-key in application-local.properties");
        }
        this.http = http;
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        String body = toProvider(request).toString();
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(ENDPOINT))
                .timeout(Duration.ofSeconds(120))
                .header("content-type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", API_VERSION)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new LlmException("Claude API error " + response.statusCode() + ": " + errorMessage(response.body()),
                        response.statusCode());
            }
            return fromProvider(JSON.readTree(response.body()));
        } catch (IOException e) {
            throw new LlmException("Could not reach the Claude API", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("Interrupted while waiting for Claude", e);
        }
    }

    @Override
    public String modelName() {
        return model;
    }

    /** Neutral request -> Messages API JSON. TOOL messages become user turns holding a tool_result block. */
    ObjectNode toProvider(LlmRequest req) {
        ObjectNode root = JSON.createObjectNode();
        root.put("model", model);
        root.put("max_tokens", req.maxTokens());
        if (req.system() != null && !req.system().isBlank()) root.put("system", req.system());

        ArrayNode messages = root.putArray("messages");
        for (Message m : req.messages()) {
            ObjectNode msg = JSON.createObjectNode();
            ArrayNode content = JSON.createArrayNode();
            switch (m.role()) {
                case USER -> {
                    msg.put("role", "user");
                    content.addObject().put("type", "text").put("text", m.content());
                }
                case ASSISTANT -> {
                    msg.put("role", "assistant");
                    if (!m.content().isBlank()) content.addObject().put("type", "text").put("text", m.content());
                    for (ToolCall c : m.toolCalls()) {
                        ObjectNode use = content.addObject();
                        use.put("type", "tool_use").put("id", c.id()).put("name", c.name());
                        use.set("input", c.args() == null ? JSON.createObjectNode() : c.args());
                    }
                }
                case TOOL -> {
                    msg.put("role", "user");
                    ObjectNode result = content.addObject();
                    result.put("type", "tool_result").put("tool_use_id", m.toolCallId()).put("content", m.content());
                    if (m.error()) result.put("is_error", true);
                }
            }
            msg.set("content", content);
            appendMerging(messages, msg);
        }

        if (!req.tools().isEmpty()) {
            ArrayNode tools = root.putArray("tools");
            for (ToolSchema t : req.tools()) {
                ObjectNode tool = tools.addObject();
                tool.put("name", t.name()).put("description", t.description());
                tool.set("input_schema", t.inputSchema());
            }
        }
        return root;
    }

    /** The API requires alternating roles, so consecutive tool results are merged into one user turn. */
    private static void appendMerging(ArrayNode messages, ObjectNode msg) {
        if (!messages.isEmpty()) {
            JsonNode last = messages.get(messages.size() - 1);
            if (last.get("role").asText().equals(msg.get("role").asText())) {
                ((ArrayNode) last.get("content")).addAll((ArrayNode) msg.get("content"));
                return;
            }
        }
        messages.add(msg);
    }

    /** Messages API JSON -> neutral response. */
    LlmResponse fromProvider(JsonNode body) {
        StringBuilder text = new StringBuilder();
        List<ToolCall> calls = new ArrayList<>();
        for (JsonNode block : body.path("content")) {
            switch (block.path("type").asText()) {
                case "text" -> text.append(block.path("text").asText());
                case "tool_use" -> calls.add(new ToolCall(block.path("id").asText(),
                        block.path("name").asText(), block.path("input")));
                default -> { /* ignore other block types */ }
            }
        }
        JsonNode usage = body.path("usage");
        return new LlmResponse(text.toString(), calls, body.path("stop_reason").asText(""),
                usage.path("input_tokens").asInt(0), usage.path("output_tokens").asInt(0));
    }

    private static String errorMessage(String body) {
        try {
            return JSON.readTree(body).path("error").path("message").asText(body);
        } catch (IOException e) {
            return body;
        }
    }
}
