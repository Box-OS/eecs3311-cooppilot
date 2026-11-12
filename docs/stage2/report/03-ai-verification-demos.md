# 5. AI tools and collaboration log

## 5.1 Tools and models

| Tool / platform | LLM / model | Used for (features, classes, tasks) | Total token cost | Money cost |
|---|---|---|---|---|
| **Claude** (claude.ai app, agentic workspace session with shell, file and GitHub access) | Claude Opus 5.5 (`claude-opus-5-5`) | Stage 2 planning; all implementation commits (skeleton, domain, persistence, LLM layer, F01–F12, CLI, GUI); the 86 automated tests; UMLet class-diagram generation; drafting this report, the change log and the demo script | ≈ 38.6 M tokens processed in the session up to Nov 12, 2026: 36.7 M cache reads, 1.6 M cache writes, 0.27 M output (≈ 8.3 M input-token-equivalents when cache reads are weighted 0.1×, cache writes 2× and output 5×) | No per-token charge: covered by the author's Claude subscription. {{TODO: plan name and monthly price}} |
| **Anthropic API** (runtime LLM inside CoopPilot, via `ClaudeAdapter`) | Claude Haiku 4.5 (`claude-haiku-4-5`) | Posting analysis (F02), the tailoring agent (F05–F07), revisions (F08), cover letters (F09), smart condense (F07) during manual testing and the demo recordings | {{TODO: total tokens from console.anthropic.com → Usage}} | {{TODO: US$ from the Console}} |

Token counts for Claude come from the session transcript's usage records. API keys are kept in the git-ignored `application-local.properties` and appear in no commit, recording or report.

## 5.2 How the collaboration worked

The author set the direction and the rules; Claude wrote most of the code and documents; the author ran, checked and demonstrated the result. CoopPilot is a Java redesign of the author's own earlier browser-based prototype (a personal job-application tracker), so most product rules come from his real use: one-page resumes, reverse-chronological experience, no bold words in Education/Skills, the `CV_Company_Role` / `CL_Company_Role` naming, never inventing facts or numbers, and asking the student instead of guessing. The Stage 1 design (written with AI assistance in an earlier session) was the specification given to the AI for each step.

## 5.3 Collaboration log (representative interactions)

| # | Task | Input (from the author) | AI contribution | Human contribution | Outcome |
|---|---|---|---|---|---|
| 1 | Stage 2 plan (Oct 21) | "How do I keep developing my tracker and still deliver Stage 2 with minimal extra work?" plus the Stage 2 handout | Mapped 11 of the 12 Stage 1 features to the prototype's existing logic; proposed porting the behaviour (not the code) to Java, keeping the Stage 1 structure, logging deviations as they happen, and a design freeze before the videos | Chose the approach; decided the Stage 1 design may change and must be documented; supplied the handout | Plan adopted; weekly scope fixed |
| 2 | Week-1 skeleton and F01 (Oct 26) | "Make the 5 commits"; stack fixed in Stage 1 (Java 21, Spring Boot + Thymeleaf, no JavaFX, SQLite, picocli) | `pom.xml`, git-ignored key config with a placeholder example, domain model, `Repository<T>`/`SqliteRepository<T>`, `LLMClient`/`ClaudeAdapter`/mock, `LatexResumeParser`, `VaultService`, vault page, 28 tests | Set the constraints; required the key never be committed; asked that commits carry his name only (AI use is disclosed here instead) | 6 commits in period 1, all tests passing |
| 3 | Adapter design decision | — | Proposed calling the Messages API over `HttpClient` instead of the SDK and documented it as C01 | Accepted the deviation | Translation fully unit-tested offline (`ClaudeAdapterTest`) |
| 4 | Agent and F02–F07 (Nov 2) | "Queue in the next 5 commits"; rules from his prototype (one page, chronology, no bold, naming, no invented facts) | `LlmTask` template, posting analysis, fit/keyword scan, agent loop with `ToolRegistry` validation, 8 tools, `LatexAssembler`, condense strategies, `ask_user` with vault learning; end-to-end agent test with scripted LLM decisions | Supplied the rules the code had to enforce; decided the agent must ask instead of guessing | 6 commits in period 2; tests caught a real naming bug ("Royal Bank of Canada" → `RBOC` instead of `RBC`), fixed before commit |
| 5 | F08–F12, CLI and GUI (Nov 9) | "Do the 6 commits" | State-pattern tracker, observing dashboard, Command-based revision with undo/redo, cover letters, library/export, picocli CLI with `--json` and `shell`, web pages with SSE progress; MockMvc walkthrough; screenshots of the running app checked | Chose the scope and order; reviewed the result {{TODO: what you checked or changed}} | 6 commits in period 3; defects found and fixed (Section 6.3) |
| 6 | Design-vs-code review (Nov 12) | "Do the reports" | Compared the code to every Stage 1 feature spec; found that F07 was only reachable inside the agent and that LLM retry with backoff was missing; implemented both (`CondenseCommand`, page meter, CLI `condense`, `completeWithRetry`) with tests; drafted this report and the Stage 2 diagrams | Approved the fixes; reviewed this report {{TODO: your edits}} | Commit `83bc4ee`; report draft |
| 7 | Real-LLM test run | {{TODO: date}} | {{TODO: fixes made after the run, if any}} | Created the API key, ran the full flow against Claude Haiku 4.5 {{TODO: what worked / what broke}} | {{TODO}} |
| 8 | Demonstration videos | Demo script (`docs/stage2/demo-script.md`) | Wrote the click-by-click script per feature | Recorded and narrated the videos; exported the UMLet PNGs | Section 7 |

**Validation responsibility.** Every AI-generated change was built and tested before it was committed (`mvn test`), and the running application was exercised through the GUI and CLI. {{TODO: one sentence on how you reviewed the code, e.g. read the agent loop, tools and state classes and can explain them.}}

# 6. Development and verification summary

## 6.1 Weekly commit summary

Each reporting period (Toronto time) has at least five meaningful code commits. All commits are on `main` in the public repository.

{{COMMITS}}

## 6.2 Verification performed

* **Automated tests: 86, all passing** (`mvn test`; JUnit 5 + Spring Boot Test + MockMvc; SQLite in memory; the mock LLM replaces Claude):

| Area | Test classes (tests) | What they check |
|---|---|---|
| Domain and parsing | `DomainModelTest` (5), `LatexResumeParserTest` (7), `ImportResumeTest` (4), `SqliteRepositoryTest` (3) | keyword word boundaries, chronology, placeholders; both `\resumeSubheading` argument orders, tags, skills, escaped `%`, warnings; idempotent import; round trips |
| LLM layer | `ClaudeAdapterTest` (5), `PostingAnalysisTaskTest` (4), `CoverLetterTest` (2), `PostingFlowTest` (2) | tool-use JSON translation, merged tool results, retries with feedback, give-up, letters-only vs. context-only facts |
| Fit, scan, library | `FitAndKeywordTest` (5), `LibraryExportTest` (5), `AssemblerAndNamingTest` (4) | scores and gaps, placeholder resumes never reused, backed/thin/known/missing, reuse naming, path-escape protection, no bold in Education/Skills |
| Agent | `AgentControllerTest` (4), `TailorAgentTest` (1), `AskUserToolTest` (3), `CondenseAndRetryTest` (3) | full tool loop, invalid/unknown tool calls, step limit, LLM failure and retry; end-to-end tailoring with invented numbers rejected; ask once and remember |
| Edits, condense | `RevisionTest` (3), `CondenseStrategyTest` (4) | undo/redo without a second LLM call, redo cleared by new edits; rule order, jobs kept, smart plan validated and retried |
| Tracking | `ApplicationStateTest` (6), `TrackerFlowTest` (3) | legal/illegal transitions, follow-ups, revival, dashboard math, streaks, persistence |
| Interfaces | `CliCommandsTest` (4), `WebPagesTest` (3), `VaultControllerTest` (3), `AgentSessionManagerTest` (2), `CoopPilotApplicationTest` (1) | every CLI feature, JSON output, shell undo; click-through of every page; SSE question answered from the browser; friendly errors |

* **Packaged application runs:** the built jar was run in CLI mode (import, library `--json`, log application, dashboard) and in GUI mode with sample data; page screenshots were checked.
* **Real LLM:** {{TODO: result of the manual run with Claude Haiku 4.5.}}

## 6.3 Important defects found and corrected

| Defect | Found by | Fix |
|---|---|---|
| "Royal Bank of Canada" named `RBOC` instead of `RBC` | `AssemblerAndNamingTest` | Skip "of/the/and" in acronyms (`ExportService.companyPart`) |
| Spring could not create `AgentSessionManager` (two constructors) | `WebPagesTest` context load | Marked the injection constructor `@Autowired` |
| Tracker "Move to" menu listed statuses in random order | Screenshot review of the running app | `allowedNext()` returns lifecycle order (`EnumSet`) |
| F07 only usable inside the agent; no LLM retry | Design-vs-code review (log #6) | `CondenseCommand`, page meter, CLI `condense`; `completeWithRetry` |
| {{TODO: anything found in the real-LLM run}} | | |

## 6.4 Remaining limitations

See Section 1: real-model quality checked manually only (KUMA evaluation in Stage 3), in-memory undo history, heuristic page estimate, single user, and the unimplemented Stage 1 items in C07, C11–C13.

# 7. Demonstration index

Each video shows the feature ID and name, how it is invoked and its input, the running application and its output, where AI is involved, and an error or alternative case. Together they show both the GUI and the CLI. Links are unlisted YouTube videos, viewable without an access request.

| Feature | Video | Interface shown | Error / alternative shown |
|---|---|---|---|
| F01 Experience vault and resume import | V01 {{TODO: link}} | GUI + CLI `vault import` | empty upload; re-import adds nothing |
| F02 Posting import and analysis | V02 {{TODO: link}} | GUI | analysis without a key / retry |
| F03 Fit analysis and strategy | V03 {{TODO: link}} | GUI + CLI `fit` | resume with placeholders never suggested |
| F04 ATS keyword scan | V04 {{TODO: link}} | GUI chips + CLI `scan` | switching the scanned resume |
| F05 Agentic tailoring | V05 {{TODO: link}} | GUI live steps + CLI `tailor` | a rejected tool call / invented number |
| F06 Clarifying questions and vault learning | V06 {{TODO: link}} | GUI question box | skipping a question; the answer reused later |
| F07 One-page fitting | V07 {{TODO: link}} | GUI meter + Quick/Smart condense, CLI `condense` | undo the condense |
| F08 Revision with undo/redo | V08 {{TODO: link}} | GUI + CLI `shell` (revise, undo, redo) | undo with nothing left |
| F09 Cover letter | V09 {{TODO: link}} | GUI | (validation retries visible in the log) |
| F10 Library, reuse and export | V10 {{TODO: link}} | GUI "Use as-is" + download, CLI `export` | reuse refused for a placeholder resume |
| F11 Application tracking | V11 {{TODO: link}} | GUI tracker + CLI `app status` | illegal move (e.g. To apply → Offer) refused |
| F12 Dashboard and goals | V12 {{TODO: link}} | GUI + CLI `dashboard` | goals changed; "Goal met" |
