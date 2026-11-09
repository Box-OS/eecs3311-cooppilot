package ca.yorku.cooppilot.service.edit;

import ca.yorku.cooppilot.domain.Resume;
import ca.yorku.cooppilot.domain.ResumeBlock;
import ca.yorku.cooppilot.llm.RevisionTask;

import java.util.List;
import java.util.function.Supplier;

/**
 * Command pattern — concrete command: apply a natural-language revision ("make the tutor bullet about
 * Java"). The LLM runs once on the first execute; undo restores the snapshot, redo reapplies the result.
 */
public class AiRevisionCommand implements ResumeEditCommand {

    private final Resume resume;
    private final String instruction;
    private final Supplier<RevisionTask> taskFactory;
    private List<ResumeBlock> before;
    private List<ResumeBlock> after;
    private String summary = "";

    public AiRevisionCommand(Resume resume, String instruction, Supplier<RevisionTask> taskFactory) {
        this.resume = resume;
        this.instruction = instruction;
        this.taskFactory = taskFactory;
    }

    @Override
    public void execute() {
        before = Edits.snapshot(resume);
        if (after == null) {
            RevisionTask.Result r = taskFactory.get().run();
            Edits.apply(resume, r);
            summary = r.summary();
            after = Edits.snapshot(resume);
        } else {
            resume.setBlocks(Edits.copies(after));
        }
    }

    @Override
    public void undo() {
        resume.setBlocks(Edits.copies(before));
    }

    @Override
    public String description() {
        return "You asked: \"" + instruction + "\"" + (summary.isBlank() ? "" : " — " + summary);
    }
}
