package ca.yorku.cooppilot.agent.tools;

import ca.yorku.cooppilot.agent.AgentSession;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * A deterministic capability the LLM may choose. The LLM decides when to call it; the tool
 * enforces the rules (only vault facts, ordering, one page...).
 */
public interface Tool {

    String name();

    String description();

    /** JSON Schema of the arguments (type object, properties, required). */
    JsonNode inputSchema();

    default ToolSchema schema() {
        return new ToolSchema(name(), description(), inputSchema());
    }

    ToolResult execute(JsonNode args, AgentSession session);
}
