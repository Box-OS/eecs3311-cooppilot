package ca.yorku.cooppilot.agent.tools;

import ca.yorku.cooppilot.agent.AgentSession;
import ca.yorku.cooppilot.domain.EntryKind;
import ca.yorku.cooppilot.domain.EntryUse;
import ca.yorku.cooppilot.domain.VaultEntry;
import ca.yorku.cooppilot.service.VaultService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.List;

/** Retrieval over the experience vault (the agent's long-term memory). Planned entries are never returned. */
@Component
public class SearchVaultTool extends AgentTool {

    private final VaultService vault;

    public SearchVaultTool(VaultService vault) {
        this.vault = vault;
    }

    public String name() { return "search_vault"; }

    public String description() {
        return "Search the student's experience vault (projects, jobs, courses, skills) for facts to use. "
                + "Entries marked CONTEXT ONLY must never be quoted.";
    }

    public JsonNode inputSchema() {
        return Schemas.object().prop("query", "string", "word or phrase, e.g. 'React' or 'tutor'", true).build();
    }

    public ToolResult execute(JsonNode args, AgentSession s) {
        List<VaultEntry> hits = vault.search(args.get("query").asText(), null).stream()
                .filter(e -> e.getKind() != EntryKind.PLANNED).limit(8).toList();
        if (hits.isEmpty()) return ToolResult.ok("No vault entries match. Do not invent this; ask_user if it matters.");
        StringBuilder sb = new StringBuilder();
        for (VaultEntry e : hits) {
            sb.append("- ").append(e.getKind()).append(": ").append(e.getTitle());
            if (e.getUse() == EntryUse.CONTEXT_ONLY) sb.append(" [CONTEXT ONLY]");
            else if (e.getUse() == EntryUse.LETTERS_ONLY) sb.append(" [letters only]");
            if (!e.getDate().isBlank()) sb.append(" (").append(e.getDate()).append(")");
            if (!e.getTags().isEmpty()) sb.append(" tags: ").append(String.join(", ", e.getTags()));
            if (!e.getBody().isBlank()) sb.append("\n  ").append(e.getBody().replace("\n", "\n  "));
            sb.append('\n');
        }
        return ToolResult.ok(sb.toString());
    }
}
