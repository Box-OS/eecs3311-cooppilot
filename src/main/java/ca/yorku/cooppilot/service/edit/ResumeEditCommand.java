package ca.yorku.cooppilot.service.edit;

/**
 * Command pattern — Command. One reversible change to a resume. execute() may be called again
 * after undo() (redo) and must give the same result without calling the LLM again.
 */
public interface ResumeEditCommand {

    void execute();

    void undo();

    /** Shown in the revision log, e.g. "Replaced bullet 1 of Peer Tutor". */
    String description();
}
