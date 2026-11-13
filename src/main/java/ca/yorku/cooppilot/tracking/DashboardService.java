package ca.yorku.cooppilot.tracking;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * F12. Observer pattern — Observer of the ApplicationTracker. Statistics are cached and recomputed only
 * after the tracker reports a change (or the goals change), instead of on every page view.
 */
@Service
public class DashboardService implements ApplicationListener {

    private final ApplicationTracker tracker;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private DashboardStats cached;
    private LocalDate cachedOn;

    public DashboardService(ApplicationTracker tracker, JdbcTemplate jdbc, Clock clock) {
        this.tracker = tracker;
        this.jdbc = jdbc;
        this.clock = clock;
        tracker.addListener(this);
    }

    @Override
    public synchronized void onApplicationChanged(Application app, StatusEvent event) {
        cached = null;
    }

    public synchronized DashboardStats stats() {
        LocalDate today = LocalDate.now(clock);
        if (cached == null || !today.equals(cachedOn)) {
            cached = compute(tracker.all(), goals(), today);
            cachedOn = today;
        }
        return cached;
    }

    /** Applications whose follow-up date is today or earlier, oldest first. */
    public List<Application> followUpsDue() {
        LocalDate today = LocalDate.now(clock);
        return tracker.all().stream().filter(a -> a.followUpDue(today))
                .sorted(java.util.Comparator.comparing(Application::getFollowUpOn)).toList();
    }

    public Goals goals() {
        int daily = setting("goal.daily", 3);
        int weekly = setting("goal.weekly", 15);
        try {
            return new Goals(daily, weekly);
        } catch (IllegalArgumentException e) {
            return new Goals(3, 15); // stored values from before the rule; fall back to the defaults
        }
    }

    public synchronized void setGoals(Goals g) {
        jdbc.update("INSERT OR REPLACE INTO settings (key, value) VALUES ('goal.daily', ?), ('goal.weekly', ?)",
                String.valueOf(g.daily()), String.valueOf(g.weekly()));
        cached = null;
    }

    static DashboardStats compute(List<Application> apps, Goals goals, LocalDate today) {
        List<Application> sent = apps.stream().filter(a -> a.getAppliedOn() != null).toList();
        int responded = (int) sent.stream()
                .filter(a -> a.getHistory().stream().anyMatch(e -> e.to().isResponse())).count();
        int interviews = (int) sent.stream()
                .filter(a -> a.getHistory().stream().anyMatch(e -> e.to() == Status.INTERVIEW)).count();
        int offers = (int) sent.stream().filter(a -> a.getStatus() == Status.OFFER).count();
        int followUps = (int) apps.stream().filter(a -> a.followUpDue(today)).count();

        Map<LocalDate, Integer> perDay = new LinkedHashMap<>();
        for (int i = 6; i >= 0; i--) perDay.put(today.minusDays(i), 0);
        for (Application a : sent) perDay.computeIfPresent(a.getAppliedOn(), (d, n) -> n + 1);

        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        int thisWeek = (int) sent.stream().filter(a -> !a.getAppliedOn().isBefore(monday) && !a.getAppliedOn().isAfter(today)).count();
        int todayCount = (int) sent.stream().filter(a -> a.getAppliedOn().equals(today)).count();

        Map<Status, Integer> byStatus = new EnumMap<>(Status.class);
        for (Application a : apps) byStatus.merge(a.getStatus(), 1, Integer::sum);

        return new DashboardStats(sent.size(), sent.isEmpty() ? 0 : Math.round(100f * responded / sent.size()),
                interviews, offers, followUps, todayCount, thisWeek, streak(sent, goals, today), goals, perDay, byStatus);
    }

    /** Consecutive days meeting the daily goal, ending today (or yesterday if today is not done yet). */
    static int streak(List<Application> sent, Goals goals, LocalDate today) {
        if (goals.daily() <= 0) return 0;
        Map<LocalDate, Integer> counts = new LinkedHashMap<>();
        for (Application a : sent) counts.merge(a.getAppliedOn(), 1, Integer::sum);
        LocalDate day = counts.getOrDefault(today, 0) >= goals.daily() ? today : today.minusDays(1);
        int n = 0;
        while (counts.getOrDefault(day, 0) >= goals.daily()) {
            n++;
            day = day.minusDays(1);
        }
        return n;
    }

    private int setting(String key, int fallback) {
        List<String> v = jdbc.queryForList("SELECT value FROM settings WHERE key = ?", String.class, key);
        try {
            return v.isEmpty() ? fallback : Integer.parseInt(v.get(0));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
