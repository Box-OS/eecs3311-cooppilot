package ca.yorku.cooppilot.service.edit;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Command pattern — Invoker. Runs commands and keeps the undo and redo stacks. */
public class RevisionHistory {

    private final Deque<ResumeEditCommand> undo = new ArrayDeque<>();
    private final Deque<ResumeEditCommand> redo = new ArrayDeque<>();

    public void run(ResumeEditCommand c) {
        c.execute();
        undo.push(c);
        redo.clear(); // a new edit invalidates the redo branch
    }

    public String undo() {
        if (undo.isEmpty()) throw new IllegalStateException("Nothing to undo.");
        ResumeEditCommand c = undo.pop();
        c.undo();
        redo.push(c);
        return c.description();
    }

    public String redo() {
        if (redo.isEmpty()) throw new IllegalStateException("Nothing to redo.");
        ResumeEditCommand c = redo.pop();
        c.execute();
        undo.push(c);
        return c.description();
    }

    public boolean canUndo() { return !undo.isEmpty(); }

    public boolean canRedo() { return !redo.isEmpty(); }

    /** Oldest first. */
    public List<String> log() {
        List<String> out = new ArrayList<>();
        undo.descendingIterator().forEachRemaining(c -> out.add(c.description()));
        return out;
    }
}
