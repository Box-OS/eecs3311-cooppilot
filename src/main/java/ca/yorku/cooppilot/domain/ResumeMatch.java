package ca.yorku.cooppilot.domain;

/** A saved resume and how well it already covers a posting (0-100). */
public record ResumeMatch(Resume resume, int matchPercent) {
}
