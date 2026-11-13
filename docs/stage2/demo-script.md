# Stage 2 demo script

Two recordings cover all 12 features, both interfaces, and an error/alternative case for each.
Upload both as **unlisted** YouTube videos, add chapter timestamps, and put the timestamped links in
the report's demonstration index (Section 7) and the README.

The handout requires each segment to: say the feature ID and name, show how it is invoked and the input,
show it running and its output, say where AI is involved, and show an error or alternative case.

## Before recording

1. In the repo folder, start from an empty database so the demo is clean: `rm -f cooppilot.db`.
2. `application-local.properties` has your key and `cooppilot.llm.provider=claude`.
   **Never open this file, your terminal history with the key, or console.anthropic.com on screen.**
3. `mvn package -DskipTests` (for the CLI), then `mvn spring-boot:run` (GUI at http://localhost:8080).
4. Have `samples/sample-resume.tex` and `samples/sample-posting.txt` ready (both fictional, safe to show).
   For a second posting, copy any real co-op posting text.
5. Browser zoom ~110%, close other tabs, turn notifications off.

---

## Recording A — Web GUI (≈ 12–15 min)

Say at the start: *"CoopPilot is an AI agent that tailors one-page LaTeX resumes to co-op postings using
only my real experience. This recording shows every feature in the web GUI."*

### A1 · F01 Experience vault and resume import (deterministic)
1. Click **Vault**. Say: *"F01 — the vault is the agent's long-term memory."*
2. Click **Import** with no file chosen → error *"Choose a .tex file first."* (error case).
3. Choose `samples/sample-resume.tex`, Save as `CV_General`, **Import** → "Imported 6 blocks … N new vault entries".
4. Scroll the entries table (jobs, projects with tech tags, skills). Search `tutor`.
5. Add an entry by hand: Title *"Hackathon — bus arrival bot"*, Kind ACTIVITY, Tags *python*, Details one line → **Save entry**.
6. Import the same file again → "0 new vault entries" (duplicates merged). Add `Knows Java` by hand → refused: already in the vault (error case).
7. Click ✎ on an entry, change its details → Save; click a tag chip to filter; × deletes an entry.
8. **Import backup**: choose your `cooppilot-backup-sina.json` (or `samples/sample-backup.json`) → "N new vault entries, M resumes". Say: *"a student can start from an existing vault."* (If you use your real data, the rest of the demo runs on it.)

### A2 · F02 Posting import and analysis (hybrid — AI extracts, code validates)
1. **Postings** → paste the text of `sample-posting.txt`, leave *Analyze* ticked → **Add posting**.
2. Say: *"F02 — Claude extracts must-haves, ATS keywords and required documents as JSON; my code validates
   it and retries with the problems listed if it is invalid; the soft-skill flag is computed by code."*
3. Point at Requirements: must-haves, nice-to-haves, documents (Cover Letter).
4. Point at the **Deadline** read from the posting.
5. Alternative: open **Edit the analysis**, add a keyword line such as `Kubernetes`, **Save analysis**; mention that the same form lets you fill the analysis in by hand if the AI analysis fails.

### A3 · F03 Fit analysis and strategy (hybrid)
1. On the posting page, point at **Fit N/100**, the gaps, and **Recommended: GENERATE/TWEAK/REUSE**.
2. Say: *"F03 is deterministic: must-have keywords count double against my vault skills. The closest saved
   resume is never one with [placeholders] — my sample has one, so it isn't offered for reuse."*

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
2. If it didn't ask: add a posting that needs a fact your vault lacks (e.g. mentions a framework only one of
   your projects might use), tailor again, answer the question. Then **Vault** → search the topic → show
   the saved fact line.
3. Alternative: skip a question (empty answer) → the agent leaves the detail out.

### A7 · F07 One-page fitting (hybrid)
1. Open the tailored resume. Scroll the **Preview** (how it will look as a PDF), then point at the **One page** meter.
2. Click **Quick condense** → message "Condensed X% → Y% …" with what was cut. Say: *"rules, free: least
   relevant content first, jobs last."*
3. **Undo** → back to the original (alternative case). Click **Smart condense** → Claude chooses what to cut
   (validated: no new numbers).

### A8 · F08 Revision with undo/redo (hybrid)
1. In **Ask for a change** type *"Make the tutoring bullet focus on Java"* → **Revise**.
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
1. **Resumes** → library grouped by family; **.tex** download link.
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
Use a terminal with a large font. `J="java -jar target/cooppilot-0.1.0.jar --cli"` keeps commands short.

### B1 · Help and F01
```
$J --help
$J vault import samples/sample-resume.tex --name CV_General
$J vault list -q java
```

### B2 · F02–F04
```
$J posting add samples/sample-posting.txt --analyze      # note the posting id
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
cooppilot> revise <resumeId> "make the cashier bullet shorter"
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
