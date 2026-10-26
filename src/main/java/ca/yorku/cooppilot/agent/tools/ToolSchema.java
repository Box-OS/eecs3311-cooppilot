package ca.yorku.cooppilot.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;

/** What the LLM sees of a tool: its name, when to use it, and a JSON Schema for its arguments. */
public record ToolSchema(String name, String description, JsonNode inputSchema) {
}
