# Recording with my own data

Follow `demo-script.md` step by step; this file only lists what to swap in so every screen shows my real data instead of the
fictional samples.

## Files (outside the repo: personal, never committed)

| Instead of | Use |
|---|---|
| `samples/sample-resume.tex` | `%USERPROFILE%\AgentOfficeData\exports\CV_General.tex` (my real CV_General) |
| `cooppilot-backup-sina.json` | `%USERPROFILE%\AgentOfficeData\exports\cooppilot-backup-sina.json` (65 vault entries, 77 resumes, from ResumeBase) |
| `samples/sample-posting.txt` | `%USERPROFILE%\AgentOfficeData\exports\cooppilot-postings\Pinterest-Software-Engineering-Intern-2027-Toronto.txt` |
| "a second posting" | `...\cooppilot-postings\CIBC-Software-Application-Developer-Co-op.txt` (or Magna, 1Password, Celestica, Interac) |

Start on an empty `cooppilot.db` (the fictional one is kept as `cooppilot-demo-fictional.db`). Keep resume headers (phone,
email) off screen: the videos are unlisted, but anyone with the link sees them.

## What to expect and say where it differs

- **A1 step 3**: *"Imported 7 blocks as CV_General; 26 new vault entries"*, with one warning: *Could not read the dates
  "Demo | GitHub"* for the Reville project. Say: *"when it can't read something it warns instead of guessing."*
- **A1 step 4**: search a skill I really have, e.g. `java` or `react`, instead of `tutor`.
- **A1 step 5**: add something real that isn't on the resume (a hackathon, a club, a side project).
- **A1 step 8**: *"Backup imported: 59 new vault entries (of 65), 77 resumes."* Say: *"six of these were already in the vault
  from my resume, so they were merged, not duplicated, and now my 77 existing resumes are in the library."*
- **A3**: with 77 resumes in the library, **Recommended** can be REUSE or TWEAK of one of mine; say which one it picked and why.
- **A6**: if the agent doesn't ask a question on Pinterest, use the CIBC posting for the second run.
- **A8**: revise one of my real bullets, e.g. *"make the first project bullet focus on Java"*.
- **A10**: the library is grouped into my 7 resume families.
- **B**: `$J vault import "%USERPROFILE%\AgentOfficeData\exports\CV_General.tex" --name CV_General` (shows 0 new: already
  imported in A), `$J vault import-backup "%USERPROFILE%\AgentOfficeData\exports\cooppilot-backup-sina.json"`, and
  `$J posting add "%USERPROFILE%\AgentOfficeData\exports\cooppilot-postings\CIBC-Software-Application-Developer-Co-op.txt" --analyze`.
