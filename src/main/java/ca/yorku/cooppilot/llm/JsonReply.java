package ca.yorku.cooppilot.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/** Pulls the JSON object out of a model reply (which may wrap it in prose or ``` fences). */
public final class JsonReply {

    private static final ObjectMapper JSON = new ObjectMapper();

    private JsonReply() {}

    public static JsonNode object(String reply) {
        int start = reply.indexOf('{');
        int end = reply.lastIndexOf('}');
        if (start < 0 || end <= start) throw new IllegalArgumentException("no JSON object in the reply");
        try {
            JsonNode node = JSON.readTree(reply.substring(start, end + 1));
            if (!node.isObject()) throw new IllegalArgumentException("reply JSON is not an object");
            return node;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("invalid JSON (" + e.getOriginalMessage() + ")");
        }
    }

    public static List<String> strings(JsonNode array) {
        List<String> out = new ArrayList<>();
        if (array != null && array.isArray()) {
            for (JsonNode n : array) {
                String s = n.asText("").trim();
                if (!s.isEmpty()) out.add(s);
            }
        }
        return out;
    }
}
