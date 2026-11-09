package ca.yorku.cooppilot.ui.cli;

import ca.yorku.cooppilot.app.CoopPilotFacade;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Runs the picocli CLI when the app is started with --cli (the web server is not started then). */
@Component
public class CliRunner implements CommandLineRunner, ExitCodeGenerator {

    private final CoopPilotFacade facade;
    private int exitCode;

    public CliRunner(CoopPilotFacade facade) {
        this.facade = facade;
    }

    public static boolean isCliMode(String[] args) {
        return Arrays.asList(args).contains("--cli");
    }

    @Override
    public void run(String... args) {
        if (!isCliMode(args)) return;
        PrintWriter out = new PrintWriter(System.out, true, StandardCharsets.UTF_8);
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        exitCode = new CliCommands(facade, in).commandLine(out).execute(CliCommands.withoutCliFlag(args));
        out.flush();
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
