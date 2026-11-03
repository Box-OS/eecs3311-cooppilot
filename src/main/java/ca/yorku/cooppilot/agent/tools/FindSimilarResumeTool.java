package ca.yorku.cooppilot.agent.tools;

import ca.yorku.cooppilot.agent.AgentSession;
import ca.yorku.cooppilot.domain.Resume;
import ca.yorku.cooppilot.domain.ResumeMatch;
import ca.yorku.cooppilot.service.ResumeLibrary;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.stream.Collectors;

/** Finds the saved resume closest to the posting and shows its blocks (ids, bullets) to the model. */
@Component
public class FindSimilarResumeTool extends AgentTool {

    private final ResumeLibrary library;

    public FindSimilarResumeTool(ResumeLibrary library) {
        this.library = library;
    }

    public String name() { return "find_similar_resume"; }

    public String description() {
        return "Find the saved resume that already covers this posting best (match %), and list its blocks with ids "
                + "and numbered bullets. Call this first. Optionally pass resume_id to open a specific saved resume.";
    }

    public JsonNode inputSchema() {
        return Schemas.object().prop("resume_id", "string", "open this saved resume instead of the closest", false).build();
    }

    public ToolResult execute(JsonNode args, AgentSession s) {
        if (args != null && args.hasNonNull("resume_id")) {
            Resume r = library.byId(args.get("resume_id").asText())
                    .orElseThrow(() -> new IllegalArgumentException("no saved resume with that id"));
            return ToolResult.ok("RESUME " + r.getName() + " (id " + r.getId() + ")\n" + describe(r));
        }
        Optional<ResumeMatch> best = library.findClosest(analysis(s));
        String others = library.all().stream().map(r -> r.getName() + " (id " + r.getId() + ")")
                .collect(Collectors.joining(", "));
        if (best.isEmpty()) {
            if (library.all().isEmpty()) return ToolResult.ok("No saved resumes. Import one first; nothing to build from.");
            Resume any = library.all().get(0);
            return ToolResult.ok("No placeholder-free resume to reuse. Saved resumes: " + others
                    + "\nUse this one as the base:\nRESUME " + any.getName() + " (id " + any.getId() + ")\n" + describe(any));
        }
        ResumeMatch m = best.get();
        return ToolResult.ok("CLOSEST: " + m.resume().getName() + " (id " + m.resume().getId() + "), match "
                + m.matchPercent() + "% (>=75% means reuse as is).\nAll saved: " + others + "\n" + describe(m.resume()));
    }
}
