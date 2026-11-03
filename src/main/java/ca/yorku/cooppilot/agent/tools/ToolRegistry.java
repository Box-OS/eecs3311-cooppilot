package ca.yorku.cooppilot.agent.tools;

import ca.yorku.cooppilot.agent.AgentSession;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Holds the tools, gives their schemas to the LLM, and validates arguments before anything runs. */
public class ToolRegistry {

    private final Map<String, Tool> tools = new LinkedHashMap<>();

    public ToolRegistry(List<Tool> initial) {
        initial.forEach(this::register);
    }

    public void register(Tool t) {
        if (tools.putIfAbsent(t.name(), t) != null) throw new IllegalArgumentException("duplicate tool " + t.name());
    }

    public List<ToolSchema> schemas() {
        return tools.values().stream().map(Tool::schema).toList();
    }

    public List<String> names() {
        return List.copyOf(tools.keySet());
    }

    /** Runs the call if the tool exists and the arguments are valid; never throws to the agent loop. */
    public ToolResult execute(ToolCall call, AgentSession session) {
        Tool tool = tools.get(call.name());
        if (tool == null) {
            return ToolResult.error("unknown tool '" + call.name() + "'. Available: " + names()).forCall(call);
        }
        List<String> problems = validateArgs(tool, call.args());
        if (!problems.isEmpty()) {
            return ToolResult.error("invalid arguments: " + String.join("; ", problems)).forCall(call);
        }
        try {
            return tool.execute(call.args(), session).forCall(call);
        } catch (RuntimeException e) {
            return ToolResult.error(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()).forCall(call);
        }
    }

    /** Checks required properties and primitive types against the tool's schema. */
    static List<String> validateArgs(Tool tool, JsonNode args) {
        List<String> problems = new ArrayList<>();
        JsonNode schema = tool.inputSchema();
        if (args == null || !args.isObject()) {
            if (schema.path("required").size() > 0) problems.add("arguments must be a JSON object");
            return problems;
        }
        for (JsonNode req : schema.path("required")) {
            JsonNode v = args.get(req.asText());
            if (v == null || v.isNull() || (v.isTextual() && v.asText().isBlank())) {
                problems.add("missing '" + req.asText() + "'");
            }
        }
        JsonNode props = schema.path("properties");
        for (Iterator<String> it = args.fieldNames(); it.hasNext(); ) {
            String field = it.next();
            JsonNode spec = props.get(field);
            if (spec == null) {
                problems.add("unexpected argument '" + field + "'");
                continue;
            }
            String type = spec.path("type").asText("");
            JsonNode v = args.get(field);
            boolean okType = switch (type) {
                case "string" -> v.isTextual();
                case "integer" -> v.isIntegralNumber();
                case "number" -> v.isNumber();
                case "boolean" -> v.isBoolean();
                case "array" -> v.isArray();
                case "object" -> v.isObject();
                default -> true;
            };
            if (!okType) problems.add("'" + field + "' must be " + type);
        }
        return problems;
    }
}
