package ca.yorku.cooppilot.app;

import ca.yorku.cooppilot.domain.Posting;
import ca.yorku.cooppilot.domain.Resume;
import ca.yorku.cooppilot.domain.VaultEntry;
import ca.yorku.cooppilot.llm.LLMClient;
import ca.yorku.cooppilot.llm.PostingAnalysisTask;
import ca.yorku.cooppilot.persistence.PostingRepository;
import ca.yorku.cooppilot.persistence.ResumeRepository;
import ca.yorku.cooppilot.service.LatexResumeParser;
import ca.yorku.cooppilot.service.ParseResult;
import ca.yorku.cooppilot.service.VaultService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Facade pattern: the single entry point both UIs (web controllers and the CLI) call. It coordinates
 * the parser, vault, repositories and, in later features, the agent, so the UIs never wire subsystems.
 */
@Service
public class CoopPilotFacade {

    private final LatexResumeParser parser;
    private final VaultService vault;
    private final ResumeRepository resumes;
    private final PostingRepository postings;
    private final LLMClient llm;

    public CoopPilotFacade(LatexResumeParser parser, VaultService vault, ResumeRepository resumes,
                           PostingRepository postings, LLMClient llm) {
        this.parser = parser;
        this.vault = vault;
        this.resumes = resumes;
        this.postings = postings;
        this.llm = llm;
    }

    /** F02: stores a pasted posting. Company/role may be blank; analysis fills them in. */
    public Posting importPosting(String text, String company, String role) {
        Posting p = new Posting(null, company, role, text, "", null);
        postings.save(p);
        return p;
    }

    /** F02: runs the LLM analysis (with validation and retries) and saves the result on the posting. */
    public Posting analyzePosting(String postingId) {
        Posting p = posting(postingId);
        PostingAnalysisTask.Result r = new PostingAnalysisTask(llm, p).run();
        p.setAnalysis(r.analysis());
        if (p.getCompany().isBlank()) p.setCompany(r.company());
        if (p.getRole().isBlank()) p.setRole(r.role());
        if (p.getTerm().isBlank()) p.setTerm(r.term());
        postings.save(p);
        return p;
    }

    public Posting posting(String postingId) {
        return postings.findById(postingId)
                .orElseThrow(() -> new IllegalArgumentException("No posting with id " + postingId));
    }

    public List<Posting> postings() {
        return postings.findAll();
    }

    /** Result of an import for the UI: the parse, the saved resume, and how many vault entries were new. */
    public record ImportOutcome(ParseResult parse, Resume resume, int newVaultEntries) {}

    /**
     * F01: parses a LaTeX resume, saves it to the library as an ORIGINAL resume, and creates vault
     * entries for its experiences, projects and skills.
     */
    public ImportOutcome importResume(String latex, String name) {
        ParseResult parsed = parser.parse(latex);
        if (parsed.blocks().isEmpty()) {
            return new ImportOutcome(parsed, null, 0);
        }
        String resumeName = (name == null || name.isBlank()) ? "CV_General" : name.trim();
        Resume resume = resumes.findByName(resumeName)
                .map(old -> new Resume(old.getId(), resumeName, old.getFamily(), Resume.Kind.ORIGINAL, parsed.blocks(), latex))
                .orElseGet(() -> new Resume(null, resumeName, "", Resume.Kind.ORIGINAL, parsed.blocks(), latex));
        resumes.save(resume);
        int added = vault.importFromResume(parsed);
        return new ImportOutcome(parsed, resume, added);
    }

    public VaultEntry addVaultEntry(VaultEntry e) {
        return vault.addEntry(e);
    }

    public List<VaultEntry> vaultEntries(String query) {
        return vault.search(query, null);
    }

    public List<Resume> resumes() {
        return resumes.findAll();
    }
}
