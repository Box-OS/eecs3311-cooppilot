# eecs3311-cooppilot
How to run: `.\mvnw.cmd package -DskipTests`, then `java -jar target\cooppilot-0.1.0.jar` → http://localhost:8080
  (CLI: `java -jar target\cooppilot-0.1.0.jar --cli --help`; key in application-local.properties, git-ignored)

## Left
- [ ] Record video A (web GUI, F01–F12) with my own data: docs/stage2/demo-script.md
- [ ] Record video B (CLI)
- [ ] Upload both unlisted, add chapters, put the timestamped links in docs/stage2/report/03-ai-verification-demos.md
      (Section 7) and the README, rebuild the PDF (`python3 docs/stage2/build_report.py`)
- [ ] Stage 3: testing (JUnit + KUMA)

## Done
- [x] 2026-11-14 UI touch-up before recording: one shared stylesheet (fragments.html): lit nav tab and brand mark, card
      shadows and hover, even grid spacing, styled links, button/input hover and focus states
- [x] 2026-11-14 F01–F05 checked in the app; recording set up with real data (CV_General.tex, backup and postings from
      ResumeBase), rehearsed F01's imports; pulled the 5 GitHub commits (vault/letter editing, deadlines) and rebuilt
- [x] 2026-11-12 F01 backup import (GUI + CLI `vault import-backup`)

## Decisions
- Videos use my real data, not the fictional samples: the demo data was too thin to show the features. Keep resume headers
  (phone, email) off screen.
- The real backup and postings live outside the repo (%USERPROFILE%\AgentOfficeData\exports): personal, never committed.
- The fictional demo database is kept as cooppilot-demo-fictional.db (git-ignored, like every *.db).
