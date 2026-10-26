package ca.yorku.cooppilot.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;

/** A tool the LLM asked to run: provider call id, tool name and JSON arguments. */
public record ToolCall(String id, String name, JsonNode args) {
}
