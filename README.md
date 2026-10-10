# CoopPilot — EECS 3311 Course Project

An AI agent that tailors one-page LaTeX resumes and cover letters to co-op job postings, using only the student's real experience, and tracks applications.

- **Stage 1 — Design:** [docs/stage1/Stage1-Design-Report.md](docs/stage1/Stage1-Design-Report.md)
- Stage 2 — Implementation: *coming*
- Stage 3 — Testing (JUnit + KUMA): *coming*

**Stack:** Java 21 · Spring Boot 3 + Thymeleaf (web GUI) · picocli (CLI) · SQLite (Spring JDBC) · Anthropic Claude (Messages API with tool use)

## Diagrams

All UML diagrams are made with [UMLet](https://www.umlet.com). The sources are the `.uxf` files in `docs/stage1/diagrams/`; the report shows PNGs rendered from them.

To re-export every diagram from the command line (UMLet standalone):

```
for f in docs/stage1/diagrams/*.uxf; do
  java -jar /path/to/Umlet/umlet.jar -action=convert -format=png -filename="$f"
done
```

Or open a `.uxf` in UMLet and use **File → Export as… → PNG**, saving it next to the `.uxf` with the same name.
