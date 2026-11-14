# Recording with my own data

The same two recordings as `demo-script.md` (A: web GUI, B: CLI), with my real vault and resumes instead of the fictional samples.
What differs from the script is here; anything not mentioned is as `demo-script.md` says.

## Before recording

- The app runs on an **empty** database (`cooppilot.db`; the fictional one is kept as `cooppilot-demo-fictional.db`).
  Start it with `java -jar target\cooppilot-0.1.0.jar` → http://localhost:8080.
- My data: `%USERPROFILE%\AgentOfficeData\exports\cooppilot-backup.json` (65 vault entries, 77 resumes, from ResumeBase).
- Real postings to paste: `%USERPROFILE%\AgentOfficeData\exports\cooppilot-postings\` (Pinterest, CIBC, Magna, 1Password,
  Celestica, Interac). Use **Pinterest – Software Engineering Intern** as the main one.
- Privacy: the videos are unlisted but public to anyone with the link. Don't scroll a resume's header (phone, email) into view,
  and never show `application-local.properties`.

## A1 · F01 Experience vault and resume import

1. **Vault**. Say: *"F01: the vault is the agent's long-term memory: every fact it may use about me."*
2. Click **Import backup** with no file → *"Choose a backup .json file first."* (error case).
3. Choose `cooppilot-backup.json` → **Import backup** → *"Backup imported: 65 new vault entries (of 65), 77 resumes."*
   Say: *"This is my real data from my own resume tracker: skills, courses, projects, and 77 resumes I've already made."*
4. Scroll the entries (projects with tech tags, skills, courses). Search `java`.
5. Point at a **context** entry: *"context entries are facts the agent may know but must never put on a resume."*
6. Import the same backup again → *"0 new vault entries"* (duplicates merged: the alternative case).
7. Optional: **Add entry** by hand, one line, **Save entry**.

## A2–A4 · F02 posting, F03 fit, F04 keyword scan

- **Postings** → paste the Pinterest posting → **Add posting** (Analyze ticked). Narrate as the script says.
- **F03**: point at **Fit N/100**, the gaps, and **Recommended**. With 77 saved resumes it can suggest reusing or tweaking one:
  say *"it looked through my library for the closest one; a resume with [placeholders] is never offered."*
- **F04**: the coloured keyword chips; then pick another resume in the dropdown → **Scan** (alternative).

## A5–A6 · F05 tailoring, F06 clarifying questions

- **Tailor with the agent**, narrate the tool calls as the script says. With a real vault the agent has real choices: point
  at `search_vault` returning my entries.
- **F06**: if it asks a question, answer it and say it's saved to the vault. If it doesn't, add a second posting (CIBC or Magna)
  that names a framework I haven't listed, tailor again, answer; then **Vault** → search it → the saved fact. Skipping a
  question (empty answer) is the alternative.

## A7–A12 · F07 one page, F08 revise, F09 letter, F10 library, F11 tracker, F12 dashboard

As the script, on the tailored Pinterest resume. Two things that change with real data:
- **F08** revise: pick a real bullet of mine, e.g. *"make the first project bullet focus on Java"*.
- **F10** library: it's grouped into my 7 resume families; say *"these are my real resumes; any of them downloads as .tex
  for Overleaf."*

## B · CLI

As the script, with my data:
```
$J vault import-backup %USERPROFILE%\AgentOfficeData\exports\cooppilot-backup.json   # "0 new" (already imported in A)
$J vault list -q java
$J posting add "%USERPROFILE%\AgentOfficeData\exports\cooppilot-postings\CIBC-Software-Application-Developer-Co-op.txt" --analyze
```
then `fit`, `scan`, `tailor`, the shell and `letter` exactly as in `demo-script.md`.
