package ca.yorku.cooppilot.service.edit;

import ca.yorku.cooppilot.domain.Resume;
import ca.yorku.cooppilot.domain.ResumeBlock;

import java.util.ArrayList;
import java.util.List;

/** Command pattern — concrete command: replace one bullet (manual edit). */
public class ReplaceBulletCommand implements ResumeEditCommand {

    private final Resume resume;
    private final String blockId;
    private final int index;
    private final String newText;
    private String oldText;

    public ReplaceBulletCommand(Resume resume, String blockId, int index, String newText) {
        this.resume = resume;
        this.blockId = blockId;
        this.index = index;
        this.newText = newText;
        ResumeBlock b = Edits.block(resume, blockId);
        if (index < 0 || index >= b.getBullets().size()) {
            throw new IllegalArgumentException(b.getTitle() + " has no bullet " + index);
        }
    }

    @Override
    public void execute() {
        ResumeBlock b = Edits.block(resume, blockId);
        List<String> bullets = new ArrayList<>(b.getBullets());
        oldText = bullets.set(index, newText);
        b.setBullets(bullets);
    }

    @Override
    public void undo() {
        ResumeBlock b = Edits.block(resume, blockId);
        List<String> bullets = new ArrayList<>(b.getBullets());
        bullets.set(index, oldText);
        b.setBullets(bullets);
    }

    @Override
    public String description() {
        return "Replaced bullet " + index + " of " + Edits.block(resume, blockId).getTitle();
    }
}
