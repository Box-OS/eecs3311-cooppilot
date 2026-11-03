package ca.yorku.cooppilot.agent.tools;

import ca.yorku.cooppilot.agent.AgentSession;
import ca.yorku.cooppilot.domain.Posting;
import ca.yorku.cooppilot.domain.Resume;
import ca.yorku.cooppilot.service.ExportService;
import ca.yorku.cooppilot.service.PageEstimator;
import ca.yorku.cooppilot.service.ResumeLibrary;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

/** Saves the current draft to the library as CV_Company_Role (replacing an older one with that name). */
@Component
public class SaveResumeTool extends AgentTool {

    private final ResumeLibrary library;
    private final ExportService export;
    private final PageEstimator pages;

    public SaveResumeTool(ResumeLibrary library, ExportService export, PageEstimator pages) {
        this.library = library;
        this.export = export;
        this.pages = pages;
    }

    public String name() { return "save_resume"; }

    public String description() {
        return "Save the current draft as the final tailored resume (named CV_Company_Role automatically). Call last.";
    }

    public JsonNode inputSchema() {
        return Schemas.object().prop("family", "string", "role family, e.g. backend, full-stack, data", false).build();
    }

    public ToolResult execute(JsonNode args, AgentSession s) {
        Resume draft = s.draft().orElseThrow(() -> new IllegalStateException("nothing to save: call compose_resume first"));
        if (!pages.fitsOnePage(draft)) {
            return ToolResult.error("the draft is over one page (" + Math.round(pages.estimate(draft) * 100)
                    + "%): call condense first");
        }
        Posting p = s.goal().posting();
        String name = export.fileName("CV", p.getCompany(), p.getRole());
        String id = library.byName(name).map(Resume::getId).orElse(null);
        String family = args != null && args.hasNonNull("family") ? args.get("family").asText() : draft.getFamily();
        Resume saved = new Resume(id, name, family, Resume.Kind.TAILORED, draft.getBlocks(), draft.getLatex());
        library.save(saved);
        s.markSaved(saved);
        return ToolResult.ok("Saved as " + name + (saved.hasPlaceholders() ? " (contains [placeholders] for the student to fill in)" : "") + ".");
    }
}
