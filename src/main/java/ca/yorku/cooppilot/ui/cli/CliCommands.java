package ca.yorku.cooppilot.ui.cli;

import ca.yorku.cooppilot.agent.AgentEventListener;
import ca.yorku.cooppilot.agent.AgentResult;
import ca.yorku.cooppilot.agent.QuestionHandler;
import ca.yorku.cooppilot.agent.tools.ToolCall;
import ca.yorku.cooppilot.agent.tools.ToolResult;
import ca.yorku.cooppilot.app.CoopPilotFacade;
import ca.yorku.cooppilot.domain.CoverLetter;
import ca.yorku.cooppilot.domain.EntryKind;
import ca.yorku.cooppilot.domain.EntryUse;
import ca.yorku.cooppilot.domain.FitReport;
import ca.yorku.cooppilot.domain.Keyword;
import ca.yorku.cooppilot.domain.KeywordReport;
import ca.yorku.cooppilot.domain.Posting;
import ca.yorku.cooppilot.domain.Resume;
import ca.yorku.cooppilot.domain.VaultEntry;
import ca.yorku.cooppilot.tracking.Application;
import ca.yorku.cooppilot.tracking.DashboardStats;
import ca.yorku.cooppilot.tracking.Status;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The CLI. Every command calls only the {@link CoopPilotFacade}. With --json, output is one JSON value
 * per command (used by scripts and the Stage 3 KUMA tests); otherwise it is readable text.
 */
@Command(name = "cooppilot", mixinStandardHelpOptions = true, version = "CoopPilot 0.2",
        description = "Tailor co-op resumes and cover letters with an AI agent, and track applications.",
        subcommands = {
                CliCommands.VaultCmd.class, CliCommands.PostingCmd.class, CliCommands.FitCmd.class,
                CliCommands.ScanCmd.class, CliCommands.TailorCmd.class, CliCommands.ReviseCmd.class,
                CliCommands.EditBulletCmd.class, CliCommands.CondenseCmd.class, CliCommands.UndoCmd.class, CliCommands.RedoCmd.class,
                CliCommands.LetterCmd.class, CliCommands.LibraryCmd.class, CliCommands.ReuseCmd.class,
                CliCommands.ExportCmd.class, CliCommands.AppCmd.class, CliCommands.DashboardCmd.class,
                CliCommands.GoalsCmd.class, CliCommands.ShellCmd.class})
public class CliCommands implements Runnable {

    @Option(names = "--json", scope = CommandLine.ScopeType.INHERIT, description = "print machine-readable JSON")
    boolean json;

    final CoopPilotFacade facade;
    final BufferedReader in;
    final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    PrintWriter out;

    public CliCommands(CoopPilotFacade facade, BufferedReader in) {
        this.facade = facade;
        this.in = in;
    }

    @Override
    public void run() {
        out.println("CoopPilot CLI. Try: cooppilot --cli --help, or --cli shell for an interactive session.");
    }

    /** Builds a CommandLine bound to this root, writing to {@code out}. */
    public CommandLine commandLine(PrintWriter out) {
        this.out = out;
        CommandLine cl = new CommandLine(this);
        cl.setOut(out).setErr(out);
        cl.setExecutionExceptionHandler((ex, c, parsed) -> {
            out.println(json ? "{\"error\":" + quote(ex.getMessage()) + "}" : "Error: " + ex.getMessage());
            out.flush();
            return 1;
        });
        return cl;
    }

    void print(Object jsonValue, String text) {
        if (json) {
            try {
                out.println(mapper.writeValueAsString(jsonValue));
            } catch (IOException e) {
                out.println("{\"error\":" + quote(e.getMessage()) + "}");
            }
        } else {
            out.println(text);
        }
        out.flush();
    }

    static String quote(String s) {
        return "\"" + String.valueOf(s).replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    static Map<String, Object> resumeView(Resume r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("name", r.getName());
        m.put("family", r.getFamily());
        m.put("kind", r.getKind());
        m.put("blocks", r.getBlocks().stream().map(b -> Map.of("id", b.getId(), "section", b.getSection(),
                "title", b.getTitle(), "bullets", b.getBullets())).toList());
        m.put("hasPlaceholders", r.hasPlaceholders());
        return m;
    }

    static Map<String, Object> appView(Application a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("company", a.getCompany());
        m.put("role", a.getRole());
        m.put("status", a.getStatus());
        m.put("appliedOn", a.getAppliedOn());
        m.put("followUpOn", a.getFollowUpOn());
        m.put("resume", a.getResumeName());
        m.put("allowedNext", a.getState().allowedNext());
        m.put("history", a.getHistory());
        return m;
    }

    static String terms(List<Keyword> ks) {
        return ks.isEmpty() ? "-" : ks.stream().map(Keyword::term).collect(Collectors.joining(", "));
    }

    // ---------------------------------------------------------------- F01 vault

    @Command(name = "vault", description = "Experience vault (F01).",
            subcommands = {VaultCmd.Import.class, VaultCmd.ListCmd.class, VaultCmd.Add.class})
    static class VaultCmd {
        @ParentCommand CliCommands root;

        @Command(name = "import", description = "Import a Jake's-template LaTeX resume into the library and vault.")
        static class Import implements Callable<Integer> {
            @ParentCommand VaultCmd parent;
            @Parameters(paramLabel = "FILE") Path file;
            @Option(names = "--name", defaultValue = "CV_General") String name;

            public Integer call() throws IOException {
                CliCommands r = parent.root;
                var outcome = r.facade.importResume(Files.readString(file), name);
                Map<String, Object> j = new LinkedHashMap<>();
                j.put("resume", outcome.resume() == null ? null : resumeView(outcome.resume()));
                j.put("newVaultEntries", outcome.newVaultEntries());
                j.put("warnings", outcome.parse().warnings());
                String text = outcome.resume() == null ? "Nothing imported." :
                        "Imported " + outcome.resume().getBlocks().size() + " blocks as " + outcome.resume().getName()
                                + " (id " + outcome.resume().getId() + "); " + outcome.newVaultEntries() + " new vault entries.";
                if (!outcome.parse().warnings().isEmpty()) text += "\nWarnings:\n- " + String.join("\n- ", outcome.parse().warnings());
                r.print(j, text);
                return outcome.resume() == null ? 1 : 0;
            }
        }

        @Command(name = "list", description = "List or search vault entries.")
        static class ListCmd implements Runnable {
            @ParentCommand VaultCmd parent;
            @Option(names = {"-q", "--query"}) String query;

            public void run() {
                List<VaultEntry> es = parent.root.facade.vaultEntries(query);
                parent.root.print(es, es.isEmpty() ? "(no entries)" : es.stream()
                        .map(e -> String.format("%-8s %s%s", e.getKind(), e.getTitle(),
                                e.getUse() == EntryUse.ALL ? "" : " [" + e.getUse() + "]"))
                        .collect(Collectors.joining("\n")));
            }
        }

        @Command(name = "add", description = "Add a vault entry by hand.")
        static class Add implements Runnable {
            @ParentCommand VaultCmd parent;
            @Option(names = "--title", required = true) String title;
            @Option(names = "--kind", required = true) EntryKind kind;
            @Option(names = "--use", defaultValue = "ALL") EntryUse use;
            @Option(names = "--tags", split = ",") List<String> tags;
            @Option(names = "--body") String body;

            public void run() {
                VaultEntry e = parent.root.facade.addVaultEntry(new VaultEntry(null, title, kind, use, "",
                        tags == null ? null : new LinkedHashSet<>(tags), body));
                parent.root.print(e, "Saved \"" + e.getTitle() + "\" (" + e.getKind() + ").");
            }
        }
    }

    // ---------------------------------------------------------------- F02 postings

    @Command(name = "posting", description = "Job postings (F02).",
            subcommands = {PostingCmd.Add.class, PostingCmd.Analyze.class, PostingCmd.ListCmd.class})
    static class PostingCmd {
        @ParentCommand CliCommands root;

        @Command(name = "add", description = "Import a posting from a text file.")
        static class Add implements Callable<Integer> {
            @ParentCommand PostingCmd parent;
            @Parameters(paramLabel = "FILE") Path file;
            @Option(names = "--company") String company;
            @Option(names = "--role") String role;
            @Option(names = "--analyze", description = "analyze right away") boolean analyze;

            public Integer call() throws IOException {
                CliCommands r = parent.root;
                Posting p = r.facade.importPosting(Files.readString(file), company, role);
                if (analyze) p = r.facade.analyzePosting(p.getId());
                r.print(Map.of("id", p.getId(), "company", p.getCompany(), "role", p.getRole(), "analyzed", p.isAnalyzed()),
                        "Posting " + p.getId() + (p.isAnalyzed() ? " analyzed: " + p.getCompany() + " — " + p.getRole() : " saved."));
                return 0;
            }
        }

        @Command(name = "analyze", description = "Analyze a posting with the LLM.")
        static class Analyze implements Runnable {
            @ParentCommand PostingCmd parent;
            @Parameters(paramLabel = "POSTING_ID") String id;

            public void run() {
                Posting p = parent.root.facade.analyzePosting(id);
                var a = p.getAnalysis().orElseThrow();
                parent.root.print(Map.of("id", p.getId(), "company", p.getCompany(), "role", p.getRole(), "term", p.getTerm(),
                                "analysis", a),
                        p.getCompany() + " — " + p.getRole() + (p.getTerm().isBlank() ? "" : " (" + p.getTerm() + ")")
                                + "\nMust-haves: " + String.join("; ", a.mustHaves())
                                + "\nKeywords: " + terms(a.keywords())
                                + "\nDocuments: " + String.join(", ", a.documentsRequired())
                                + (a.softSkillFocus() ? "\nSoft-skill focus" : ""));
            }
        }

        @Command(name = "list", description = "List postings.")
        static class ListCmd implements Runnable {
            @ParentCommand PostingCmd parent;

            public void run() {
                List<Posting> ps = parent.root.facade.postings();
                parent.root.print(ps.stream().map(p -> Map.of("id", p.getId(), "company", p.getCompany(),
                                "role", p.getRole(), "analyzed", p.isAnalyzed())).toList(),
                        ps.isEmpty() ? "(no postings)" : ps.stream().map(p -> p.getId() + "  " + p.getCompany() + " — "
                                + p.getRole() + (p.isAnalyzed() ? "" : "  [not analyzed]")).collect(Collectors.joining("\n")));
            }
        }
    }

    // ---------------------------------------------------------------- F03 / F04

    @Command(name = "fit", description = "Fit score, gaps and recommended strategy for a posting (F03).")
    static class FitCmd implements Runnable {
        @ParentCommand CliCommands root;
        @Parameters(paramLabel = "POSTING_ID") String postingId;

        public void run() {
            FitReport f = root.facade.analyzeFit(postingId);
            Map<String, Object> j = new LinkedHashMap<>();
            j.put("score", f.score());
            j.put("matched", f.matched());
            j.put("gaps", f.gaps());
            j.put("strategy", f.strategy());
            j.put("closestResume", f.closestResume().map(m -> Map.of("id", m.resume().getId(), "name", m.resume().getName(),
                    "matchPercent", m.matchPercent())).orElse(null));
            root.print(j, "Fit " + f.score() + "/100 — strategy " + f.strategy()
                    + "\nMatched: " + (f.matched().isEmpty() ? "-" : String.join(", ", f.matched()))
                    + "\nGaps: " + (f.gaps().isEmpty() ? "-" : String.join(", ", f.gaps()))
                    + f.closestResume().map(m -> "\nClosest saved resume: " + m.resume().getName() + " (" + m.matchPercent()
                    + "%, id " + m.resume().getId() + ")").orElse("\nNo reusable saved resume."));
        }
    }

    @Command(name = "scan", description = "ATS keyword coverage of a resume against a posting (F04).")
    static class ScanCmd implements Runnable {
        @ParentCommand CliCommands root;
        @Parameters(index = "0", paramLabel = "RESUME_ID") String resumeId;
        @Parameters(index = "1", paramLabel = "POSTING_ID") String postingId;

        public void run() {
            KeywordReport k = root.facade.scanKeywords(resumeId, postingId);
            root.print(Map.of("coverage", Math.round(k.coverage() * 100), "backed", k.backed(), "thin", k.thin(),
                            "knownNotWorded", k.knownNotWorded(), "missing", k.missing()),
                    "Coverage " + Math.round(k.coverage() * 100) + "%"
                            + "\n  on resume, backed:      " + terms(k.backed())
                            + "\n  on resume, skills only: " + terms(k.thin())
                            + "\n  you have it, not worded: " + terms(k.knownNotWorded())
                            + "\n  missing:                " + terms(k.missing()));
        }
    }

    // ---------------------------------------------------------------- F05 / F06 agent

    @Command(name = "tailor", description = "Run the tailoring agent for an analyzed posting (F05, F06).")
    static class TailorCmd implements Runnable {
        @ParentCommand CliCommands root;
        @Parameters(paramLabel = "POSTING_ID") String postingId;
        @Option(names = "--no-questions", description = "never ask; the agent leaves unknown details out") boolean noQuestions;

        public void run() {
            List<Map<String, Object>> steps = new ArrayList<>();
            QuestionHandler q = noQuestions || root.json ? QuestionHandler.NONE : question -> {
                root.out.print("? " + question + "\n> ");
                root.out.flush();
                try {
                    String line = root.in.readLine();
                    return Optional.ofNullable(line).map(String::trim).filter(s -> !s.isEmpty());
                } catch (IOException e) {
                    return Optional.empty();
                }
            };
            AgentEventListener printer = new AgentEventListener() {
                public void onThinking(String text) {
                    if (!root.json) { root.out.println("  " + text.replace("\n", "\n  ")); root.out.flush(); }
                }
                public void onToolCalled(ToolCall c) {
                    steps.add(new LinkedHashMap<>(Map.of("tool", c.name(), "args", String.valueOf(c.args()))));
                    if (!root.json) { root.out.println("→ " + c.name() + " " + c.args()); root.out.flush(); }
                }
                public void onToolResult(ToolResult r) {
                    if (!steps.isEmpty()) steps.get(steps.size() - 1).put("ok", r.ok());
                    if (!root.json) {
                        String first = r.forModel().lines().findFirst().orElse("");
                        root.out.println("  " + (r.ok() ? "✓ " : "✗ ") + first);
                        root.out.flush();
                    }
                }
                public void onFinished(AgentResult r) {}
            };
            AgentResult r = root.facade.tailorResume(postingId, q, printer);
            Map<String, Object> j = new LinkedHashMap<>();
            j.put("status", r.status());
            j.put("steps", r.steps());
            j.put("toolCalls", steps);
            j.put("summary", r.summary());
            j.put("resume", r.resumeOpt().map(CliCommands::resumeView).orElse(null));
            j.put("tokens", Map.of("input", r.inputTokens(), "output", r.outputTokens()));
            root.print(j, "\n" + r.status() + " after " + r.steps() + " steps"
                    + r.resumeOpt().map(x -> ": " + x.getName() + " (id " + x.getId() + ")").orElse("")
                    + "\n" + r.summary() + "\nTokens: " + r.inputTokens() + " in / " + r.outputTokens() + " out");
        }
    }

    // ---------------------------------------------------------------- F08 revision

    @Command(name = "revise", description = "Revise a resume in plain English (F08).")
    static class ReviseCmd implements Runnable {
        @ParentCommand CliCommands root;
        @Parameters(index = "0", paramLabel = "RESUME_ID") String resumeId;
        @Parameters(index = "1..*", paramLabel = "INSTRUCTION") List<String> words;

        public void run() {
            Resume r = root.facade.reviseResume(resumeId, String.join(" ", words));
            List<String> log = root.facade.revisionLog(resumeId);
            root.print(Map.of("resume", resumeView(r), "log", log), log.get(log.size() - 1));
        }
    }

    @Command(name = "edit-bullet", description = "Replace one bullet by hand (F08).")
    static class EditBulletCmd implements Runnable {
        @ParentCommand CliCommands root;
        @Parameters(index = "0") String resumeId;
        @Parameters(index = "1") String blockId;
        @Parameters(index = "2") int index;
        @Parameters(index = "3..*") List<String> words;

        public void run() {
            Resume r = root.facade.replaceBullet(resumeId, blockId, index, String.join(" ", words));
            root.print(resumeView(r), "Bullet replaced.");
        }
    }

    @Command(name = "condense", description = "Fit a resume to one page for a posting (F07); undoable in the shell.")
    static class CondenseCmd implements Runnable {
        @ParentCommand CliCommands root;
        @Parameters(index = "0", paramLabel = "RESUME_ID") String resumeId;
        @Parameters(index = "1", paramLabel = "POSTING_ID") String postingId;
        @Option(names = "--mode", defaultValue = "RULES", description = "RULES (free) or SMART (1 LLM request)")
        ca.yorku.cooppilot.service.CondenseStrategy.Mode mode;

        public void run() {
            long before = Math.round(root.facade.pageEstimate(resumeId) * 100);
            Resume r = root.facade.condenseResume(resumeId, postingId, mode);
            long after = Math.round(root.facade.pageEstimate(resumeId) * 100);
            List<String> log = root.facade.revisionLog(resumeId);
            root.print(Map.of("resume", resumeView(r), "pageBefore", before, "pageAfter", after, "report", log.get(log.size() - 1)),
                    log.get(log.size() - 1));
        }
    }

    @Command(name = "undo", description = "Undo the last edit of a resume (F08; same session).")
    static class UndoCmd implements Runnable {
        @ParentCommand CliCommands root;
        @Parameters(paramLabel = "RESUME_ID") String resumeId;

        public void run() {
            String what = root.facade.undo(resumeId);
            root.print(Map.of("undone", what), "Undid: " + what);
        }
    }

    @Command(name = "redo", description = "Redo the last undone edit (F08; same session).")
    static class RedoCmd implements Runnable {
        @ParentCommand CliCommands root;
        @Parameters(paramLabel = "RESUME_ID") String resumeId;

        public void run() {
            String what = root.facade.redo(resumeId);
            root.print(Map.of("redone", what), "Redid: " + what);
        }
    }

    // ---------------------------------------------------------------- F09 letter

    @Command(name = "letter", description = "Draft a cover letter (F09).")
    static class LetterCmd implements Runnable {
        @ParentCommand CliCommands root;
        @Parameters(index = "0", paramLabel = "POSTING_ID") String postingId;
        @Parameters(index = "1", paramLabel = "RESUME_ID") String resumeId;

        public void run() {
            CoverLetter c = root.facade.writeCoverLetter(postingId, resumeId);
            root.print(c, c.name() + " (" + c.wordCount() + " words)\n\n" + c.body());
        }
    }

    // ---------------------------------------------------------------- F10 library

    @Command(name = "library", description = "Saved resumes grouped by role family (F10).")
    static class LibraryCmd implements Runnable {
        @ParentCommand CliCommands root;

        public void run() {
            Map<String, List<Resume>> groups = root.facade.resumeLibrary();
            Map<String, Object> j = new LinkedHashMap<>();
            StringBuilder sb = new StringBuilder();
            groups.forEach((fam, rs) -> {
                j.put(fam, rs.stream().map(r -> Map.of("id", r.getId(), "name", r.getName(), "kind", r.getKind())).toList());
                sb.append(fam).append(":\n");
                rs.forEach(r -> sb.append("  ").append(r.getName()).append("  ").append(r.getKind())
                        .append("  ").append(Math.round(root.facade.pageEstimate(r.getId()) * 100)).append("% page")
                        .append("  id ").append(r.getId()).append(r.hasPlaceholders() ? "  [placeholders]" : "").append('\n'));
            });
            root.print(j, sb.length() == 0 ? "(library is empty)" : sb.toString().stripTrailing());
        }
    }

    @Command(name = "reuse", description = "Use a saved resume as-is for a posting (F10).")
    static class ReuseCmd implements Runnable {
        @ParentCommand CliCommands root;
        @Parameters(index = "0", paramLabel = "POSTING_ID") String postingId;
        @Parameters(index = "1", paramLabel = "RESUME_ID") String resumeId;

        public void run() {
            Resume r = root.facade.reuseResume(postingId, resumeId);
            root.print(resumeView(r), "Saved as " + r.getName() + " (id " + r.getId() + ").");
        }
    }

    @Command(name = "export", description = "Write a resume's .tex (or a cover letter with --letter) to the export folder (F10).")
    static class ExportCmd implements Runnable {
        @ParentCommand CliCommands root;
        @Parameters(paramLabel = "RESUME_ID_OR_LETTER_NAME") String id;
        @Option(names = "--letter") boolean letter;

        public void run() {
            Path p = letter ? root.facade.exportCoverLetter(id) : root.facade.exportResume(id);
            root.print(Map.of("path", p.toAbsolutePath().toString()), "Wrote " + p.toAbsolutePath());
        }
    }

    // ---------------------------------------------------------------- F11 / F12 tracking

    @Command(name = "app", description = "Application tracking (F11).",
            subcommands = {AppCmd.Add.class, AppCmd.StatusCmd.class, AppCmd.ListCmd.class})
    static class AppCmd {
        @ParentCommand CliCommands root;

        @Command(name = "add", description = "Log an application (starts as TO_APPLY).")
        static class Add implements Runnable {
            @ParentCommand AppCmd parent;
            @Option(names = "--posting") String postingId;
            @Option(names = "--company") String company;
            @Option(names = "--role") String role;
            @Option(names = "--resume", defaultValue = "") String resume;

            public void run() {
                Application a = parent.root.facade.logApplication(postingId, company, role, resume);
                parent.root.print(appView(a), "Logged " + a.getCompany() + " — " + a.getRole() + " (id " + a.getId() + ", " + a.getStatus() + ")");
            }
        }

        @Command(name = "status", description = "Move an application to a new status.")
        static class StatusCmd implements Runnable {
            @ParentCommand AppCmd parent;
            @Parameters(index = "0", paramLabel = "APP_ID") String id;
            @Parameters(index = "1", paramLabel = "STATUS", description = "${COMPLETION-CANDIDATES}") Status status;
            @Option(names = "--note", defaultValue = "") String note;

            public void run() {
                Application a = parent.root.facade.changeStatus(id, status, note);
                parent.root.print(appView(a), a.getCompany() + " is now " + a.getStatus()
                        + (a.getFollowUpOn() == null ? "" : "; follow up on " + a.getFollowUpOn())
                        + (a.getState().isTerminal() ? " (final)" : "; next: " + a.getState().allowedNext()));
            }
        }

        @Command(name = "list", description = "List applications.")
        static class ListCmd implements Runnable {
            @ParentCommand AppCmd parent;

            public void run() {
                List<Application> apps = parent.root.facade.applications();
                parent.root.print(apps.stream().map(CliCommands::appView).toList(), apps.isEmpty() ? "(no applications)" :
                        apps.stream().map(a -> String.format("%-11s %-22s %-26s %s", a.getStatus(), a.getCompany(), a.getRole(),
                                a.getAppliedOn() == null ? "" : "applied " + a.getAppliedOn())).collect(Collectors.joining("\n")));
            }
        }
    }

    @Command(name = "dashboard", description = "Progress and goals (F12).")
    static class DashboardCmd implements Runnable {
        @ParentCommand CliCommands root;

        public void run() {
            DashboardStats s = root.facade.dashboard();
            Map<String, Object> j = new LinkedHashMap<>();
            j.put("totalApplied", s.totalApplied());
            j.put("responseRatePercent", s.responseRatePercent());
            j.put("interviews", s.interviews());
            j.put("offers", s.offers());
            j.put("followUpsDue", s.followUpsDue());
            j.put("today", Map.of("applied", s.appliedToday(), "goal", s.goals().daily(), "met", s.dailyGoalMet()));
            j.put("thisWeek", Map.of("applied", s.appliedThisWeek(), "goal", s.goals().weekly(), "met", s.weeklyGoalMet()));
            j.put("streakDays", s.streakDays());
            Map<String, Integer> days = new LinkedHashMap<>();
            s.lastSevenDays().forEach((d, n) -> days.put(d.toString(), n));
            j.put("lastSevenDays", days);
            StringBuilder bars = new StringBuilder();
            s.lastSevenDays().forEach((d, n) -> bars.append(String.format("  %s %-3s %s%n", d.getDayOfWeek().toString().substring(0, 3), n, "█".repeat(n))));
            root.print(j, String.format("Applied %d · response rate %d%% · interviews %d · offers %d · follow-ups due %d%n"
                            + "Today %d/%d%s · this week %d/%d%s · streak %d day(s)%n%s",
                    s.totalApplied(), s.responseRatePercent(), s.interviews(), s.offers(), s.followUpsDue(),
                    s.appliedToday(), s.goals().daily(), s.dailyGoalMet() ? " ✓" : "",
                    s.appliedThisWeek(), s.goals().weekly(), s.weeklyGoalMet() ? " ✓" : "", s.streakDays(), bars).stripTrailing());
        }
    }

    @Command(name = "goals", description = "Set daily and weekly application goals (F12).")
    static class GoalsCmd implements Runnable {
        @ParentCommand CliCommands root;
        @Parameters(index = "0", paramLabel = "DAILY") int daily;
        @Parameters(index = "1", paramLabel = "WEEKLY") int weekly;

        public void run() {
            root.facade.setGoals(daily, weekly);
            root.print(Map.of("daily", daily, "weekly", weekly), "Goals: " + daily + "/day, " + weekly + "/week.");
        }
    }

    // ---------------------------------------------------------------- interactive

    @Command(name = "shell", description = "Interactive session (keeps undo/redo history between commands). Type 'exit' to quit.")
    static class ShellCmd implements Callable<Integer> {
        @ParentCommand CliCommands root;
        private static final Pattern TOKEN = Pattern.compile("\"([^\"]*)\"|(\\S+)");

        public Integer call() throws IOException {
            root.out.println("CoopPilot shell — commands as in --help, 'exit' to quit.");
            while (true) {
                root.out.print("cooppilot> ");
                root.out.flush();
                String line = root.in.readLine();
                if (line == null || line.trim().equals("exit") || line.trim().equals("quit")) return 0;
                if (line.isBlank()) continue;
                String[] args = split(line);
                if (args[0].equals("shell")) { root.out.println("already in the shell"); continue; }
                boolean wasJson = root.json;
                root.commandLine(root.out).execute(args);
                root.json = wasJson;
            }
        }

        static String[] split(String line) {
            List<String> out = new ArrayList<>();
            Matcher m = TOKEN.matcher(line);
            while (m.find()) out.add(m.group(1) != null ? m.group(1) : m.group(2));
            return out.toArray(String[]::new);
        }
    }

    /** Arguments without the --cli switch. */
    static String[] withoutCliFlag(String[] args) {
        return Arrays.stream(args).filter(a -> !a.equals("--cli")).toArray(String[]::new);
    }
}
