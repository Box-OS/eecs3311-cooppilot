package ca.yorku.cooppilot.agent.tools;

import ca.yorku.cooppilot.agent.AgentSession;
import ca.yorku.cooppilot.domain.EntryKind;
import ca.yorku.cooppilot.domain.VaultEntry;
import ca.yorku.cooppilot.service.VaultService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

/**
 * F06: asks the student for a missing fact instead of guessing, then files the answer in the vault
 * (memory). A question answered before is answered from the vault without asking again.
 */
@Component
public class AskUserTool extends AgentTool {

    private final VaultService vault;

    public AskUserTool(VaultService vault) {
        this.vault = vault;
    }

    public String name() { return "ask_user"; }

    public String description() {
        return "Ask the student one short question when a non-numeric fact is missing (a framework, a date, a role). "
                + "topic = title of the vault entry it is about. The answer is saved to the vault.";
    }

    public JsonNode inputSchema() {
        return Schemas.object()
                .prop("question", "string", "one short question", true)
                .prop("topic", "string", "the project/job it is about, e.g. 'StudyBuddy'", false)
                .prop("kind", "string", "if it is a new experience: PROJECT, WORK, COURSE, ACTIVITY or SKILL", false)
                .build();
    }

    public ToolResult execute(JsonNode args, AgentSession s) {
        String question = args.get("question").asText().trim();
        Optional<String> known = vault.knownAnswer(question);
        if (known.isPresent()) {
            return ToolResult.ok("Already answered before (from the vault): " + known.get());
        }
        Optional<String> answer = s.questions().ask(question).map(String::trim).filter(a -> !a.isEmpty());
        if (answer.isEmpty()) {
            return ToolResult.ok("No answer. Do not guess: leave this detail out or use a numeric placeholder.");
        }
        String topic = args.hasNonNull("topic") ? args.get("topic").asText() : null;
        VaultEntry filed = vault.fileAnswer(question, answer.get(), topic, kind(args));
        return ToolResult.ok("Student answered: " + answer.get() + "\n(saved to vault entry \"" + filed.getTitle() + "\")");
    }

    private static EntryKind kind(JsonNode args) {
        if (!args.hasNonNull("kind")) return null;
        try {
            EntryKind k = EntryKind.valueOf(args.get("kind").asText().trim().toUpperCase(Locale.ROOT));
            return k == EntryKind.PLANNED ? null : k;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
