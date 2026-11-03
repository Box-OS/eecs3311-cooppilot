package ca.yorku.cooppilot.service;

import ca.yorku.cooppilot.domain.PostingAnalysis;
import ca.yorku.cooppilot.domain.Resume;

/**
 * Strategy pattern — Strategy. Interchangeable ways to bring a resume down to one page. The caller
 * (the condense tool or the facade) picks one by mode and never knows how it works.
 */
public interface CondenseStrategy {

    enum Mode { RULES, SMART }

    /** Returns a condensed copy; the input is not modified. */
    Resume condense(Resume r, PostingAnalysis a, PageEstimator pages);

    /** Short description of what was cut, for the user and the agent. */
    String lastReport();
}
