package ca.yorku.cooppilot.app;

import ca.yorku.cooppilot.agent.AgentController;
import ca.yorku.cooppilot.agent.AgentEventListener;
import ca.yorku.cooppilot.agent.AgentGoal;
import ca.yorku.cooppilot.agent.AgentResult;
import ca.yorku.cooppilot.agent.QuestionHandler;
import ca.yorku.cooppilot.domain.CoverLetter;
import ca.yorku.cooppilot.domain.FitReport;
import ca.yorku.cooppilot.domain.KeywordReport;
import ca.yorku.cooppilot.domain.Posting;
import ca.yorku.cooppilot.domain.PostingAnalysis;
import ca.yorku.cooppilot.domain.Resume;
import ca.yorku.cooppilot.domain.VaultEntry;
import ca.yorku.cooppilot.llm.CoverLetterTask;
import ca.yorku.cooppilot.llm.LLMClient;
import ca.yorku.cooppilot.persistence.CoverLetterRepository;
import ca.yorku.cooppilot.service.ExportService;
import ca.yorku.cooppilot.llm.PostingAnalysisTask;
import ca.yorku.cooppilot.persistence.PostingRepository;
import ca.yorku.cooppilot.persistence.ResumeRepository;
import ca.yorku.cooppilot.service.FitScorer;
import ca.yorku.cooppilot.service.KeywordScanner;
import ca.yorku.cooppilot.service.LatexResumeParser;
import ca.yorku.cooppilot.service.ResumeLibrary;
import ca.yorku.cooppilot.service.ParseResult;
import ca.yorku.cooppilot.service.VaultService;
import ca.yorku.cooppilot.service.edit.ResumeEditor;
import ca.yorku.cooppilot.tracking.Application;
import ca.yorku.cooppilot.tracking.ApplicationTracker;
import ca.yorku.cooppilot.tracking.DashboardService;
import ca.yorku.cooppilot.tracking.DashboardStats;
import ca.yorku.cooppilot.tracking.Goals;
import ca.yorku.cooppilot.tracking.Status;
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
    private final ApplicationTracker tracker;
    private final DashboardService dashboard;
    private ResumeEditor editor;
    private java.util.Map<ca.yorku.cooppilot.service.CondenseStrategy.Mode, ca.yorku.cooppilot.service.CondenseStrategy> condensers;
    private ca.yorku.cooppilot.service.PageEstimator pages;
    private final ca.yorku.cooppilot.service.ResumePreview resumePreview = new ca.yorku.cooppilot.service.ResumePreview();

    @org.springframework.beans.factory.annotation.Autowired
    void setCondense(java.util.Map<ca.yorku.cooppilot.service.CondenseStrategy.Mode, ca.yorku.cooppilot.service.CondenseStrategy> condensers,
                     ca.yorku.cooppilot.service.PageEstimator pages) {
        this.condensers = condensers;
        this.pages = pages;
    }
    private CoverLetterRepository letters;
    private ExportService export;
    private String studentName = "";

    @org.springframework.beans.factory.annotation.Autowired
    void setLetters(CoverLetterRepository letters, ExportService export,
                    @org.springframework.beans.factory.annotation.Value("${cooppilot.student-name:}") String studentName) {
        this.letters = letters;
        this.export = export;
        this.studentName = studentName;
    }

    /** Setter injection keeps the constructor from growing for every feature. */
    @org.springframework.beans.factory.annotation.Autowired
    void setEditor(ResumeEditor editor) {
        this.editor = editor;
    }

    public CoopPilotFacade(LatexResumeParser parser, VaultService vault, ResumeRepository resumes,
                           PostingRepository postings, LLMClient llm, FitScorer fitScorer,
                           KeywordScanner keywordScanner, ResumeLibrary library, AgentController agent,
                           ApplicationTracker tracker, DashboardService dashboard) {
        this.agent = agent;
        this.tracker = tracker;
        this.dashboard = dashboard;
        this.parser = parser;
        this.vault = vault;
        this.resumes = resumes;
        this.postings = postings;
        this.llm = llm;
        this.fitScorer = fitScorer;
        this.keywordScanner = keywordScanner;
        this.library = library;
    }

    /** F08: plain-English revision ("make the tutor bullet focus on Java"); undoable. */
    public Resume reviseResume(String resumeId, String instruction) {
        return editor.revise(resumeId, instruction);
    }

    /** F08: manual bullet edit; undoable. */
    public Resume replaceBullet(String resumeId, String blockId, int index, String text) {
        return editor.replaceBullet(resumeId, blockId, index, text);
    }

    /** F08: remove an entry; undoable. */
    public Resume removeBlock(String resumeId, String blockId) {
        return editor.removeBlock(resumeId, blockId);
    }

    @org.springframework.beans.factory.annotation.Autowired
    private ca.yorku.cooppilot.service.BackupImporter backupImporter;

    /** F01: import a JSON backup (vault entries + resumes), e.g. exported from another tracker. */
    public ca.yorku.cooppilot.service.BackupImporter.Result importBackup(String json) {
        return backupImporter.importJson(json);
    }

    /** Readable, PDF-like view of a resume for the GUI. */
    public ca.yorku.cooppilot.service.ResumePreview.Preview preview(String resumeId) {
        return resumePreview.build(resume(resumeId));
    }

    /** F07: how much of one page the resume fills (1.0 = full). */
    public double pageEstimate(String resumeId) {
        return pages.estimate(resume(resumeId));
    }

    /** F07: fit a resume to one page for a posting, with RULES (free) or SMART (1 LLM request); undoable. */
    public Resume condenseResume(String resumeId, String postingId, ca.yorku.cooppilot.service.CondenseStrategy.Mode mode) {
        PostingAnalysis a = analysisOf(postingId);
        return editor.condense(resumeId, condensers.get(mode), a, pages);
    }

    public String undo(String resumeId) {
        return editor.undo(resumeId);
    }

    public String redo(String resumeId) {
        return editor.redo(resumeId);
    }

    public List<String> revisionLog(String resumeId) {
        return editor.log(resumeId);
    }

    public Resume resume(String resumeId) {
        return library.byId(resumeId).orElseThrow(() -> new IllegalArgumentException("No resume with id " + resumeId));
    }

    /**
     * F09: drafts a cover letter for an analyzed posting from a resume (usually the tailored one) and
     * the vault facts letters may use (ALL and LETTERS_ONLY; never CONTEXT_ONLY). Saved as CL_Company_Role.
     */
    public CoverLetter writeCoverLetter(String postingId, String resumeId) {
        Posting p = posting(postingId);
        if (!p.isAnalyzed()) throw new IllegalStateException("Analyze the posting first.");
        Resume r = resume(resumeId);
        String facts = vault.all().stream().filter(e -> e.getUse().allowedInLetter())
                .map(e -> e.getTitle() + ": " + e.getBody().replace('\n', ' '))
                .collect(java.util.stream.Collectors.joining("\n"));
        String body = new CoverLetterTask(llm, p, r, facts, studentName).run();
        CoverLetter letter = new CoverLetter(export.fileName("CL", p.getCompany(), p.getRole()), p.getId(), body);
        letters.save(letter);
        return letter;
    }

    public java.util.Optional<CoverLetter> coverLetter(String name) {
        return letters.findByName(name);
    }

    public List<CoverLetter> coverLetters() {
        return letters.findAll();
    }

    /**
     * F10: reuse a saved resume unchanged for a posting when it already covers it (>= 75% match):
     * saved as a copy named CV_Company_Role.
     */
    public Resume reuseResume(String postingId, String resumeId) {
        Posting p = posting(postingId);
        return library.reuseAs(resumeId, export.fileName("CV", p.getCompany(), p.getRole()));
    }

    /** F10: the library grouped by role family. */
    public java.util.Map<String, List<Resume>> resumeLibrary() {
        return library.groupByFamily();
    }

    public void renameResume(String resumeId, String newName) {
        library.rename(resumeId, newName);
    }

    public void setResumeFamily(String resumeId, String family) {
        library.setFamily(resumeId, family);
    }

    public void deleteResume(String resumeId) {
        library.delete(resumeId);
    }

    /** F10: writes the resume's .tex to the export folder. */
    public java.nio.file.Path exportResume(String resumeId) {
        return export.writeTex(resume(resumeId));
    }

    /** F10: writes a cover letter to the export folder. */
    public java.nio.file.Path exportCoverLetter(String name) {
        return export.writeLetter(coverLetter(name).orElseThrow(() -> new IllegalArgumentException("No cover letter " + name)));
    }

    /** F11: logs an application (starts in TO_APPLY). Company/role default to the posting's. */
    public Application logApplication(String postingId, String company, String role, String resumeName) {
        if (postingId != null && !postingId.isBlank()) {
            Posting p = posting(postingId);
            if (company == null || company.isBlank()) company = p.getCompany();
            if (role == null || role.isBlank()) role = p.getRole();
        }
        return tracker.create(company, role, postingId, resumeName);
    }

    /** F11: moves an application to a new status; illegal moves throw IllegalStateException. */
    public Application changeStatus(String applicationId, Status target, String note) {
        return tracker.transition(applicationId, target, note);
    }

    public List<Application> applications() {
        return tracker.all();
    }

    public Application application(String id) {
        return tracker.find(id);
    }

    /** F12. */
    public DashboardStats dashboard() {
        return dashboard.stats();
    }

    public void setGoals(int daily, int weekly) {
        dashboard.setGoals(new Goals(daily, weekly));
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
