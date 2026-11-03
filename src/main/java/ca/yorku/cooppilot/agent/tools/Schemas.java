package ca.yorku.cooppilot.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Small builder for tool argument schemas. */
public final class Schemas {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ObjectNode root = JSON.createObjectNode().put("type", "object");
    private final ObjectNode props = root.putObject("properties");

    public static Schemas object() {
        return new Schemas();
    }

    public Schemas prop(String name, String type, String description, boolean required) {
        props.putObject(name).put("type", type).put("description", description);
        if (required) {
            if (!root.has("required")) root.putArray("required");
            root.withArray("required").add(name);
        }
        return this;
    }

    public Schemas stringArray(String name, String description, boolean required) {
        ObjectNode p = props.putObject(name).put("type", "array").put("description", description);
        p.putObject("items").put("type", "string");
        if (required) {
            if (!root.has("required")) root.putArray("required");
            root.withArray("required").add(name);
        }
        return this;
    }

    public JsonNode build() {
        return root;
    }
}
