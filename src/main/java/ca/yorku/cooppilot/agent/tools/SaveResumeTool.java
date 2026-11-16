package ca.yorku.cooppilot.agent.tools;

import ca.yorku.cooppilot.agent.AgentSession;
import ca.yorku.cooppilot.domain.Keyword;
import ca.yorku.cooppilot.domain.Posting;
import ca.yorku.cooppilot.domain.Resume;
import ca.yorku.cooppilot.llm.PostingAnalysisTask;
import ca.yorku.cooppilot.service.ExportService;
import ca.yorku.cooppilot.service.KeywordScanner;
import ca.yorku.cooppilot.service.PageEstimator;
import ca.yorku.cooppilot.service.ResumeLibrary;
import ca.yorku.cooppilot.service.VaultService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Saves the current draft to the library as CV_Company_Role (replacing an older one with that name). Refuses while the
 * posting has must-haves with no evidence that the student hasn't been asked about (F06, enforced by code).
 */
@Component
public class SaveResumeTool extends AgentTool {

    /** At most this many must-haves are asked about per run, so the student isn't flooded with questions. */
    static final int MAX_QUESTIONS = 2;

    private final ResumeLibrary library;
    private final ExportService export;
    private final PageEstimator pages;
    private final KeywordScanner scanner;
    private final VaultService vault;

    public SaveResumeTool(ResumeLibrary library, ExportService export, PageEstimator pages, KeywordScanner scanner, VaultService vault) {
        this.library = library;
        this.export = export;
        this.pages = pages;
        this.scanner = scanner;
        this.vault = vault;
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
        List<Keyword> ask = unasked(draft, s);
        if (!ask.isEmpty()) {
            return ToolResult.error("not saved yet: the posting requires " + ask.stream().map(Keyword::term).collect(Collectors.joining(", "))
                    + " and nothing in the vault shows it. Call ask_user once for each (e.g. \"Have you used " + ask.get(0).term()
                    + "? If so, in which project or job?\", topic: the skill), add it only if the answer says yes, then save again.");
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

    /** Must-haves (the first MAX_QUESTIONS) that neither the draft nor the vault backs, and that no question has covered yet. */
    List<Keyword> unasked(Resume draft, AgentSession s) {
        if (s.goal().posting().getAnalysis().isEmpty()) return List.of();
        List<String> asked = s.<List<String>>get(AskUserTool.ASKED).orElse(List.of());
        if (asked.size() >= MAX_QUESTIONS) return List.of(); // it has asked enough this run
        return scanner.scan(draft, analysis(s)).missing().stream()
                .filter(Keyword::mustHave)
                .filter(k -> !PostingAnalysisTask.isSoftOrGeneric(k.term()))
                .filter(k -> !vault.answeredAbout(k::appearsIn))
                .limit(MAX_QUESTIONS)
                .filter(k -> asked.stream().noneMatch(k::appearsIn))
                .toList();
    }
}
