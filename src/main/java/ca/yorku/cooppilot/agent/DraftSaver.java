package ca.yorku.cooppilot.agent;

import ca.yorku.cooppilot.domain.Posting;
import ca.yorku.cooppilot.domain.Resume;

/** Keeps the agent's unfinished draft when a run stops early (step limit or failure), so work is not lost. */
@FunctionalInterface
public interface DraftSaver {

    /** Saves the draft and returns the saved resume (e.g. named CV_Company_Role_Draft). */
    Resume saveDraft(Resume draft, Posting posting);
}
