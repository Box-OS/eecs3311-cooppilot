# Stage 2 demo script

Two recordings cover all 12 features, both interfaces, and an error/alternative case for each.
Upload both as **unlisted** YouTube videos, add chapter timestamps, and put the timestamped links in
the report's demonstration index (Section 7) and the README.

The handout requires each segment to: say the feature ID and name, show how it is invoked and the input,
show it running and its output, say where AI is involved, and show an error or alternative case.

## Before recording

1. Start from an empty database so the demo is clean. Stop the app first (Ctrl+C in its terminal: it keeps the file open),
   then in the repo folder, in PowerShell: `Remove-Item cooppilot.db -ErrorAction SilentlyContinue`
   (macOS/Linux: `rm -f cooppilot.db`). The app makes a new, empty one when it starts.
2. `application-local.properties` has `cooppilot.llm.provider=claude-cli`: the AI runs through the Claude Code CLI on my
   Claude subscription, so no API key is needed (`provider=claude` uses the API key instead).
   **Never open this file, your terminal history with a key, or console.anthropic.com on screen.**
3. `.\mvnw.cmd package -DskipTests` (macOS/Linux: `./mvnw`), then `java -jar target\cooppilot-0.1.0.jar` (GUI at
   http://localhost:8080). Keep that terminal open while you record.
4. The demo runs on my real data. Have these ready (they live outside the repo, in `%USERPROFILE%\AgentOfficeData\exports\`,
   because they're personal and never committed):
   - `CV_General.tex`: my real resume, for the `.tex` import
   - `cooppilot-backup-sina.json`: my vault (65 entries) and 77 resumes, from ResumeBase
   - `cooppilot-postings\Pinterest-Software-Engineering-Intern-2027-Toronto.txt`: the main posting; a second one from the
     same folder (CIBC, Magna, 1Password, Celestica, Interac) for A6
   The fictional `samples/` files still work for a demo without personal data.
5. Browser zoom ~110%, close other tabs, turn notifications off. The videos are unlisted, but anyone with the link sees them:
   keep a resume's header (phone, email) off screen.

---

## Recording A — Web GUI (≈ 12–15 min)

Say at the start: *"CoopPilot is an AI agent that tailors one-page LaTeX resumes to co-op postings using
only my real experience. This recording shows every feature in the web GUI."*

### A1 · F01 Experience vault and resume import (deterministic)
1. Click **Vault**. Say: *"F01 — the vault is the agent's long-term memory."*
2. Click **Import** with no file chosen → error *"Choose a .tex file first."* (error case).
3. Choose `CV_General.tex`, Save as `CV_General`, **Import** → "Imported 7 blocks as CV_General; 26 new vault entries", with
   one warning: *Could not read the dates "Demo | GitHub"* for the Reville project. Say: *"when it can't read something it
   warns instead of guessing."*
4. Scroll the entries table (projects with tech tags, skills, courses). Search a skill I have, e.g. `java` or `react`.
5. Add an entry by hand: something real that isn't on the resume (a hackathon, a club, a side project), with a kind, a tag and
   one line of details → **Save entry**.
6. Import the same file again → "0 new vault entries" (duplicates merged). Add `Knows Java` by hand → refused: already in the vault (error case).
7. Click ✎ on an entry, change its details → Save; click a tag chip to filter; × deletes an entry.
8. **Import backup**: choose `cooppilot-backup-sina.json` → "Backup imported: 59 new vault entries (of 65), 77 resumes". Say:
   *"a student can start from an existing vault: six of these were already in it from my resume, so they were merged, not
   duplicated, and my 77 existing resumes are now in the library."*

### A2 · F02 Posting import and analysis (hybrid — AI extracts, code validates)
1. **Postings** → paste the text of the Pinterest posting, leave *Analyze* ticked → **Add posting**.
2. Say: *"F02 — Claude extracts must-haves, ATS keywords and required documents as JSON; my code validates
   it and retries with the problems listed if it is invalid; the soft-skill flag is computed by code."*
3. Point at Requirements: must-haves, nice-to-haves, documents (Cover Letter).
4. Point at the **Deadline** read from the posting.
5. Alternative: open **Edit the analysis**, add a keyword line such as `Kubernetes`, **Save analysis**; mention that the same form lets you fill the analysis in by hand if the AI analysis fails.

### A3 · F03 Fit analysis and strategy (hybrid)
1. On the posting page, point at **Fit N/100**, the gaps, and **Recommended: GENERATE/TWEAK/REUSE**.
2. Say: *"F03 is deterministic: must-have keywords count double against my vault skills."* With 77 resumes in the library,
   **Recommended** can be REUSE or TWEAK of one of mine: say which it picked and why. *"A resume with [placeholders] is never
   offered for reuse."*

### A4 · F04 ATS keyword scan (deterministic)
1. Scroll to **Keyword scan**. Explain the colours: green backed, amber skills-line only, blue dashed
   "you have it but it isn't worded", grey missing.
2. Alternative: pick another resume in the dropdown → **Scan**. Later, on the resume page (A8), the same chips
   appear under *Keywords (F04)* and change after an edit.

### A5 · F05 Agentic tailoring (AI)
1. Click **Tailor with the agent**. Say: *"F05 — this is the agent. Claude plans and calls my tools one at
   a time; my code validates every call and enforces the rules."*
2. As steps stream in, narrate: find_similar_resume → search_vault → compose_resume → keyword_scan →
   estimate_page → (condense) → save_resume. Open one result with ▸ to show what the tool returned.
3. If a step shows ✗ or "REJECTED … numbers", stop and explain: *"the model tried to add a number that isn't
   in my material, so the tool refused it"* (error case). Otherwise point at the rules in the steps.
4. At the end: COMPLETED, step count, **Open CV_…** button. Mention: if a run stops early, the partial draft is
   kept as `CV_…_Draft`.

### A6 · F06 Clarifying questions and vault learning (hybrid)
1. If the agent asks a question during A5, answer it in the box and say: *"F06 — instead of guessing,
   it asks me; the answer is saved to the vault so it never asks again."*
2. If it didn't ask: add the second posting (CIBC or Magna), or any that needs a fact my vault lacks (e.g. mentions a
   framework only one of my projects might use), tailor again, answer the question. Then **Vault** → search the topic → show
   the saved fact line.
3. Alternative: skip a question (empty answer) → the agent leaves the detail out.

### A7 · F07 One-page fitting (hybrid)
1. Open the tailored resume. Scroll the **Preview** (how it will look as a PDF), then point at the **One page** meter.
2. Click **Quick condense** → message "Condensed X% → Y% …" with what was cut. Say: *"rules, free: least
   relevant content first, jobs last."*
3. **Undo** → back to the original (alternative case). Click **Smart condense** → Claude chooses what to cut
   (validated: no new numbers).

### A8 · F08 Revision with undo/redo (hybrid)
1. In **Ask for a change** ask for a change to one of my real bullets, e.g. *"Make the first project bullet focus on Java"*
   → **Revise**.
2. Point at the changed bullet in Blocks and the log line "You asked: …".
3. Point at **What changed** (old text struck through, new text in green).
4. **↶ Undo** → bullet back; **↷ Redo** → reapplied (*"no second AI call"*).
5. In **Blocks**, click ✎ on a bullet, edit it by hand → Save bullet; click × on a project → removed; Undo brings
   it back. Press Undo until "Nothing to undo" (error case).

### A9 · F09 Cover letter (AI)
1. On the resume page, choose the posting → **Write cover letter** → the letter page (`CL_Company_Role`,
   word count). Say: *"F09 — written only from my resume and letter-safe vault facts; code checks length,
   that it names the company, and that it invents no numbers."* Edit a sentence in the text area → **Save letter**,
   then **Download .txt**. (If a draft still fails the checks, the page lists the problems above the letter.)

### A10 · F10 Library, reuse and export (deterministic)
1. **Resumes** → library grouped by family (my 7 resume families); **.tex** download link.
2. On the resume page click **Download .tex** and open the file briefly (ready for Overleaf).
3. Alternative/error: on a posting where the closest resume has placeholders, show that "Use as-is" is not
   offered; on one that fits ≥ 75%, click **Use as-is** → saved as `CV_Company_Role`.

### A11 · F11 Application tracking (deterministic, State pattern)
1. On the tailored resume's page, **Apply (F11) → Log application** (status TO_APPLY); you land on the Tracker.
2. Move to **APPLIED** → applied date + follow-up in 10 days. Move to **ASSESSMENT**, then **INTERVIEW**.
3. Error case: show that the menu only offers legal next statuses; log a second application and note that
   OFFER isn't offered from TO_APPLY (CLI A-B4 shows the refusal message).

### A12 · F12 Dashboard and goals (deterministic, Observer)
1. **Dashboard** → tiles (today/week vs goals, response rate, interviews, follow-ups, streak) and the
   last-7-days chart. Say: *"the dashboard observes the tracker and only recomputes after a change."*
2. Change goals to Daily 1 / Weekly 5 → **Save** → "Goal met" appears and the bar turns green. Try Daily 0 →
   refused (error case). Point at **Follow-ups due** (applications 10+ days old show here).

---

## Recording B — CLI (≈ 5–7 min)

Say: *"Every feature is also available from the command line through the same facade."*
Use bash (Git Bash on Windows) with a large font, in the repo folder. A short `$J` keeps commands readable:
`J="java -jar target/cooppilot-0.1.0.jar --cli"`. If ✓ or → show up as `?`, run `chcp.com 65001` first.

### B1 · Help and F01
```
$J --help
$J vault import "$USERPROFILE/AgentOfficeData/exports/CV_General.tex" --name CV_General      # 0 new: imported in A
$J vault import-backup "$USERPROFILE/AgentOfficeData/exports/cooppilot-backup-sina.json"     # 0 new: same
$J vault list -q java
```

### B2 · F02–F04
```
$J posting add "$USERPROFILE/AgentOfficeData/exports/cooppilot-postings/CIBC-Software-Application-Developer-Co-op.txt" --analyze   # note the posting id
$J fit <postingId>
$J scan <resumeId> <postingId>                           # ids from 'library' and 'posting list'
$J fit <postingId> --json                                # machine-readable output (Stage 3 tests)
```

### B3 · F05–F07 — the agent in the terminal
```
$J tailor <postingId>                                    # answer any '?' question it asks (F06)
$J condense <newResumeId> <postingId> --mode RULES
```

### B4 · F08, F10, F11, F12 in the interactive shell
```
$J shell
cooppilot> revise <resumeId> "make the first project bullet shorter"
cooppilot> undo <resumeId>
cooppilot> redo <resumeId>
cooppilot> undo <resumeId>
cooppilot> undo <resumeId>          # "Nothing to undo." (error case)
cooppilot> library
cooppilot> export <resumeId>        # writes exports/CV_….tex
cooppilot> app add --posting <postingId> --resume CV_General
cooppilot> app status <appId> OFFER # refused: TO_APPLY -> OFFER is illegal (error case)
cooppilot> app status <appId> APPLIED
cooppilot> dashboard
cooppilot> exit
```

### B5 · F09
```
$J letter <postingId> <resumeId>
```

---

## After recording

1. Upload both videos as **Unlisted**; in each description add chapters, e.g.
   `0:00 Intro` · `0:20 F01 Vault` · `1:45 F02 Posting analysis` · …
2. Build timestamped links: `https://youtu.be/<id>?t=105` (seconds).
3. Paste them into `docs/stage2/report/03-ai-verification-demos.md` (Section 7 table) — or send them to
   Claude to fill in — and rebuild the PDF with `python3 docs/stage2/build_report.py`.
4. Open each link in a private window to confirm it plays without signing in.
