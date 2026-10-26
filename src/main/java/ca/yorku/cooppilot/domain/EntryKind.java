package ca.yorku.cooppilot.domain;

/** What a vault entry describes. PLANNED entries are ideas and never appear on a resume as finished work. */
public enum EntryKind {
    PROJECT, WORK, COURSE, ACTIVITY, SKILL, PLANNED
}
