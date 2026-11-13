# 5. AI tools and collaboration log

## 5.1 Tools and models

| Tool / platform | LLM / model | Used for (features, classes, tasks) | Total token cost | Money cost |
|---|---|---|---|---|
| **Claude** (claude.ai app, agentic workspace session with shell, file and GitHub access) | Claude Opus 5.5 (`claude-opus-5-5`) | Stage 2 planning; all implementation commits (skeleton, domain, persistence, LLM layer, F01–F12, CLI, GUI); the 86 automated tests; UMLet class-diagram generation; drafting this report, the change log and the demo script | ≈ 38.6 M tokens processed in the session up to Nov 12, 2026: 36.7 M cache reads, 1.6 M cache writes, 0.27 M output (≈ 8.3 M input-token-equivalents when cache reads are weighted 0.1×, cache writes 2× and output 5×) | No per-token charge: covered by the author's **Claude Pro** subscription, **CA$31.64 per month**. All Stage 2 work (Oct 21 – Nov 15) falls within about one month of the subscription. |
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
| 6 | Design-vs-code review and report (Nov 12) | "Do the reports" | Compared the code to every Stage 1 feature spec; found that F07 was only reachable inside the agent and that LLM retry with backoff was missing; implemented both (`CondenseCommand`, page meter, CLI `condense`, `completeWithRetry`) with tests; drafted this report and the Stage 2 diagrams | Approved the fixes; reviewed this report {{TODO: your edits}} | Commit `83bc4ee`; report draft |
| 7 | First runs on real hardware and a real LLM (Nov 12) | Screenshots from the author's Windows PC: tests passing, then the agent page showing **FAILED · 5 steps** with the model asking "Would you like me to…?" | Diagnosed the stop (the model ended its turn to chat instead of saving); added autonomy rules and bounded nudges, fixed an "Open draft" link to an unsaved draft; added the Maven Wrapper and Windows setup steps | Set up Java, Git and the API key on Windows; ran the tests and the app; ran the agent against Claude Haiku 4.5 and reported the failure with a screenshot; re-ran it after the fix | Second run **COMPLETED** and saved `CV_NorthwindLabs_SoftwareDeveloper` (63% of a page); commit `1251b0c` |
| 8 | Readable resume view (Nov 12) | "Why isn't there a readable LaTeX viewer, like the PDF?" | Built `ResumePreview` (Jake's-template-like HTML, text escaped against injection) | Noticed the missing preview that Stage 1 promised | Preview on the resume page; commit `aa00500` |
| 9 | Real data and calibration (Nov 12) | "We can use my ResumeBase data so there is a pre-existing vault" | Built the JSON backup import; converted the author's tracker data (65 vault entries, 50 resumes); found that every real one-page resume estimated at 102–113% and recalibrated the page estimate (now 83–98%) | Proposed using his own data; supplied it through his earlier tracker | Commits `17ecbf1`, `4ad069e` |
| 10 | Remaining Stage 1 gaps (Nov 12) | "What else is left to be implemented?", then "go ahead" | Audited every Stage 1 feature spec and use case; implemented smart-condense fallback, partial drafts, manual bullet edits, resume-page keyword chips, vault edit/delete/tag filter, editable cover letters with kept failing drafts, "What changed" diff, log-from-resume and follow-ups list, deadlines and editable/manual analysis, goal rules; found and fixed a stale-copy bug in the editor | Asked for the audit, chose to close the gaps before recording | Commits `25bf36e`…`724ebae`; 108 tests |
| 11 | Demonstration videos | Demo script (`docs/stage2/demo-script.md`) | Wrote the click-by-click script per feature | Recorded and narrated the videos; exported the UMLet PNGs (Nov 12) | Section 7 |

**Validation responsibility.** Every AI-generated change was built and tested before it was committed (`mvn test`), and the running application was exercised through the GUI and CLI. {{TODO: one sentence on how you reviewed the code, e.g. read the agent loop, tools and state classes and can explain them.}}

# 6. Development and verification summary

## 6.1 Weekly commit summary

Each reporting period (Toronto time) has at least five meaningful code commits. All commits are on `main` in the public repository.

{{COMMITS}}

## 6.2 Verification performed

* **Automated tests: 108, all passing** (`mvn test`; JUnit 5 + Spring Boot Test + MockMvc; SQLite in memory; the mock LLM replaces Claude):

| Area | Test classes (tests) | What they check |
|---|---|---|
| Domain and parsing | `DomainModelTest` (5), `LatexResumeParserTest` (7), `ImportResumeTest` (4), `SqliteRepositoryTest` (3), `BackupImporterTest` (3) | keyword word boundaries, chronology, placeholders; both `\resumeSubheading` argument orders, tags, skills, escaped `%`, warnings; idempotent import; round trips; backup kinds/uses, resumes without duplicate vault entries |
| LLM layer | `ClaudeAdapterTest` (5), `PostingAnalysisTaskTest` (4), `CoverLetterTest` (4), `PostingFlowTest` (2), `AnalysisEditTest` (3) | tool-use JSON translation, merged tool results, retries with feedback, give-up, letters-only vs. context-only facts, failing draft kept with problems, letter edits; deadline extraction, manual analysis |
| Fit, scan, library, preview | `FitAndKeywordTest` (5), `LibraryExportTest` (5), `AssemblerAndNamingTest` (4), `ResumePreviewTest` (2) | scores and gaps, placeholder resumes never reused, backed/thin/known/missing, reuse naming, path-escape protection, no bold in Education/Skills; preview escapes HTML |
| Agent | `AgentControllerTest` (8), `TailorAgentTest` (1), `AskUserToolTest` (3), `CondenseAndRetryTest` (3) | full tool loop, invalid/unknown tool calls, step limit, LLM failure and retry, chatty model nudged to save, partial draft kept; end-to-end tailoring with invented numbers rejected; ask once and remember |
| Edits, condense | `RevisionTest` (5), `ResumeDiffTest` (2), `CondenseStrategyTest` (5) | undo/redo without a second LLM call, redo cleared by new edits, what-changed diff, reload of a replaced resume; rule order, jobs kept, smart plan validated, retried and falling back to rules |
| Tracking | `ApplicationStateTest` (6), `TrackerFlowTest` (4) | legal/illegal transitions, follow-ups, revival, dashboard math, streaks, goal rules, persistence, follow-ups-due list |
| Interfaces | `CliCommandsTest` (4), `WebPagesTest` (3), `VaultControllerTest` (5), `AgentSessionManagerTest` (2), `CoopPilotApplicationTest` (1) | every CLI feature, JSON output, shell undo; click-through of every page incl. bullet edits, analysis edit, log from resume; vault edit/delete/tag filter/duplicate message; SSE question answered from the browser; friendly errors |

* **Packaged application runs:** the built jar was run in CLI mode (import, backup import, library `--json`, log application, dashboard) and in GUI mode with sample data; page screenshots were checked after each GUI change.
* **Author's machine (Windows 11):** `mvnw.cmd test` passed and the GUI ran at http://localhost:8080.
* **Real LLM (Claude Haiku 4.5):** first run FAILED after 5 steps because the model stopped to ask a question in chat (fixed, C20); the second run COMPLETED: find similar resume → keyword scan → three vault searches → compose → keyword scan → estimate page → save, producing a one-page `CV_NorthwindLabs_SoftwareDeveloper`. {{TODO: results of the rest of your test run (condense, revise/undo, cover letter, tracker, dashboard)}}
* **Real data:** the author's 65 vault entries and 50 resumes imported with no parser warnings.

## 6.3 Important defects found and corrected

| Defect | Found by | Fix |
|---|---|---|
| "Royal Bank of Canada" named `RBOC` instead of `RBC` | `AssemblerAndNamingTest` | Skip "of/the/and" in acronyms (`ExportService.companyPart`) |
| Spring could not create `AgentSessionManager` (two constructors) | `WebPagesTest` context load | Marked the injection constructor `@Autowired` |
| Tracker "Move to" menu listed statuses in random order | Screenshot review of the running app | `allowedNext()` returns lifecycle order (`EnumSet`) |
| F07 only usable inside the agent; no LLM retry | Design-vs-code review (log #6) | `CondenseCommand`, page meter, CLI `condense`; `completeWithRetry` |
| Agent stopped to ask "Would you like me to…?" and never saved | First real-LLM run (log #7) | Autonomy rules + bounded nudges (C20) |
| "Open draft" button linked to a draft that was never stored | First real-LLM run | Link only for stored resumes; partial drafts now stored (C22) |
| Every real one-page resume estimated over one page (102–113%) | Importing the author's real resumes (log #9) | Recalibrated to 63 lines per page (C19) |
| Editor kept editing a stale copy after the agent saved a new version under the same name | Code review during the gap work (log #10) | `ResumeEditor` reloads when the stored LaTeX changed |
| Preview showed "Languages:Java" without a space | Screenshot of the new preview | `ResumePreview.skillLine` |

## 6.4 Remaining limitations

See Section 1: real-model behaviour checked manually only (KUMA evaluation in Stage 3), non-numeric embellishments not blocked deterministically, in-memory undo history, heuristic page estimate, single user, and the one unimplemented Stage 1 item (C07, the second LLM adapter).

# 7. Demonstration index

Each video shows the feature ID and name, how it is invoked and its input, the running application and its output, where AI is involved, and an error or alternative case. Together they show both the GUI and the CLI. Links are unlisted YouTube videos, viewable without an access request.

| Feature | Video | Interface shown | Error / alternative shown |
|---|---|---|---|
| F01 Experience vault and resume import | V01 {{TODO: link}} | GUI (import, backup import, edit, tag filter) + CLI `vault import` | empty upload; duplicate skill refused |
| F02 Posting import and analysis | V02 {{TODO: link}} | GUI | editing the analysis / filling it in by hand |
| F03 Fit analysis and strategy | V03 {{TODO: link}} | GUI + CLI `fit` | resume with placeholders never suggested |
| F04 ATS keyword scan | V04 {{TODO: link}} | GUI chips (posting and resume pages) + CLI `scan` | chips change after an edit |
| F05 Agentic tailoring | V05 {{TODO: link}} | GUI live steps + CLI `tailor` | a rejected tool call / invented number |
| F06 Clarifying questions and vault learning | V06 {{TODO: link}} | GUI question box | skipping a question; the answer reused later |
| F07 One-page fitting | V07 {{TODO: link}} | GUI meter + Quick/Smart condense, CLI `condense` | undo the condense |
| F08 Revision with undo/redo | V08 {{TODO: link}} | GUI (revise, ✎ bullet edit, What changed) + CLI `shell` | undo with nothing left |
| F09 Cover letter | V09 {{TODO: link}} | GUI (draft, edit, save, download) | problems shown for a draft that fails validation |
| F10 Library, reuse and export | V10 {{TODO: link}} | GUI "Use as-is" + download, CLI `export` | reuse refused for a placeholder resume |
| F11 Application tracking | V11 {{TODO: link}} | GUI (log from resume page, tracker) + CLI `app status` | illegal move (e.g. To apply → Offer) refused |
| F12 Dashboard and goals | V12 {{TODO: link}} | GUI + CLI `dashboard` | goal of 0 refused; follow-ups due |
