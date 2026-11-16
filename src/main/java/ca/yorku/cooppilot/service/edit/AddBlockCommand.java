package ca.yorku.cooppilot.service.edit;

import ca.yorku.cooppilot.domain.Resume;
import ca.yorku.cooppilot.domain.ResumeBlock;

import java.util.ArrayList;
import java.util.List;

/**
 * Command pattern — concrete command: add an entry taken from another saved resume (a copy with its own id); undo takes it
 * out again. Nothing is invented: the entry's title, dates, bullets and LaTeX are the student's own, from their library.
 */
public class AddBlockCommand implements ResumeEditCommand {

    private final Resume resume;
    private final ResumeBlock added;

    public AddBlockCommand(Resume resume, ResumeBlock from) {
        this.resume = resume;
        ResumeBlock b = new ResumeBlock(null, from.getSection(), from.getTitle(), from.getSubtitle(), from.getStartDate(),
                from.getEndDate(), from.getBullets(), from.getLatex());
        from.getTags().forEach(b::addTag);
        this.added = b;
    }

    @Override
    public void execute() {
        List<ResumeBlock> blocks = new ArrayList<>(resume.getBlocks());
        blocks.add(added);
        resume.setBlocks(blocks);
    }

    @Override
    public void undo() {
        List<ResumeBlock> blocks = new ArrayList<>(resume.getBlocks());
        blocks.removeIf(b -> b.getId().equals(added.getId()));
        resume.setBlocks(blocks);
    }

    @Override
    public String description() {
        return "Added " + added.getTitle();
    }
}
