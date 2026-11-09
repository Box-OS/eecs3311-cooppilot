package ca.yorku.cooppilot.tracking;

import ca.yorku.cooppilot.app.CoopPilotApplication;
import ca.yorku.cooppilot.app.CoopPilotFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/** F11 + F12 through the facade with SQLite: persistence of state/history and dashboard refresh. */
@SpringBootTest(classes = CoopPilotApplication.class)
class TrackerFlowTest {

    @Autowired CoopPilotFacade facade;
    @Autowired ApplicationRepository repo;

    @BeforeEach
    void clean() {
        repo.findAll().forEach(a -> repo.delete(a.getId()));
    }

    @Test
    void statusAndHistorySurviveReloadAndDashboardUpdates() {
        int before = facade.dashboard().totalApplied();
        Application a = facade.logApplication(null, "Northwind Labs", "Developer Co-op", "CV_NorthwindLabs_Developer");
        assertEquals(before, facade.dashboard().totalApplied(), "TO_APPLY does not count as applied");

        facade.changeStatus(a.getId(), Status.APPLIED, "York portal");
        facade.changeStatus(a.getId(), Status.ASSESSMENT, "OA");

        Application reloaded = facade.application(a.getId());
        assertEquals(Status.ASSESSMENT, reloaded.getStatus());
        assertEquals(2, reloaded.getHistory().size());
        assertEquals("OA", reloaded.getHistory().get(1).note());
        assertNotNull(reloaded.getAppliedOn());

        DashboardStats s = facade.dashboard();
        assertEquals(before + 1, s.totalApplied(), "the dashboard observed the change");
        assertEquals(100, s.responseRatePercent());
    }

    @Test
    void illegalTransitionIsRefusedAndNotSaved() {
        Application a = facade.logApplication(null, "Acme", "Dev", "");
        assertThrows(IllegalStateException.class, () -> facade.changeStatus(a.getId(), Status.OFFER, ""));
        assertEquals(Status.TO_APPLY, facade.application(a.getId()).getStatus());
    }

    @Test
    void goalsArePersisted() {
        facade.setGoals(4, 20);
        assertEquals(new Goals(4, 20), facade.dashboard().goals());
    }
}
