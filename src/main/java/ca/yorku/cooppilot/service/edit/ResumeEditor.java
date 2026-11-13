package ca.yorku.cooppilot.service.edit;

import ca.yorku.cooppilot.domain.PostingAnalysis;
import ca.yorku.cooppilot.domain.Resume;
import ca.yorku.cooppilot.domain.ResumeBlock;
import ca.yorku.cooppilot.service.CondenseStrategy;
import ca.yorku.cooppilot.service.PageEstimator;
import ca.yorku.cooppilot.llm.LLMClient;
import ca.yorku.cooppilot.llm.RevisionTask;
import ca.yorku.cooppilot.service.LatexAssembler;
import ca.yorku.cooppilot.service.LatexText;
import ca.yorku.cooppilot.service.ResumeLibrary;
import ca.yorku.cooppilot.service.VaultService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * F08: creates the edit commands, runs them through one RevisionHistory per resume, and reassembles
 * and saves the resume after every execute, undo and redo.
 */
@Service
public class ResumeEditor {

    /** One open resume: the working copy, its undo/redo history, the stored LaTeX it was loaded from, and the last diff. */
    private static final class Editing {
        final Resume resume;
        final RevisionHistory history = new RevisionHistory();
        String loadedLatex;
        List<ResumeDiff.Change> lastChanges = List.of();
        String lastAction = "";

        Editing(Resume resume) {
            this.resume = resume;
            this.loadedLatex = resume.getLatex();
        }

        Resume resume() { return resume; }
        RevisionHistory history() { return history; }
    }

    private final ResumeLibrary library;
    private final VaultService vault;
    private final LatexAssembler assembler;
    private final LLMClient llm;
    private final Map<String, Editing> open = new ConcurrentHashMap<>();

    public ResumeEditor(ResumeLibrary library, VaultService vault, LatexAssembler assembler, LLMClient llm) {
        this.library = library;
        this.vault = vault;
        this.assembler = assembler;
        this.llm = llm;
    }

    public Resume revise(String resumeId, String instruction) {
        Editing e = editing(resumeId);
        String evidence = evidence(e.resume());
        return run(e, new AiRevisionCommand(e.resume(), instruction,
                () -> new RevisionTask(llm, e.resume(), instruction, evidence)));
    }

    public Resume replaceBullet(String resumeId, String blockId, int index, String text) {
        Editing e = editing(resumeId);
        return run(e, new ReplaceBulletCommand(e.resume(), blockId, index, text));
    }

    /** F07: fit to one page with the given strategy, as an undoable command. */
    public Resume condense(String resumeId, CondenseStrategy strategy, PostingAnalysis analysis, PageEstimator pages) {
        Editing e = editing(resumeId);
        return run(e, new CondenseCommand(e.resume(), strategy, analysis, pages));
    }

    public Resume removeBlock(String resumeId, String blockId) {
        Editing e = editing(resumeId);
        return run(e, new RemoveBlockCommand(e.resume(), blockId));
    }

    public String undo(String resumeId) {
        Editing e = editing(resumeId);
        List<ResumeBlock> before = snapshot(e);
        String what = e.history().undo();
        save(e);
        record(e, before, "Undid: " + what);
        return what;
    }

    public String redo(String resumeId) {
        Editing e = editing(resumeId);
        List<ResumeBlock> before = snapshot(e);
        String what = e.history().redo();
        save(e);
        record(e, before, "Redid: " + what);
        return what;
    }

    /** What the last edit, undo or redo changed (UC07: "What changed"). */
    public List<ResumeDiff.Change> lastChanges(String resumeId) {
        Editing e = open.get(resumeId);
        return e == null ? List.of() : e.lastChanges;
    }

    public String lastAction(String resumeId) {
        Editing e = open.get(resumeId);
        return e == null ? "" : e.lastAction;
    }

    private static List<ResumeBlock> snapshot(Editing e) {
        return e.resume().getBlocks().stream().map(ResumeBlock::copy).toList();
    }

    private static void record(Editing e, List<ResumeBlock> before, String action) {
        e.lastChanges = ResumeDiff.between(before, e.resume().getBlocks());
        e.lastAction = action;
    }

    public List<String> log(String resumeId) {
        return editing(resumeId).history().log();
    }

    public boolean canUndo(String resumeId) {
        return editing(resumeId).history().canUndo();
    }

    public boolean canRedo(String resumeId) {
        return editing(resumeId).history().canRedo();
    }

    private Resume run(Editing e, ResumeEditCommand c) {
        List<ResumeBlock> before = snapshot(e);
        e.history().run(c);
        save(e);
        record(e, before, c.description());
        return e.resume();
    }

    private void save(Editing e) {
        String template = e.resume().getLatex();
        assembler.assemble(e.resume(), template);
        library.save(e.resume());
        e.loadedLatex = e.resume().getLatex();
    }

    /**
     * The open working copy. If the stored resume changed outside the editor (e.g. the agent saved a new
     * version under the same name), the stale copy and its history are dropped and the new version loaded.
     */
    private Editing editing(String resumeId) {
        Resume stored = library.byId(resumeId).orElseThrow(() -> new IllegalArgumentException("No resume with id " + resumeId));
        Editing e = open.get(resumeId);
        if (e == null || !e.loadedLatex.equals(stored.getLatex())) {
            e = new Editing(stored);
            open.put(resumeId, e);
        }
        return e;
    }

    private String evidence(Resume r) {
        String vaultText = vault.all().stream().filter(v -> v.getUse().allowedOnResume())
                .map(v -> v.getTitle() + ": " + v.getBody().replace('\n', ' ')).collect(Collectors.joining("\n"));
        String resumeText = r.getBlocks().stream().flatMap(b -> b.getBullets().stream()).map(LatexText::plain)
                .collect(Collectors.joining(" "));
        return vaultText + "\n" + resumeText;
    }
}
