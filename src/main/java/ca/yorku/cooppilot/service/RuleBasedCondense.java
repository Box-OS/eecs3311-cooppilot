package ca.yorku.cooppilot.service;

import ca.yorku.cooppilot.domain.Keyword;
import ca.yorku.cooppilot.domain.PostingAnalysis;
import ca.yorku.cooppilot.domain.Resume;
import ca.yorku.cooppilot.domain.ResumeBlock;
import ca.yorku.cooppilot.domain.Section;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Strategy pattern — concrete strategy (free, no LLM). Removes what is least relevant to the posting,
 * in a fixed order, until the resume fits: irrelevant projects, long projects to 2 bullets, jobs to
 * 2 bullets, the weakest extra project, projects to 1 bullet, jobs to 1 bullet. Jobs give way last;
 * at least 1 project is kept.
 */
@Service
public class RuleBasedCondense implements CondenseStrategy {

    private final ThreadLocal<List<String>> report = ThreadLocal.withInitial(ArrayList::new);

    @Override
    public Resume condense(Resume original, PostingAnalysis a, PageEstimator pages) {
        Resume r = original.copy();
        List<String> log = new ArrayList<>();
        report.set(log);

        // 1. projects with no posting keyword at all (keep at least 2 projects)
        while (!pages.fitsOnePage(r) && projects(r).size() > 2) {
            ResumeBlock weakest = weakest(projects(r), a);
            if (relevance(weakest, a) > 0) break;
            remove(r, weakest, log, "dropped project with no posting keywords");
        }
        // 2-3. long projects, then jobs, down to 2 bullets
        trimTo(r, a, pages, 2, b -> b.getSection() == Section.PROJECTS, log);
        trimTo(r, a, pages, 2, b -> b.getSection() == Section.EXPERIENCE || b.getSection() == Section.LEADERSHIP, log);
        // 4. weakest extra project (keep 1)
        while (!pages.fitsOnePage(r) && projects(r).size() > 1) {
            remove(r, weakest(projects(r), a), log, "dropped least relevant project");
        }
        // 5-6. projects, then jobs, to 1 bullet
        trimTo(r, a, pages, 1, b -> b.getSection() == Section.PROJECTS, log);
        trimTo(r, a, pages, 1, b -> b.getSection() == Section.EXPERIENCE || b.getSection() == Section.LEADERSHIP, log);

        if (!pages.fitsOnePage(r)) log.add("still over one page after all rule steps; try smart condense");
        return r;
    }

    @Override
    public String lastReport() {
        List<String> log = report.get();
        return log.isEmpty() ? "nothing to cut" : String.join("; ", log);
    }

    private static void trimTo(Resume r, PostingAnalysis a, PageEstimator pages, int max,
                               Predicate<ResumeBlock> which, List<String> log) {
        List<ResumeBlock> candidates = new ArrayList<>(r.getBlocks().stream().filter(which)
                .filter(b -> b.getBullets().size() > max).toList());
        candidates.sort(Comparator.comparingInt(b -> relevance(b, a)));   // least relevant first
        for (ResumeBlock b : candidates) {
            if (pages.fitsOnePage(r)) return;
            List<String> bullets = new ArrayList<>(b.getBullets());
            while (bullets.size() > max) {
                int worst = 0;
                for (int i = 1; i < bullets.size(); i++) {
                    if (bulletScore(bullets.get(i), a) <= bulletScore(bullets.get(worst), a)) worst = i;
                }
                bullets.remove(worst);
            }
            b.setBullets(bullets);
            log.add(b.getTitle() + " trimmed to " + max + " bullet" + (max == 1 ? "" : "s"));
        }
    }

    private static void remove(Resume r, ResumeBlock b, List<String> log, String why) {
        List<ResumeBlock> blocks = new ArrayList<>(r.getBlocks());
        blocks.removeIf(x -> x.getId().equals(b.getId()));
        r.setBlocks(blocks);
        log.add(why + ": " + b.getTitle());
    }

    private static List<ResumeBlock> projects(Resume r) {
        return r.blocksIn(Section.PROJECTS);
    }

    private static ResumeBlock weakest(List<ResumeBlock> blocks, PostingAnalysis a) {
        return blocks.stream().min(Comparator.comparingInt((ResumeBlock b) -> relevance(b, a))
                .thenComparing(ResumeBlock::sortDate)).orElseThrow();
    }

    /** Keyword hits in a block (must-haves count twice). */
    static int relevance(ResumeBlock b, PostingAnalysis a) {
        String text = ResumeText.block(b) + " " + String.join(" ", b.getTags());
        int score = 0;
        for (Keyword k : a.keywords()) if (k.appearsIn(text)) score += k.mustHave() ? 2 : 1;
        return score;
    }

    private static int bulletScore(String bullet, PostingAnalysis a) {
        String text = LatexText.plain(bullet);
        int score = 0;
        for (Keyword k : a.keywords()) if (k.appearsIn(text)) score += k.mustHave() ? 2 : 1;
        return score;
    }
}
