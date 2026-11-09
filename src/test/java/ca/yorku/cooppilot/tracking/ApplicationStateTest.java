package ca.yorku.cooppilot.tracking;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ApplicationStateTest {

    private static final LocalDate MON = LocalDate.of(2026, 11, 2);

    @Test
    void applyingStampsDateAndSchedulesFollowUp() {
        Application a = new Application(null, "Northwind", "Dev", null, "CV_Northwind_Dev");
        assertEquals(Status.TO_APPLY, a.getStatus());
        a.transitionTo(Status.APPLIED, MON, "via portal");
        assertEquals(MON, a.getAppliedOn());
        assertEquals(MON.plusDays(10), a.getFollowUpOn());
        assertFalse(a.followUpDue(MON.plusDays(9)));
        assertTrue(a.followUpDue(MON.plusDays(10)));
    }

    @Test
    void fullHappyPathIsRecordedInHistory() {
        Application a = new Application(null, "Northwind", "Dev", null, "");
        a.transitionTo(Status.APPLIED, MON, "");
        a.transitionTo(Status.ASSESSMENT, MON.plusDays(3), "HackerRank");
        a.transitionTo(Status.INTERVIEW, MON.plusDays(8), "round 1");
        a.transitionTo(Status.INTERVIEW, MON.plusDays(12), "round 2");
        a.transitionTo(Status.OFFER, MON.plusDays(20), "");
        assertTrue(a.getState().isTerminal());
        assertNull(a.getFollowUpOn(), "no follow-up once there is an answer");
        assertEquals(5, a.getHistory().size());
        assertEquals(Status.INTERVIEW, a.getHistory().get(3).from());
    }

    @Test
    void illegalMovesAreRejectedByTheState() {
        Application a = new Application(null, "Northwind", "Dev", null, "");
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> a.transitionTo(Status.INTERVIEW, MON, ""));
        assertTrue(e.getMessage().contains("TO_APPLY"));
        a.transitionTo(Status.APPLIED, MON, "");
        a.transitionTo(Status.REJECTED, MON.plusDays(5), "");
        assertThrows(IllegalStateException.class, () -> a.transitionTo(Status.OFFER, MON.plusDays(6), ""));
        assertEquals(Status.REJECTED, a.getStatus(), "a failed move changes nothing");
    }

    @Test
    void ghostedApplicationCanBeRevivedByALateReply() {
        Application a = new Application(null, "Northwind", "Dev", null, "");
        a.transitionTo(Status.APPLIED, MON, "");
        a.transitionTo(Status.GHOSTED, MON.plusDays(30), "");
        a.transitionTo(Status.INTERVIEW, MON.plusDays(40), "they called");
        assertEquals(Status.INTERVIEW, a.getStatus());
    }

    @Test
    void dashboardNumbers() {
        List<Application> apps = new ArrayList<>();
        LocalDate today = MON.plusDays(3); // Thursday
        for (int d = 0; d <= 3; d++) {           // Mon..Thu: 2 per day
            for (int k = 0; k < 2; k++) {
                Application a = new Application(null, "C" + d + k, "Dev", null, "");
                a.transitionTo(Status.APPLIED, MON.plusDays(d), "");
                apps.add(a);
            }
        }
        apps.get(0).transitionTo(Status.INTERVIEW, today, "");
        apps.get(1).transitionTo(Status.REJECTED, today, "");
        apps.add(new Application(null, "Not yet", "Dev", null, ""));

        DashboardStats s = DashboardService.compute(apps, new Goals(2, 10), today);
        assertEquals(8, s.totalApplied());
        assertEquals(25, s.responseRatePercent(), "2 of 8 answered");
        assertEquals(1, s.interviews());
        assertEquals(2, s.appliedToday());
        assertEquals(8, s.appliedThisWeek());
        assertTrue(s.dailyGoalMet());
        assertFalse(s.weeklyGoalMet());
        assertEquals(4, s.streakDays());
        assertEquals(7, s.lastSevenDays().size());
        assertEquals(1, s.byStatus().get(Status.TO_APPLY));
    }

    @Test
    void streakCountsFromYesterdayWhenTodayIsNotDoneYet() {
        List<Application> apps = new ArrayList<>();
        for (int d = 1; d <= 2; d++) {
            Application a = new Application(null, "C" + d, "Dev", null, "");
            a.transitionTo(Status.APPLIED, MON.minusDays(d), "");
            apps.add(a);
        }
        assertEquals(2, DashboardService.streak(apps, new Goals(1, 5), MON));
        assertEquals(0, DashboardService.streak(apps, new Goals(0, 5), MON));
    }
}
