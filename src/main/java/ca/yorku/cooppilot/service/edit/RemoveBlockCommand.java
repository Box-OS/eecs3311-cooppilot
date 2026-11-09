package ca.yorku.cooppilot.service.edit;

import ca.yorku.cooppilot.domain.Resume;
import ca.yorku.cooppilot.domain.ResumeBlock;

import java.util.ArrayList;
import java.util.List;

/** Command pattern — concrete command: remove a whole entry; undo puts it back where it was. */
public class RemoveBlockCommand implements ResumeEditCommand {

    private final Resume resume;
    private final String blockId;
    private ResumeBlock removed;
    private int position;

    public RemoveBlockCommand(Resume resume, String blockId) {
        this.resume = resume;
        this.blockId = blockId;
        Edits.block(resume, blockId); // fail fast on unknown ids
    }

    @Override
    public void execute() {
        List<ResumeBlock> blocks = new ArrayList<>(resume.getBlocks());
        for (int i = 0; i < blocks.size(); i++) {
            if (blocks.get(i).getId().equals(blockId)) {
                position = i;
                removed = blocks.remove(i);
                break;
            }
        }
        resume.setBlocks(blocks);
    }

    @Override
    public void undo() {
        List<ResumeBlock> blocks = new ArrayList<>(resume.getBlocks());
        blocks.add(Math.min(position, blocks.size()), removed);
        resume.setBlocks(blocks);
    }

    @Override
    public String description() {
        return "Removed " + (removed != null ? removed.getTitle() : blockId);
    }
}
