package ca.yorku.cooppilot.app;

import ca.yorku.cooppilot.agent.AgentController;
import ca.yorku.cooppilot.agent.AgentEventListener;
import ca.yorku.cooppilot.agent.AgentGoal;
import ca.yorku.cooppilot.agent.AgentResult;
import ca.yorku.cooppilot.agent.QuestionHandler;
import ca.yorku.cooppilot.domain.FitReport;
import ca.yorku.cooppilot.domain.KeywordReport;
import ca.yorku.cooppilot.domain.Posting;
import ca.yorku.cooppilot.domain.PostingAnalysis;
import ca.yorku.cooppilot.domain.Resume;
import ca.yorku.cooppilot.domain.VaultEntry;
import ca.yorku.cooppilot.llm.LLMClient;
import ca.yorku.cooppilot.llm.PostingAnalysisTask;
import ca.yorku.cooppilot.persistence.PostingRepository;
import ca.yorku.cooppilot.persistence.ResumeRepository;
import ca.yorku.cooppilot.service.FitScorer;
import ca.yorku.cooppilot.service.KeywordScanner;
import ca.yorku.cooppilot.service.LatexResumeParser;
import ca.yorku.cooppilot.service.ResumeLibrary;
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
    private final FitScorer fitScorer;
    private final KeywordScanner keywordScanner;
    private final ResumeLibrary library;
    private final AgentController agent;

    public CoopPilotFacade(LatexResumeParser parser, VaultService vault, ResumeRepository resumes,
                           PostingRepository postings, LLMClient llm, FitScorer fitScorer,
                           KeywordScanner keywordScanner, ResumeLibrary library, AgentController agent) {
        this.agent = agent;
        this.parser = parser;
        this.vault = vault;
        this.resumes = resumes;
        this.postings = postings;
        this.llm = llm;
        this.fitScorer = fitScorer;
        this.keywordScanner = keywordScanner;
        this.library = library;
    }

    /** F05: runs the tailoring agent for an analyzed posting; progress goes to the listener. */
    public AgentResult tailorResume(String postingId, QuestionHandler questions, AgentEventListener listener) {
        Posting p = posting(postingId);
        if (!p.isAnalyzed()) throw new IllegalStateException("Analyze the posting first.");
        AgentGoal goal = new AgentGoal(AgentGoal.GoalType.TAILOR_RESUME, p, List.of());
        return agent.run(goal, questions, listener);
    }

    /** F03: fit score, gaps, closest saved resume and the recommended strategy. */
    public FitReport analyzeFit(String postingId) {
        PostingAnalysis a = analysisOf(postingId);
        return fitScorer.score(a, vault.skills(), library.findClosest(a));
    }

    /** F04: literal keyword coverage of one resume against one posting. */
    public KeywordReport scanKeywords(String resumeId, String postingId) {
        Resume r = library.byId(resumeId)
                .orElseThrow(() -> new IllegalArgumentException("No resume with id " + resumeId));
        return keywordScanner.scan(r, analysisOf(postingId));
    }

    private PostingAnalysis analysisOf(String postingId) {
        return posting(postingId).getAnalysis()
                .orElseThrow(() -> new IllegalStateException("Analyze the posting first."));
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
