package ca.yorku.cooppilot.tracking;

import java.time.LocalDate;
import java.util.Map;

/** F12 numbers shown on the dashboard. {@code lastSevenDays} maps each day to applications sent. */
public record DashboardStats(int totalApplied, int responseRatePercent, int interviews, int offers,
                             int followUpsDue, int appliedToday, int appliedThisWeek, int streakDays,
                             Goals goals, Map<LocalDate, Integer> lastSevenDays, Map<Status, Integer> byStatus) {

    public boolean dailyGoalMet() {
        return goals.daily() > 0 && appliedToday >= goals.daily();
    }

    public boolean weeklyGoalMet() {
        return goals.weekly() > 0 && appliedThisWeek >= goals.weekly();
    }
}
