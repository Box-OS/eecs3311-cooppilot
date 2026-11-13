---
title: "CoopPilot — Stage 2 Implementation Report"
subtitle: "EECS 3311 Software Design · Fall 2026 · AI–Human Collaborative Implementation"
author: "Sina Akhavan Safar · solo project · github.com/Box-OS/eecs3311-cooppilot"
---

# 1. Project overview and implementation status

**CoopPilot** is an AI agent that helps a co-op student apply for jobs. The student keeps their real experience in an *experience vault* (imported from their LaTeX resume and extended by hand). For each job posting, an LLM extracts the requirements and keywords, deterministic code scores the student's fit, and a tool-using agent builds a **one-page, tailored LaTeX resume** from the student's own material, asking the student when a fact is missing instead of guessing. The student can then revise the resume in plain English (with undo/redo), draft a cover letter, export the files for Overleaf, and track every application through its lifecycle on a progress dashboard.

**Users.** University co-op students (the author first) who apply to many postings each term and need each resume to fit the posting, stay truthful, and fit on one page.

**AI / LLM integration.** Anthropic Claude (default model `claude-haiku-4-5`) through the Messages API with native tool use, behind the `LLMClient` interface. The LLM is used in two ways:

* **Agent (F05–F07):** a reason–act–observe loop (`AgentController`) in which the model chooses among 9 tools (find similar resume, search vault, keyword scan, compose, estimate page, condense, ask user, save). This covers the required agent behaviours: *planning and multi-step execution* (the loop, up to 15 steps), *tool use* (validated by `ToolRegistry`), *retrieval* (vault and resume library search), *memory* (answers are filed in the vault and never asked again), and *decision making* (reuse vs. rewrite, what to cut to fit one page).
* **Single-shot tasks (F02, F08, F09, smart F07):** posting analysis, natural-language revision, cover letters and smart condensing, each through the `LlmTask<T>` template, which validates the reply and retries with the problems listed.

Deterministic Java code enforces every rule the LLM could break: only facts from the vault (new numbers in rewritten text are rejected by `FactGuard`), reverse-chronological Experience, no bold words in Education/Skills, one page maximum, and `CV_Company_Role` / `CL_Company_Role` naming.

**Current working functionality.** All 12 Stage 1 features are implemented and reachable from **both** interfaces:

* **Web GUI** (Spring Boot 3 + Thymeleaf, `mvn spring-boot:run`, http://localhost:8080): Vault, Postings, live Tailoring page (agent steps streamed over Server-Sent Events, questions answered in the page), Resumes (revise/undo/redo, one-page meter and condense, cover letter, `.tex` download), Tracker, Dashboard.
* **CLI** (picocli, `java -jar target/cooppilot-0.1.0.jar --cli <command>`): a command for every feature, `--json` output for scripting (used by the Stage 3 KUMA tests), and an interactive `shell` in which undo/redo history persists between commands.
* **Storage:** SQLite (`cooppilot.db`, git-ignored). **Build:** Maven (wrapper included: `mvnw`/`mvnw.cmd`), Java 21; runs on Windows, macOS and Linux. **Tests:** 108 automated tests (JUnit 5, Spring Boot Test, MockMvc) pass.
* **Real use:** the agent was run end-to-end against Claude Haiku 4.5, and the author's real vault (65 entries) and resume library (50 resumes) were imported from his earlier tracker through the JSON backup import.

**Limitations and incomplete functionality (honest list).**

1. **Real-LLM behaviour is verified by manual runs, not by automated tests.** Automated tests drive the agent with a scripted `MockLlmClient`; the real model's tool use was checked manually (Section 6). Systematic agent evaluation is Stage 3 (KUMA).
2. **Only invented numbers are blocked deterministically.** `FactGuard` rejects new numbers, but a reworded bullet can still add small non-numeric embellishments (in one real run the model added "algorithms" to a tutoring bullet). The student reviews the "What changed" diff; deeper fact checking is Stage 3 work.
3. **Undo/redo history lives in memory**: it is lost when the application restarts, and in the CLI it only spans one process (hence the `shell` command).
4. **The page estimate is a heuristic** (line-cost model calibrated on 50 real one-page resumes), not a compiled PDF; the in-app preview approximates the layout and the student compiles the `.tex` in Overleaf.
5. **Single user, no authentication**; the app is meant to run locally.
6. Not implemented from the Stage 1 design: only the `OpenAiCompatibleAdapter` (a second LLM provider); Claude and the offline mock implement `LLMClient`. See C07.
7. Without an API key the app runs with the mock LLM: deterministic features work fully, LLM features return a fixed "no key" reply.

# 2. Feature-to-implementation mapping

Every Stage 1 feature (F01–F12) is accounted for below. No feature was removed or replaced; several were modified (details in Section 4). There are no new features beyond F01–F12; the CLI `shell` and `--json` mode are interface additions, not features. Type: **D** deterministic, **AI** LLM-based, **H** hybrid. Package prefix `ca.yorku.cooppilot` is omitted. Video links are in Section 7.

## F01 — Experience Vault and Resume Import (D)

| Field | Entry |
|---|---|
| Stage 1 reference | F01, UC01, SD08 |
| Status | Implemented + extended (JSON backup import; see C21) |
| Classes / files | `service.LatexResumeParser` (splits a Jake's-template `.tex` into blocks), `service.LatexText` (brace-balanced argument reader, LaTeX→text, date ranges), `service.VaultService` (vault rules: dedupe, search, tag filter, skills, import), `service.BackupImporter` (JSON backup of vault entries and resumes), `vault-edit.html` (edit an entry), `domain.VaultEntry`/`EntryKind`/`EntryUse`, `domain.ResumeBlock`/`Resume`/`Section`, `persistence.SqliteVaultRepository`, `persistence.SqliteResumeRepository`, `app.CoopPilotFacade`, `ui.web.VaultController` + `vault.html`, `ui.cli.CliCommands.VaultCmd` |
| Key methods | `LatexResumeParser.parse(latex)`, `VaultService.importFromResume(parse)`, `VaultService.addEntry(e)`, `VaultService.search(q, tags)`, `CoopPilotFacade.importResume(latex, name)`, `importBackup(json)`, `updateVaultEntry/deleteVaultEntry` |
| Design patterns | Facade; Repository (not counted) |
| Video | V01 |

**Collaboration.** The Vault page uploads the `.tex` file (CLI: `vault import FILE`) → `VaultController` calls `CoopPilotFacade.importResume` → `LatexResumeParser` finds each `\section`, reads `\resumeSubheading` (detecting the education vs. experience argument order), `\resumeProjectHeading` (tech stack → tags), `\resumeItem` bullets and skill lines, and returns blocks plus warnings → the facade saves an `ORIGINAL` resume and `VaultService` creates one vault entry per experience and one skill entry per skill, merging duplicates → the page shows counts and warnings. Re-importing the same resume adds nothing. Entries can be edited (✎) or deleted (×), filtered by clicking a tag, and a duplicate added by hand (e.g. the same skill with different case or spacing) is refused with a message. **Import backup** (Vault page or `vault import-backup FILE`) loads a JSON file of vault entries and resumes, so a student can start from an existing vault; it was used to import the author's real data from his earlier tracker.

## F02 — Job Posting Import and Analysis (H)

| Field | Entry |
|---|---|
| Stage 1 reference | F02, UC02, SD03 |
| Status | Implemented |
| Classes / files | `llm.PostingAnalysisTask` (prompt, JSON parsing, validation, soft-skill rule), `llm.LlmTask` (retry template), `llm.JsonReply` (extracts JSON from chatty replies), `domain.Posting`/`PostingAnalysis`/`Keyword`, `persistence.SqlitePostingRepository`, `CoopPilotFacade`, `ui.web.PostingController` + `postings.html`/`posting.html`, `CliCommands.PostingCmd` |
| Key methods | `CoopPilotFacade.importPosting(text, company, role)`, `CoopPilotFacade.analyzePosting(id)`, `CoopPilotFacade.saveAnalysis(...)`, `LlmTask.run()`, `PostingAnalysisTask.parse/validate`, `PostingAnalysisTask.softSkillFocus(mustHaves)` |
| Design patterns | Template Method, Adapter, Facade |
| Video | V02 |

**Collaboration.** The student pastes a posting (CLI: `posting add FILE --analyze`) → the facade saves it → `PostingAnalysisTask.run()` (template) builds a strict-JSON prompt, calls `LLMClient`, parses must-haves, nice-to-haves, keywords with aliases and required documents, and validates (keywords non-empty, requirements present); invalid replies are retried with the problems listed. The **soft-skill focus** flag is then recomputed deterministically (2+ or 40%+ of must-haves are soft skills). Blank company/role/term/deadline are filled from the analysis. The student can **edit the analysis** (company, role, term, deadline, requirements, keywords as `term | aliases | must`, documents), or fill it in by hand when the LLM analysis fails (UC02 3a).

## F03 — Fit Analysis and Skill-Gap Detection (H)

| Field | Entry |
|---|---|
| Stage 1 reference | F03, UC03, SD03 |
| Status | Implemented (TWEAK threshold added, see C-F03) |
| Classes / files | `service.FitScorer` (weighted score, gaps, strategy thresholds), `service.ResumeLibrary.findClosest` (closest saved resume), `domain.FitReport`/`ResumeMatch`/`TailorStrategy`, `CoopPilotFacade.analyzeFit`, `posting.html`, `CliCommands.FitCmd` |
| Key methods | `FitScorer.score(analysis, skills, closest)`, `ResumeLibrary.findClosest(analysis)`, `FitScorer.strategy(closest)` |
| Design patterns | Facade |
| Video | V03 |

**Collaboration.** After analysis, the posting page (CLI: `fit POSTING_ID`) calls `CoopPilotFacade.analyzeFit` → `FitScorer` matches each keyword (must-haves weigh 2) against the vault's skills and lists the gaps, must-haves first → `ResumeLibrary.findClosest` scores every saved resume that has no `[placeholders]` → the strategy is **REUSE** at ≥ 75% match, **TWEAK** at ≥ 45%, else **GENERATE**. The LLM's part is the analysis from F02; the scoring is deterministic.

## F04 — ATS Keyword Coverage Scan (D)

| Field | Entry |
|---|---|
| Stage 1 reference | F04, UC03, SD03 |
| Status | Implemented |
| Classes / files | `service.KeywordScanner`, `service.ResumeText` (plain-text views of a resume; "evidence" text excludes the skills line), `domain.Keyword.appearsIn` (word-boundary matching: Java ≠ JavaScript, C++ handled), `domain.KeywordReport`, `agent.tools.KeywordScanTool`, chips on `posting.html` and `resume.html`, `CliCommands.ScanCmd` |
| Key methods | `KeywordScanner.scan(resume, analysis)`, `Keyword.appearsIn(text)`, `KeywordReport.coverage()` |
| Design patterns | N/A (used as an agent tool) |
| Video | V04 |

**Collaboration.** For a chosen resume, `KeywordScanner` strips LaTeX, then classifies each posting keyword as **backed** (in experience/project text), **thin** (only in the skills line), **known but not worded** (the vault supports it) or **missing**. The posting page and the resume page show coloured chips (on the resume page they refresh after every edit, for any chosen posting); the agent calls the same scanner through `keyword_scan`.

## F05 — Agentic Resume Tailoring (AI)

| Field | Entry |
|---|---|
| Stage 1 reference | F05, UC04, SD01, SD09 |
| Status | Implemented (direct HTTP adapter instead of the SDK; richer `Message`; autonomy nudges; see C01, C02, C20) |
| Classes / files | `agent.AgentController` (loop, step limit, LLM retry), `agent.AgentSession` (conversation, draft, listeners), `agent.PromptBuilder` (goal + hard rules), `agent.tools.ToolRegistry` (argument validation), tools `FindSimilarResumeTool`, `SearchVaultTool`, `KeywordScanTool`, `ComposeResumeTool`, `EstimatePageTool`, `CondenseTool`, `AskUserTool`, `SaveResumeTool`; `service.LatexAssembler`, `service.FactGuard`, `service.ExportService.fileName`; `llm.ClaudeAdapter`; `ui.web.AgentSessionManager`, `SseProgressPublisher`, `TailorController` + `tailor.html`; `CliCommands.TailorCmd` |
| Key methods | `CoopPilotFacade.tailorResume(postingId, q, listener)`, `AgentController.run/loop/completeWithRetry`, `ToolRegistry.execute/validateArgs`, `ComposeResumeTool.execute`, `LatexAssembler.assemble`, `SaveResumeTool.execute` |
| Design patterns | Observer, Adapter, Facade (+ Strategy through `CondenseTool`) |
| Video | V05 |

**Collaboration.** "Tailor with the agent" (CLI: `tailor POSTING_ID`) → `AgentSessionManager` starts the run on a background thread → `AgentController` sends the system prompt (goal + rules) and tool schemas to Claude → each tool call the model makes is validated by `ToolRegistry` (unknown tools, missing or mistyped arguments and tool exceptions go back to the model as errors) and executed; results return to the conversation → typical plan: `find_similar_resume` → `search_vault` → `compose_resume` (keep/reorder blocks, front-load skills, reword bullets; rewrites with invented numbers are rejected) → `keyword_scan` → `estimate_page` → `condense` if needed → `save_resume` (refused over one page; named `CV_Company_Role`). `SseProgressPublisher` (observer) streams every step to the browser. The run ends when `save_resume` has succeeded and the model stops (`COMPLETED`), at 15 steps (`STEP_LIMIT`), or on a non-retryable LLM error (`FAILED`); 429/5xx errors are retried with backoff. If the model stops to chat without saving (seen in the first real run), the loop tells it to finish and save, at most twice. When a run stops early, the partial draft is kept as `CV_Company_Role_Draft` (UC04 5a).

## F06 — Clarifying Questions and Vault Learning (H)

| Field | Entry |
|---|---|
| Stage 1 reference | F06, UC06, SD02 |
| Status | Modified (answers filed as fact lines; repeats answered from the vault; see C-F06) |
| Classes / files | `agent.tools.AskUserTool`, `agent.QuestionHandler` (interface), `ui.web.WebQuestionHandler` (waits ≤ 5 min for the browser's answer), CLI stdin handler in `CliCommands.TailorCmd`, `service.VaultService.fileAnswer/knownAnswer` |
| Key methods | `AskUserTool.execute`, `QuestionHandler.ask(question)`, `AgentSessionManager.answer(runId, answer)`, `VaultService.fileAnswer(q, a, topic, kind)`, `VaultService.knownAnswer(q)` |
| Design patterns | Interface polymorphism (`QuestionHandler`) |
| Video | V06 |

**Collaboration.** When a non-numeric fact is missing, the model calls `ask_user` → `AskUserTool` first checks the vault for an earlier answer to the same question → otherwise `QuestionHandler.ask` shows the question (web: an SSE `question` event and an answer box; CLI: a `?` prompt) → the answer is added to the vault entry it is about (or a new entry) → the tool result goes back to the model. An empty answer or a timeout tells the model not to guess.

## F07 — One-Page Fitting (Estimate and Condense) (H)

| Field | Entry |
|---|---|
| Stage 1 reference | F07, UC05, SD01 |
| Status | Implemented (+ undoable `CondenseCommand`; see C03, C19) |
| Classes / files | `service.PageEstimator`, `service.CondenseStrategy` (interface), `service.RuleBasedCondense`, `service.LlmCondense` + `llm.SmartCondenseTask`, `service.edit.CondenseCommand`, `agent.tools.EstimatePageTool`/`CondenseTool`, `AppConfig.condenseStrategies`, `resume.html` (page meter, Quick/Smart condense), `CliCommands.CondenseCmd` |
| Key methods | `PageEstimator.estimate(resume)`, `CondenseStrategy.condense(resume, analysis, pages)`, `CoopPilotFacade.condenseResume(resumeId, postingId, mode)`, `ResumeEditor.condense` |
| Design patterns | Strategy, Command, Template Method (smart) |
| Video | V07 |

**Collaboration.** `PageEstimator` sums each block's heading and wrapped-bullet lines into a fraction of a page (shown as a meter, red when over 100%). **Quick condense** (`RuleBasedCondense`, free) removes the least relevant content in a fixed order: irrelevant projects (keeping 2) → projects, then jobs, to 2 bullets → weakest extra project → projects, then jobs, to 1 bullet. Jobs give way last and one project always stays. **Smart condense** (`LlmCondense`) asks the model what to cut or shorten (validated: no unknown bullets, no new numbers); if its plan keeps failing validation it falls back to Quick condense (UC05 2a), and it finishes with the rules if still over. Condensing aims at ≤ 98% of a page; saving requires ≤ 100%. From the GUI/CLI the condense runs as a `CondenseCommand`, so it can be undone; inside the agent it runs through `CondenseTool`.

## F08 — Natural-Language Revision with Version History (H)

| Field | Entry |
|---|---|
| Stage 1 reference | F08, UC07, SD04 |
| Status | Implemented |
| Classes / files | `service.edit.ResumeEditor` (one `RevisionHistory` per resume; reassembles and saves after each step; records the last diff), `service.edit.ResumeDiff` ("What changed"), `RevisionHistory` (invoker), `ResumeEditCommand` + `AiRevisionCommand`, `ReplaceBulletCommand`, `RemoveBlockCommand`, `CondenseCommand`; `llm.RevisionTask`; `service.FactGuard`; `resume.html`; `CliCommands.ReviseCmd/EditBulletCmd/UndoCmd/RedoCmd` |
| Key methods | `CoopPilotFacade.reviseResume/replaceBullet/removeBlock/undo/redo/revisionLog`, `RevisionHistory.run/undo/redo`, `AiRevisionCommand.execute/undo` |
| Design patterns | Command, Template Method |
| Video | V08 |

**Collaboration.** The student types a request (CLI: `revise ID "..."`) → `ResumeEditor` creates an `AiRevisionCommand` and runs it through the resume's `RevisionHistory` → the command snapshots the blocks and runs `RevisionTask` (bullet rewrites/removals as JSON; validation rejects unknown bullets and numbers not in the vault or resume) → the LaTeX is reassembled and saved. Undo restores the snapshot; redo reapplies the stored result **without calling the LLM again**. A new edit clears the redo branch. After every edit, undo or redo the page shows **What changed** (added, removed and reworded bullets, old text struck through, new text in green). Bullets can also be edited by hand (✎) and entries removed (×), both undoable.

## F09 — Cover Letter Drafting (AI)

| Field | Entry |
|---|---|
| Stage 1 reference | F09, UC08, SD05 |
| Status | Implemented (validation window 180–450 words; see C12) |
| Classes / files | `llm.CoverLetterTask`, `persistence.CoverLetterRepository`, `domain.CoverLetter`, `CoopPilotFacade.writeCoverLetter`, `ui.web.ResumeController` + `letter.html`, `CliCommands.LetterCmd` |
| Key methods | `CoopPilotFacade.draftCoverLetter(postingId, resumeId)`, `saveCoverLetter(name, body)`, `CoverLetterTask.buildRequest/parse/validate` |
| Design patterns | Template Method |
| Video | V09 |

**Collaboration.** From a resume page (CLI: `letter POSTING_ID RESUME_ID`) → the facade collects only vault entries allowed in letters (`ALL`, `LETTERS_ONLY`; never `CONTEXT_ONLY`) → `CoverLetterTask` asks for a 250–350-word plain-text letter that states the posting's term and connects 2–3 real experiences to the must-haves → validation rejects bad length, letters that don't name the company, `[placeholders]` and invented numbers, with retries → saved as `CL_Company_Role` and shown in an editable text area (Save, Download .txt). If the draft still fails validation after the retries, it is saved anyway and shown with the problems listed (UC08 3a).

## F10 — Resume Library, Reuse and Export (D)

| Field | Entry |
|---|---|
| Stage 1 reference | F10, UC09, SD06 |
| Status | Modified (match formula; see C-F10) |
| Classes / files | `service.ResumeLibrary` (closest match, `reuseAs`, grouping, rename, family, delete), `service.ExportService` (naming, `.tex`/`.txt` export with sanitized names), `resumes.html`/`resume.html`/`posting.html` ("Use as-is", download), `CliCommands.LibraryCmd/ReuseCmd/ExportCmd` |
| Key methods | `CoopPilotFacade.reuseResume(postingId, resumeId)`, `ResumeLibrary.reuseAs`, `ResumeLibrary.groupByFamily`, `ExportService.fileName/writeTex/writeLetter` |
| Design patterns | Facade |
| Video | V10 |

**Collaboration.** When F03 recommends REUSE, "Use as-is" copies the saved resume unchanged as `CV_Company_Role` (the original is kept; resumes with placeholders are refused; repeating replaces the copy). The library groups resumes by role family; any resume downloads as `.tex` (GUI) or is written to `exports/` (CLI `export`), with names sanitized so they cannot leave that folder.

## F11 — Application Tracking with Status Lifecycle (D)

| Field | Entry |
|---|---|
| Stage 1 reference | F11, UC10, SD07 |
| Status | Modified (state objects as constants; exception type; see C-State) |
| Classes / files | `tracking.Application` (context), `tracking.ApplicationState` (state; seven concrete states), `tracking.Status`, `tracking.StatusEvent`, `tracking.ApplicationTracker` (subject), `tracking.SqliteApplicationRepository`, `ui.web.TrackerController` + `tracker.html`, `CliCommands.AppCmd` |
| Key methods | `CoopPilotFacade.logApplication/changeStatus`, `ApplicationTracker.transition(id, target, note)`, `Application.transitionTo`, `ApplicationState.moveTo/onEnter/allowedNext` |
| Design patterns | State, Observer |
| Video | V11 |

**Collaboration.** An application starts in **To apply** → moving it (GUI dropdown shows only legal next states; CLI `app status ID STATUS`) calls `ApplicationTracker.transition` → `Application` delegates to its current state, which refuses illegal moves (e.g. Rejected → Offer) and runs entry actions (**Applied** stamps the date and a +10-day follow-up; final states clear it; **Ghosted** can still be revived by a late reply) → the history event is saved and listeners are notified. An application can also be logged straight from a resume page ("Log application"), linking that resume.

## F12 — Progress Dashboard and Goals (D)

| Field | Entry |
|---|---|
| Stage 1 reference | F12, UC11, SD07 |
| Status | Implemented (weekly chart = last 7 days) |
| Classes / files | `tracking.DashboardService` (observer with cached stats), `tracking.DashboardStats`, `tracking.Goals`, `settings` table, `ui.web.TrackerController.dashboard` + `dashboard.html`, `CliCommands.DashboardCmd/GoalsCmd` |
| Key methods | `DashboardService.stats()`, `DashboardService.compute(apps, goals, today)`, `DashboardService.streak(...)`, `CoopPilotFacade.dashboard/setGoals` |
| Design patterns | Observer |
| Video | V12 |

**Collaboration.** `DashboardService` registers with `ApplicationTracker` and drops its cached statistics whenever an application changes (or the goals change); `stats()` recomputes only then (and when the date changes): today and this week vs. goals (with "Goal met"), total applied, response rate, interviews, offers, follow-ups due, daily-goal streak and a last-7-days bar chart, plus the list of follow-ups due and an empty state when there are no applications. Goals are stored in SQLite and must be at least 1 (UC11 3a).
