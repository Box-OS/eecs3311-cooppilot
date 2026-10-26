package ca.yorku.cooppilot.domain;

/** Where a vault entry may be used. CONTEXT_ONLY entries keep the agent accurate but are never quoted. */
public enum EntryUse {
    ALL, LETTERS_ONLY, CONTEXT_ONLY;

    public boolean allowedOnResume() {
        return this == ALL;
    }

    public boolean allowedInLetter() {
        return this == ALL || this == LETTERS_ONLY;
    }
}
