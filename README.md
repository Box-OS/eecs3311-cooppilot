# CoopPilot — EECS 3311 Course Project

An AI agent that tailors one-page LaTeX resumes and cover letters to co-op job postings, using only the student's real experience, and tracks applications.

- **Stage 1 — Design:** [docs/stage1/Stage1-Design-Report.md](docs/stage1/Stage1-Design-Report.md)
- Stage 2 — Implementation: *coming*
- Stage 3 — Testing (JUnit + KUMA): *coming*

**Stack:** Java 21 · Spring Boot 3 + Thymeleaf (web GUI) · picocli (CLI) · SQLite (Spring JDBC) · Anthropic Claude (Messages API with tool use)

## Diagrams

All UML diagrams are made with [UMLet](https://www.umlet.com). The sources are the `.uxf` files in `docs/stage1/diagrams/`; the report shows PNGs rendered from them.

## Running (Stage 2, in progress)

Prerequisites: Java 21 and Maven 3.9+.

1. Copy the example config and add your key (the copy is git-ignored, never commit it):
   ```
   cp application-local.properties.example application-local.properties
   ```
   Then set `cooppilot.llm.api-key=` to your Anthropic key. Without a key the app runs with `cooppilot.llm.provider=mock`.
2. Build and test: `mvn test`
3. Start the web GUI: `mvn spring-boot:run`, then open http://localhost:8080

### Command line (CLI)

Build once with `mvn package -DskipTests`, then run any command with `--cli` (no web server starts):

```
java -jar target/cooppilot-0.1.0.jar --cli --help
java -jar target/cooppilot-0.1.0.jar --cli vault import samples/sample-resume.tex --name CV_General
java -jar target/cooppilot-0.1.0.jar --cli posting add samples/sample-posting.txt --analyze
java -jar target/cooppilot-0.1.0.jar --cli fit <postingId>
java -jar target/cooppilot-0.1.0.jar --cli tailor <postingId>
java -jar target/cooppilot-0.1.0.jar --cli dashboard --json
java -jar target/cooppilot-0.1.0.jar --cli shell        # interactive; undo/redo work across commands
```

Add `--json` to any command for machine-readable output.
