package ca.yorku.cooppilot.service.edit;

import ca.yorku.cooppilot.domain.Resume;
import ca.yorku.cooppilot.domain.ResumeBlock;
import ca.yorku.cooppilot.llm.RevisionTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Helpers shared by the edit commands. */
final class Edits {

    private Edits() {}

    static ResumeBlock block(Resume r, String id) {
        return r.getBlocks().stream().filter(b -> b.getId().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no block " + id));
    }

    static List<ResumeBlock> snapshot(Resume r) {
        return copies(r.getBlocks());
    }

    static List<ResumeBlock> copies(List<ResumeBlock> blocks) {
        return blocks.stream().map(ResumeBlock::copy).toList();
    }

    /** Applies validated rewrites, then removals from the highest index down. */
    static void apply(Resume r, RevisionTask.Result result) {
        for (RevisionTask.Edit e : result.rewrite()) {
            ResumeBlock b = block(r, e.blockId());
            List<String> bullets = new ArrayList<>(b.getBullets());
            bullets.set(e.index(), e.text());
            b.setBullets(bullets);
        }
        result.remove().stream().sorted(Comparator.comparingInt(RevisionTask.Edit::index).reversed()).forEach(e -> {
            ResumeBlock b = block(r, e.blockId());
            List<String> bullets = new ArrayList<>(b.getBullets());
            if (e.index() < bullets.size()) bullets.remove(e.index());
            b.setBullets(bullets);
        });
    }
}
