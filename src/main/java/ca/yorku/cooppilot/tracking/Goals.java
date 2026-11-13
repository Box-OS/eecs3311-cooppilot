package ca.yorku.cooppilot.tracking;

/** Daily and weekly application targets. */
public record Goals(int daily, int weekly) {

    public Goals {
        // UC11 3a: a goal must be at least 1
        if (daily < 1 || weekly < 1) throw new IllegalArgumentException("Goals must be at least 1 per day and 1 per week.");
        if (weekly < daily) throw new IllegalArgumentException("The weekly goal cannot be smaller than the daily goal.");
    }
}
