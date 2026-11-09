package ca.yorku.cooppilot.app;

import ca.yorku.cooppilot.ui.cli.CliRunner;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Map;

/**
 * Entry point. Starts the web GUI by default; with --cli it runs one CLI command (or the interactive
 * shell) without a web server and exits with the command's exit code.
 */
@SpringBootApplication(scanBasePackages = "ca.yorku.cooppilot")
public class CoopPilotApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(CoopPilotApplication.class);
        if (CliRunner.isCliMode(args)) {
            app.setWebApplicationType(WebApplicationType.NONE);
            app.setBannerMode(Banner.Mode.OFF);
            app.setDefaultProperties(Map.of("logging.level.root", "WARN"));
            ConfigurableApplicationContext ctx = app.run(args);
            System.exit(SpringApplication.exit(ctx));
        }
        app.run(args);
    }
}
