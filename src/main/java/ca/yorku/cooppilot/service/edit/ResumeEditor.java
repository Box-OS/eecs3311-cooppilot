package ca.yorku.cooppilot.service.edit;

import ca.yorku.cooppilot.domain.Resume;
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

    private record Editing(Resume resume, RevisionHistory history) {}

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

    public Resume removeBlock(String resumeId, String blockId) {
        Editing e = editing(resumeId);
        return run(e, new RemoveBlockCommand(e.resume(), blockId));
    }

    public String undo(String resumeId) {
        Editing e = editing(resumeId);
        String what = e.history().undo();
        save(e);
        return what;
    }

    public String redo(String resumeId) {
        Editing e = editing(resumeId);
        String what = e.history().redo();
        save(e);
        return what;
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
        e.history().run(c);
        save(e);
        return e.resume();
    }

    private void save(Editing e) {
        String template = e.resume().getLatex();
        assembler.assemble(e.resume(), template);
        library.save(e.resume());
    }

    private Editing editing(String resumeId) {
        return open.computeIfAbsent(resumeId, id -> new Editing(
                library.byId(id).orElseThrow(() -> new IllegalArgumentException("No resume with id " + id)),
                new RevisionHistory()));
    }

    private String evidence(Resume r) {
        String vaultText = vault.all().stream().filter(v -> v.getUse().allowedOnResume())
                .map(v -> v.getTitle() + ": " + v.getBody().replace('\n', ' ')).collect(Collectors.joining("\n"));
        String resumeText = r.getBlocks().stream().flatMap(b -> b.getBullets().stream()).map(LatexText::plain)
                .collect(Collectors.joining(" "));
        return vaultText + "\n" + resumeText;
    }
}
