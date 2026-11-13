# CoopPilot — EECS 3311 Course Project

An AI agent that tailors one-page LaTeX resumes and cover letters to co-op job postings, using only the student's real experience, and tracks applications.

- **Stage 1 — Design:** [docs/stage1/Stage1-Design-Report.md](docs/stage1/Stage1-Design-Report.md)
- **Stage 2 — Implementation report:** [stage2-report.pdf](stage2-report.pdf) (source: [docs/stage2/report/](docs/stage2/report/))
- Stage 3 — Testing (JUnit + KUMA): *coming*

**Stack:** Java 21 · Spring Boot 3 + Thymeleaf (web GUI) · picocli (CLI) · SQLite (Spring JDBC) · Anthropic Claude (Messages API with tool use) · Maven

## Prerequisites

- **Java 21** (`java -version`). Maven does **not** need to be installed: the included Maven Wrapper (`./mvnw` on macOS/Linux, `mvnw.cmd` on Windows) downloads it on first use. On macOS: `brew install openjdk@21`.
- An **Anthropic API key** (console.anthropic.com → API Keys) for the AI features. Without a key the app still runs, using a mock LLM: deterministic features work fully; AI features return a fixed "no key" reply.

## Install and configure

```
git clone https://github.com/Box-OS/eecs3311-cooppilot.git
cd eecs3311-cooppilot
cp application-local.properties.example application-local.properties
```

Edit `application-local.properties` (it is **git-ignored**, so your key is never committed):

```
cooppilot.llm.provider=claude
cooppilot.llm.api-key=YOUR_LOCAL_API_KEY_HERE
cooppilot.llm.model=claude-haiku-4-5
```

The key can also come from the `ANTHROPIC_API_KEY` environment variable. Optional settings in `src/main/resources/application.properties`: `cooppilot.student-name` (signs cover letters), `cooppilot.agent.max-steps` (15), `cooppilot.export-dir` (`exports`).

Maven downloads all dependencies on the first build. Run the tests (no key needed):

```
mvn test
```

### On Windows (PowerShell)

```
winget install Microsoft.OpenJDK.21
winget install Git.Git
# close and reopen PowerShell, then:
git clone https://github.com/Box-OS/eecs3311-cooppilot.git
cd eecs3311-cooppilot
copy application-local.properties.example application-local.properties
notepad application-local.properties          # set provider=claude and your key, save
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run                    # then open http://localhost:8080
.\mvnw.cmd package -DskipTests
java -jar target\cooppilot-0.1.0.jar --cli --help
```

For the CLI's ✓/→ symbols to display correctly, use Windows Terminal or run `chcp 65001` first.
On macOS/Linux, use `./mvnw` wherever this README says `mvn`.

## Run the web GUI

```
mvn spring-boot:run
```

Open **http://localhost:8080**.

| Page | Features |
|---|---|
| `/vault` | F01 import a LaTeX resume, add/search vault entries |
| `/postings`, `/postings/{id}` | F02 import + analyze, F03 fit and strategy, F04 keyword scan, F10 use as-is |
| `/tailor/{run}` | F05 live agent steps (Server-Sent Events), F06 answer the agent's questions |
| `/resumes`, `/resumes/{id}` | F07 page meter + Quick/Smart condense, F08 revise + undo/redo, F09 cover letter, F10 `.tex` download |
| `/tracker` | F11 log applications, move them through their statuses |
| `/dashboard` | F12 goals, today/this week, response rate, streak, last 7 days |

## Run the CLI

```
mvn package -DskipTests
java -jar target/cooppilot-0.1.0.jar --cli --help
```

`--cli` runs one command without starting the web server. Add `--json` to any command for machine-readable output.

### Example session (with the safe sample data in `samples/`)

```
J="java -jar target/cooppilot-0.1.0.jar --cli"
$J vault import samples/sample-resume.tex --name CV_General      # F01
$J posting add samples/sample-posting.txt --analyze              # F02 (prints the posting id)
$J fit <postingId>                                               # F03
$J library                                                       # resume ids
$J scan <resumeId> <postingId>                                   # F04
$J tailor <postingId>                                            # F05 + F06 (answer '?' questions)
$J condense <resumeId> <postingId> --mode RULES                  # F07
$J letter <postingId> <resumeId>                                 # F09
$J export <resumeId>                                             # F10 -> exports/CV_....tex
$J app add --posting <postingId> --resume CV_General             # F11
$J app status <appId> APPLIED                                    # F11
$J dashboard                                                     # F12
$J shell                                                         # interactive: revise / undo / redo (F08)
```

## Sample data

`samples/sample-resume.tex` (a fictional student's Jake's-template resume) and `samples/sample-posting.txt` (a fictional posting) reproduce every feature without personal data. The local database is `cooppilot.db` and exports go to `exports/`; both are git-ignored. Delete `cooppilot.db` to start fresh.

## Demonstration videos

Feature-by-feature links with timestamps are in the report's **Section 7 — Demonstration index**. The recording plan is in [docs/stage2/demo-script.md](docs/stage2/demo-script.md).

## Project layout

```
src/main/java/ca/yorku/cooppilot/
  agent/          agent loop, session (observer subject), prompt builder
  agent/tools/    the 8 agent tools + ToolRegistry (argument validation)
  llm/            LLMClient (adapter target), ClaudeAdapter, mock, LlmTask template + 4 tasks
  domain/         vault entries, resumes, postings, keywords, reports
  service/        parser, assembler, fit, keyword scan, library, export, page estimate, condense strategies
  service/edit/   undo/redo commands and RevisionHistory
  tracking/       application states (State), tracker, dashboard (Observer)
  persistence/    Repository<T>, SqliteRepository<T> and repositories
  ui/web/         Spring MVC controllers, SSE agent sessions
  ui/cli/         picocli commands
  app/            CoopPilotFacade, configuration, entry point
docs/stage1/      Stage 1 design report and UMLet diagrams
docs/stage2/      Stage 2 report sources, UMLet class diagrams, demo script, report build script
```

## Diagrams

All UML diagrams are made with [UMLet](https://www.umlet.com): `.uxf` sources with PNG exports beside them, in `docs/stage1/diagrams/` and `docs/stage2/diagrams/`.

## Rebuilding the report

`python3 docs/stage2/build_report.py` (needs pandoc and wkhtmltopdf) regenerates `stage2-report.md` and `stage2-report.pdf`, including the weekly commit table with links.
