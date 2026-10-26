package ca.yorku.cooppilot.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point. Starts the web GUI by default; the CLI is added as a CommandLineRunner.
 */
@SpringBootApplication(scanBasePackages = "ca.yorku.cooppilot")
public class CoopPilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoopPilotApplication.class, args);
    }
}
