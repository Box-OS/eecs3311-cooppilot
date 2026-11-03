package ca.yorku.cooppilot.domain;

/** What to do for a posting: reuse a saved resume unchanged, tweak the closest one, or generate a new one. */
public enum TailorStrategy {
    REUSE, TWEAK, GENERATE
}
