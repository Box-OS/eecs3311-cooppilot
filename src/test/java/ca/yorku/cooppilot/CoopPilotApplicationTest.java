package ca.yorku.cooppilot;

import ca.yorku.cooppilot.app.CoopPilotApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = CoopPilotApplication.class)
class CoopPilotApplicationTest {

    @Test
    void contextLoads() {
        // fails if the Spring wiring or the database setup is broken
    }
}
