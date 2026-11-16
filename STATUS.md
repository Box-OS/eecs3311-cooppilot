# eecs3311-cooppilot
How to run: `.\mvnw.cmd package -DskipTests`, then `java -jar target\cooppilot-0.1.0.jar` → http://localhost:8080
  (CLI: `java -jar target\cooppilot-0.1.0.jar --cli --help`; AI settings in application-local.properties, git-ignored)

## Left
- [ ] Record video A (web GUI, F01–F12) with my own data: docs/stage2/demo-script.md
- [ ] Record video B (CLI)
- [ ] Upload both unlisted, add chapters, put the timestamped links in docs/stage2/report/03-ai-verification-demos.md
      (Section 7) and the README, rebuild the PDF (`python3 docs/stage2/build_report.py`)
- [ ] Stage 3: testing (JUnit + KUMA)

## Done
- [x] 2026-11-14 claude-cli provider (ClaudeCliAdapter): the AI runs through `claude -p` on a Claude subscription, no API
      key; checked F02 analysis (~8 s) and a full F05 tailor run (13 steps, saved) on my real data
- [x] 2026-11-14 UI touch-up before recording: one shared stylesheet (fragments.html): lit nav tab and brand mark, card
      shadows and hover, even grid spacing, styled links, button/input hover and focus states
- [x] 2026-11-14 F01–F05 checked in the app; recording set up with real data (CV_General.tex, backup and postings from
      ResumeBase), rehearsed F01's imports; pulled the 5 GitHub commits (vault/letter editing, deadlines) and rebuilt
- [x] 2026-11-12 F01 backup import (GUI + CLI `vault import-backup`)

## Decisions
- Resume page has Add entry (an entry from another saved resume, undoable): every real resume fits on one page (max 98%),
  so F07 had nothing to condense. One added entry takes the Pinterest resume to 116%; Quick condense brings it to 91%.
- F06 asking is enforced by code: save_resume refuses until the agent has asked about up to 2 must-haves with no evidence
  (soft skills and broad ones like "software development" skipped). Left to the model it never asked with my full vault
  (Haiku decided asking wasn't allowed). A "no" goes into one context-only "Not used" entry, so it never counts as the skill.
- F03 closest-resume match counts every posting keyword (must-haves x2), not only those in the vault: the old way gave the
  resume the vault came from 100% on every posting, so everything said REUSE even at 5/100 fit. With real data nothing
  reaches 75% now (highest ~60%), so Pinterest shows TWEAK; Use as-is is still offered for the closest resume.
- The API key stopped working (Anthropic returns 401), so the videos use provider=claude-cli on the board's priority
  account (Claude Marco). Thinking is off (MAX_THINKING_TOKENS=0): with it on, one analysis took 53 s instead of 8 s.
- Videos use my real data, not the fictional samples: the demo data was too thin to show the features. Keep resume headers
  (phone, email) off screen.
- The real backup and postings live outside the repo (%USERPROFILE%\AgentOfficeData\exports): personal, never committed.
- The fictional demo database is kept as cooppilot-demo-fictional.db (git-ignored, like every *.db).
