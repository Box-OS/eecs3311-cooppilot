package ca.yorku.cooppilot.tracking;

/** Daily and weekly application targets. */
public record Goals(int daily, int weekly) {

    public Goals {
        if (daily < 0 || weekly < 0) throw new IllegalArgumentException("goals cannot be negative");
    }
}
