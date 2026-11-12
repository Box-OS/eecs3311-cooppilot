# 3. Implemented design patterns

Seven distinct patterns are implemented, matching the seven planned in Stage 1. MVC, Repository and dependency injection are also used but are not counted.

## 3.1 Strategy — interchangeable condense algorithms

* **Problem.** A resume can be fitted to one page cheaply with fixed rules or more cleverly with the LLM. The agent, the GUI buttons and the CLI must all trigger "condense" without knowing which algorithm runs.
* **Roles.** Strategy: `service.CondenseStrategy`. Concrete strategies: `service.RuleBasedCondense`, `service.LlmCondense`. Contexts: `agent.tools.CondenseTool` (agent) and `service.edit.CondenseCommand` (GUI/CLI). The strategies are registered by mode in `AppConfig.condenseStrategies()` (`Map<Mode, CondenseStrategy>`).
* **Files / methods.** `CondenseStrategy.condense(resume, analysis, pages)`, `lastReport()`; `CondenseTool.execute` and `CondenseCommand.execute` select by `Mode` and never branch on the algorithm.
* **Why useful.** Same signature, different cost and behaviour, chosen at run time (the agent's `mode` argument or the button pressed). `LlmCondense` even reuses `RuleBasedCondense` as its fallback. Adding a third algorithm means one class and one map entry.
* **Stage 1.** Matches. Change: the GUI/CLI context is `CondenseCommand` instead of the facade, so condensing is undoable (C03).

## 3.2 Command — undoable resume edits

* **Problem.** AI revisions, manual bullet edits, block removals and condensing all change a resume; the student must be able to undo and redo any of them in order, and an AI revision (expensive, non-repeatable) must be reversible exactly.
* **Roles.** Command: `service.edit.ResumeEditCommand`. Concrete commands: `AiRevisionCommand`, `ReplaceBulletCommand`, `RemoveBlockCommand`, `CondenseCommand`. Invoker: `RevisionHistory` (undo/redo stacks). Receiver: `domain.Resume`. Client: `ResumeEditor` (creates commands; one history per resume).
* **Files / methods.** `RevisionHistory.run/undo/redo/log`; each command's `execute()` stores what `undo()` needs (old text, removed block and position, or a before/after snapshot).
* **Why useful.** The history treats every edit uniformly; `AiRevisionCommand` calls the LLM only on the first `execute()` and redo reapplies the stored result.
* **Stage 1.** Matches, with one added concrete command (`CondenseCommand`) and the client moved from the facade to `ResumeEditor` (C-Command).

## 3.3 Adapter — provider-independent LLM access

* **Problem.** The agent and the LLM tasks need one request/response format with tool calls, independent of any provider's wire format, and must be testable without the network.
* **Roles.** Target: `llm.LLMClient`. Adapter: `llm.ClaudeAdapter` (translates `LlmRequest`/`LlmResponse`/`ToolCall`/`Message` to and from the Anthropic Messages API JSON: `tool_use`/`tool_result` blocks, merged consecutive tool results, usage). Second implementation: `llm.MockLlmClient`. Clients: `AgentController`, `LlmTask` subclasses, `LlmCondense`.
* **Files / methods.** `ClaudeAdapter.complete`, `toProvider`, `fromProvider`; provider chosen in `AppConfig.llmClient` from `cooppilot.llm.provider`.
* **Why useful.** No provider type appears outside the adapter; switching models or providers touches one class; every agent test runs on the mock.
* **Stage 1.** Partly different: the adaptee is the HTTP API directly (not the Java SDK), and `OpenAiCompatibleAdapter` was not built (C01, C-Adapter).

## 3.4 Observer — live agent progress and dashboard updates

* **Problem.** (a) The browser and the CLI show each agent step live, but the agent core must not depend on either UI. (b) Dashboard statistics must stay correct whenever any application changes, without recomputing on every page view.
* **Roles.** (a) Subject `agent.AgentSession`; observer interface `agent.AgentEventListener`; concrete observers `ui.web.SseProgressPublisher` (SSE events to the browser) and the CLI's printer in `CliCommands.TailorCmd`. (b) Subject `tracking.ApplicationTracker`; observer interface `tracking.ApplicationListener`; concrete observer `tracking.DashboardService` (invalidates its cache).
* **Files / methods.** `AgentSession.addListener`, `notifyToolCalled/notifyToolResult/notifyFinished`; `ApplicationTracker.addListener`, `transition` → `onApplicationChanged`.
* **Why useful.** One-to-many notification with no compile-time dependency from the core on any UI; the dashboard is cheap to read.
* **Stage 1.** Matches; the CLI observer is an anonymous listener inside `TailorCmd` rather than `CliRunner` itself (C-Observer).

## 3.5 State — application lifecycle

* **Problem.** What an application can do next depends on its status, and entering a status has side effects (Applied schedules a follow-up).
* **Roles.** Context: `tracking.Application`. State: `tracking.ApplicationState` (abstract). Concrete states: the seven constants `TO_APPLY`, `APPLIED`, `ASSESSMENT`, `INTERVIEW`, `OFFER`, `REJECTED`, `GHOSTED`, each an anonymous subclass that defines its legal next statuses and overrides `onEnter` where needed. `tracking.Status` is the stored name and maps back to its state.
* **Files / methods.** `Application.transitionTo(target, today, note)` delegates to `ApplicationState.moveTo`, which checks `canMoveTo` and calls the new state's `onEnter`.
* **Why useful.** No status `switch` anywhere; the GUI menu is built from `allowedNext()`; adding a status touches one place.
* **Stage 1.** Same pattern; different form (stateless shared constants instead of seven top-level classes, `IllegalStateException` instead of a custom exception, transitions by target status instead of events; C-State).

## 3.6 Facade — one entry point for both UIs

* **Problem.** A user action such as "tailor a resume" involves the agent, vault, library, repositories, editor and tracker, and two UIs need identical capabilities.
* **Roles.** Facade: `app.CoopPilotFacade`. Subsystems: `VaultService`, `LatexResumeParser`, `ResumeLibrary`, `FitScorer`, `KeywordScanner`, `AgentController`, `ResumeEditor`, `ExportService`, `ApplicationTracker`, `DashboardService`, repositories. Clients: `VaultController`, `PostingController`, `ResumeController`, `TrackerController`, `AgentSessionManager`, `CliCommands`.
* **Why useful.** Controllers and CLI commands are thin and call only the facade, so the GUI and CLI cannot drift apart; the CLI's `--json` mode becomes a ready-made test entry point for Stage 3.
* **Stage 1.** Matches.

## 3.7 Template Method — single-shot LLM tasks

* **Problem.** Posting analysis, revisions, cover letters and smart condensing all build a request, call the LLM, parse, validate and retry on invalid output; only the prompt, parsing and checks differ.
* **Roles.** Abstract class `llm.LlmTask<T>` with `final run()`; concrete classes `PostingAnalysisTask`, `RevisionTask`, `CoverLetterTask`, `SmartCondenseTask` overriding `buildRequest()`, `parse()` and the `validate()` hook.
* **Why useful.** The retry-with-feedback policy, which guards against malformed or invented output, is written once and cannot be skipped (`run()` is final).
* **Stage 1.** Matches.

## 3.8 Updated UML diagrams

The class diagrams were redrawn for the implemented system in UMLet (sources in `docs/stage2/diagrams/*.uxf`, PNG exports beside them). Pattern roles are marked with stereotypes.

![Class diagram 1 — agent core, tools and LLM layer](docs/stage2/diagrams/class-1-agent-core.png)

![Class diagram 2 — domain, services, edit commands and persistence](docs/stage2/diagrams/class-2-domain-services.png)

![Class diagram 3 — facade, web GUI, CLI and application tracking](docs/stage2/diagrams/class-3-web-cli-tracking.png)

**Use-case and sequence diagrams.** The Stage 1 use-case diagram and SD01–SD08 still describe the implemented interactions; the differences are at the method level and are listed in Section 4 (notably: SD02 answers are filed by `VaultService.fileAnswer` as a fact line; SD04 commands are created by `ResumeEditor`, not the facade; SD06 export writes `exports/NAME.tex`; SD09 commands are `cooppilot --cli tailor POSTING_ID` and progress is printed by a listener in `TailorCmd`). No use case was added or removed.

# 4. Changes and rationale

## 4.1 Consolidated record (Original design → Implemented change → Reason → Impact)

| ID | Original design | Implemented change | Reason | Impact |
|---|---|---|---|---|
| C01 | `ClaudeAdapter` wraps the Anthropic Java SDK | Builds Messages API JSON with Jackson and sends it with `java.net.http.HttpClient` | One less dependency to version-match; translation is small and unit-testable offline | Adapter unchanged; `AnthropicClient` removed from the class diagram |
| C02 | `Message(role, content)` | `Message(role, content, toolCalls, toolCallId, error)`, roles USER/ASSISTANT/TOOL | The loop must send tool calls and their results back to the model | `AgentSession.messages`, `LlmRequest` carry the full tool conversation |
| C03 | `CondenseStrategy` with two strategies, context = facade | Adds `lastReport()`; `LlmCondense` falls back to rules; GUI/CLI condensing runs as `CondenseCommand` | Users and the agent must see what was cut; result must always fit; condensing should be undoable | Strategy unchanged; Command gains a 4th concrete command |
| C04 | Facade owns one `RevisionHistory` | `ResumeEditor` keeps one history per resume and saves after each step | Several resumes can be edited independently | Facade delegates to `ResumeEditor` (C-Command) |
| C05 | CLI `coop …` commands; `CliRunner` observes the agent | `cooppilot --cli …` via picocli `CliCommands`; progress printed by a listener in `TailorCmd`; new `shell`, `condense`, `--json` | Spring Boot runs the CLI in the same jar; undo history needs one process | Observer role moves; CLI covers all 12 features |
| C06 | 7 concrete state classes, `handle(event)`, custom exception | 7 state constants (anonymous subclasses) in `ApplicationState`, `transitionTo(target)`, `IllegalStateException` | States carry no data, so shared instances suffice; status names map 1:1 to the stored enum | State pattern kept; fewer files (C-State) |
| C07 | `OpenAiCompatibleAdapter` | Not implemented | Only Claude is used; the mock covers offline/test use | Adapter has one real adapter + mock |
| C08 | F10 match = skill overlap 65% + role-family similarity 35% | Share of the posting keywords the student has that the resume shows (must-haves ×2) | No reliable role-family classifier yet; keyword evidence is what an ATS sees | Same thresholds (≥ 75% reuse) |
| C09 | F03 thresholds: ≥ 75% reuse only | REUSE ≥ 75%, TWEAK ≥ 45%, else GENERATE | A strategy needed a defined boundary between tweak and generate | `FitScorer.TWEAK_THRESHOLD` |
| C10 | F06 answer rewritten by the LLM into a clean vault entry; repeat questions answered from the session | Answer appended as a fact line to the matching entry (or a new entry); repeats answered from the vault | Deterministic filing cannot lose or alter facts; vault lookup works across sessions | `VaultService.fileAnswer/knownAnswer` |
| C11 | F08 "What changed" diff | Revision log ("You asked: … — summary") with undo/redo | Time; the log plus undo covers the user need to see and revert changes | Diff view listed as a limitation |
| C12 | F09 validation 250–400 words; editable text area | Prompt asks for 250–350, validation accepts 180–450; letter viewed and downloaded | Strict ranges caused needless retries; editing is done in the downloaded file | `CoverLetterTask.validate` |
| C13 | F02 manual field entry on failure; deadline extracted | Error message on failure (posting kept); no deadline extraction | Time; analysis can be retried with a button | Limitation |
| C14 | F01 duplicate skill rejected with a message | Duplicates merged silently (tags/body kept) | Re-importing the same resume should be harmless | `VaultService.addEntry` |
| C15 | F05 LLM failure → retry with backoff | Implemented: 429/5xx/network retried twice with doubling waits; 4xx fail at once | Matches Stage 1 (added after the first implementation review) | `AgentController.completeWithRetry` |
| C16 | — (new class) | `FactGuard` shared deterministic check for invented numbers | Same rule needed by compose, revision and cover letters | Used by 3 classes |
| C17 | `AgentSessionManager` runs the agent | Also buffers events so late or reconnecting pages see every step | The page subscribes after the run starts | SSE robust to timing |
| C18 | Dates from the system clock | `Clock` bean injected into tracker and dashboard | Follow-up and streak rules must be testable | Test-only impact |

## 4.2 Modified features (Section 3 items 1–5)

No feature was added, replaced or removed. The substantially modified features are:

| Feature | 1. Original | 2. Implemented behaviour | 3. What changed and why | 4. User need | 5. Affected use cases / classes / methods / patterns |
|---|---|---|---|---|---|
| C-F02 | F02 | Analyze with retries; failure shows an error, posting kept as text | No manual-entry form, no deadline (time) | Still turns a posting into requirements and keywords; "Analyze" can be retried | UC02; `PostingController.analyze`; Template Method |
| C-F03 | F03 | Adds TWEAK ≥ 45% | Needed a defined middle band | Same: score, gaps, strategy | UC03; `FitScorer.strategy` |
| C-F06 | F06 | Fact lines + vault lookup | Deterministic, cross-session memory | Agent still never asks twice | UC06; `AskUserTool`, `VaultService.fileAnswer/knownAnswer` |
| C-F08 | F08 | Revision log, undo/redo | No diff view (time) | Student still sees each change and can revert it | UC07; `ResumeEditor`, `RevisionHistory.log`; Command |
| C-F09 | F09 | 180–450 validation, view/download | Fewer needless retries; no in-app editor | Letter still grounded, named and exportable | UC08; `CoverLetterTask.validate` |
| C-F10 | F10 | Keyword-evidence match % | No role-family classifier | Reuse suggestion still at ≥ 75% | UC09; `ResumeLibrary.findClosest/matchPercent` |

## 4.3 Modified patterns (Section 3 items 6–10)

| Pattern | 6. Original | 7. Implemented | 8. Classes and roles | 9. Problem addressed | 10. Why appropriate / architectural effect |
|---|---|---|---|---|---|
| C-State | State with 7 classes and events | State with 7 shared state objects, transitions by target | `Application` context, `ApplicationState` state, 7 constants concrete states, `Status` mapping | Legal moves and entry actions per status | States hold no data, so singletons avoid allocation and keep all rules in one file; GUI reads `allowedNext()` |
| C-Command | Command with facade as client, 3 commands | Command with `ResumeEditor` as client, 4 commands | `ResumeEditCommand`, `RevisionHistory` invoker, `ResumeEditor` client, `Resume` receiver, `CondenseCommand` new | Uniform undo/redo for all edits | One history per resume; condensing joins the same undo stack; facade stays thin |
| C-Adapter | 2 adapters over SDK/HTTP | 1 adapter over HTTP + mock | `LLMClient` target, `ClaudeAdapter` adapter, `MockLlmClient` | Provider independence and offline tests | Same isolation with fewer dependencies |
| C-Observer | `CliRunner` as CLI observer | Listener inside `CliCommands.TailorCmd` | `AgentSession` subject, `AgentEventListener`, `SseProgressPublisher`, CLI listener | Live progress without UI dependencies | The picocli command owns its output stream, so it owns the listener |
