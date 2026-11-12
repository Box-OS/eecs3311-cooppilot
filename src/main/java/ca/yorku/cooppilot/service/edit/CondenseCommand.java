package ca.yorku.cooppilot.service.edit;

import ca.yorku.cooppilot.domain.PostingAnalysis;
import ca.yorku.cooppilot.domain.Resume;
import ca.yorku.cooppilot.domain.ResumeBlock;
import ca.yorku.cooppilot.service.CondenseStrategy;
import ca.yorku.cooppilot.service.PageEstimator;

import java.util.List;

/**
 * Command pattern — concrete command: fit the resume to one page with a chosen condense strategy
 * (Strategy pattern). Runs the strategy once; undo restores the snapshot, redo reapplies the result.
 */
public class CondenseCommand implements ResumeEditCommand {

    private final Resume resume;
    private final CondenseStrategy strategy;
    private final PostingAnalysis analysis;
    private final PageEstimator pages;
    private List<ResumeBlock> before;
    private List<ResumeBlock> after;
    private String report = "";
    private long fromPercent;
    private long toPercent;

    public CondenseCommand(Resume resume, CondenseStrategy strategy, PostingAnalysis analysis, PageEstimator pages) {
        this.resume = resume;
        this.strategy = strategy;
        this.analysis = analysis;
        this.pages = pages;
    }

    @Override
    public void execute() {
        before = Edits.snapshot(resume);
        if (after == null) {
            fromPercent = Math.round(pages.estimate(resume) * 100);
            Resume condensed = strategy.condense(resume, analysis, pages);
            report = strategy.lastReport();
            after = Edits.copies(condensed.getBlocks());
            toPercent = Math.round(pages.estimate(condensed) * 100);
        }
        resume.setBlocks(Edits.copies(after));
    }

    @Override
    public void undo() {
        resume.setBlocks(Edits.copies(before));
    }

    @Override
    public String description() {
        return "Condensed " + fromPercent + "% -> " + toPercent + "% of a page: " + report;
    }
}
