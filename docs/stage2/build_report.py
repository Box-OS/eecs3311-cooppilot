#!/usr/bin/env python3
"""Builds stage2-report.md and stage2-report.pdf at the repository root from docs/stage2/report/*.md.

- {{COMMITS}} is replaced by the weekly commit summary, generated from `git log` with GitHub links
  (so the links stay correct even if the history is rewritten).
- Diagram images that have not been exported from UMLet yet are replaced by a visible note.
- {{TODO: ...}} markers are highlighted so nothing unfinished goes unnoticed.

Requires git, pandoc and wkhtmltopdf. Run from anywhere: python3 docs/stage2/build_report.py
"""
import os
import re
import subprocess
import sys
from datetime import date

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
PARTS = os.path.join(ROOT, "docs", "stage2", "report")
REPO_URL = "https://github.com/Box-OS/eecs3311-cooppilot"
PERIODS = [
    ("Period 1", "2026-10-20", "2026-10-26"),
    ("Period 2", "2026-10-27", "2026-11-02"),
    ("Period 3", "2026-11-03", "2026-11-09"),
]
CSS = """
body { font-family: 'DejaVu Sans', Arial, sans-serif; font-size: 10.5pt; line-height: 1.45; color: #1d2433; margin: 0 6px; }
h1 { font-size: 17pt; border-bottom: 2px solid #1d2433; padding-bottom: 3px; margin-top: 26px; page-break-after: avoid; }
h2 { font-size: 13pt; margin-top: 18px; page-break-after: avoid; }
header h1.title { border: none; font-size: 22pt; margin-bottom: 2px; }
header p.subtitle { font-size: 12pt; margin: 0; color: #344054; }
header p.author { color: #667085; margin-top: 4px; }
table { border-collapse: collapse; width: 100%; margin: 8px 0 12px; font-size: 8.8pt; page-break-inside: auto; }
tr { page-break-inside: avoid; }
th, td { border: 1px solid #d0d5dd; padding: 4px 6px; vertical-align: top; text-align: left; }
th { background: #f2f4f7; }
code { font-family: 'DejaVu Sans Mono', monospace; font-size: 8.6pt; background: #f2f4f7; padding: 0 2px; }
img { max-width: 100%; border: 1px solid #e4e7ec; margin: 6px 0; }
.todo { background: #fff3a3; color: #7a4d00; font-weight: bold; padding: 0 2px; }
.missing { background: #fef3f2; color: #b42318; padding: 8px; border: 1px dashed #b42318; }
nav#TOC { font-size: 9.5pt; } nav#TOC ul { list-style: none; padding-left: 14px; }
"""


def git(*args):
    return subprocess.run(["git", "-C", ROOT, *args], check=True, capture_output=True, text=True).stdout


def commit_summary():
    log = git("log", "origin/main" if "origin/main" in git("branch", "-r") else "HEAD",
              "--reverse", "--date=format:%Y-%m-%d %H:%M", "--format=%H|%h|%ad|%s")
    commits = [line.split("|", 3) for line in log.strip().splitlines()]
    out = []
    for name, start, end in PERIODS:
        rows = [c for c in commits if start <= c[2][:10] <= end]
        out.append(f"**{name} ({start} to {end}): {len(rows)} code commits**\n")
        out.append("| Commit | Date (Toronto) | Description |\n|---|---|---|")
        for full, short, when, subject in rows:
            out.append(f"| [`{short}`]({REPO_URL}/commit/{full}) | {when} | {subject.replace('|', '/')} |")
        out.append("")
    later = [c for c in commits if c[2][:10] > PERIODS[-1][2]]
    if later:
        out.append(f"**After the reporting periods:** " + "; ".join(
            f"[`{c[1]}`]({REPO_URL}/commit/{c[0]}) {c[3]}" for c in later) + "\n")
    return "\n".join(out)


def main():
    parts = sorted(f for f in os.listdir(PARTS) if f.endswith(".md"))
    text = "\n\n".join(open(os.path.join(PARTS, f), encoding="utf-8").read() for f in parts)
    text = text.replace("{{COMMITS}}", commit_summary())

    def image(m):
        alt, path = m.group(1), m.group(2)
        if os.path.exists(os.path.join(ROOT, path)):
            return m.group(0)
        return (f'<div class="missing">{alt}: export <code>{path[:-4]}.uxf</code> from UMLet '
                f'(File → Export as… → PNG) to <code>{path}</code>, then rebuild.</div>')
    text = re.sub(r"!\[([^\]]*)\]\(([^)]+\.png)\)", image, text)

    md_out = os.path.join(ROOT, "stage2-report.md")
    open(md_out, "w", encoding="utf-8").write(text)
    todos = re.findall(r"\{\{TODO[^}]*\}\}", text)

    html_text = re.sub(r"\{\{(TODO[^}]*)\}\}", r'<span class="todo">[\1]</span>', text)
    # the HTML is written to target/, so point images at absolute paths for wkhtmltopdf
    html_text = re.sub(r"!\[([^\]]*)\]\((docs/[^)]+\.png)\)",
                       lambda m: f"![{m.group(1)}]({os.path.join(ROOT, m.group(2))})", html_text)
    html_text += f"\n\n---\n*Generated {date.today().isoformat()} from `docs/stage2/report/`.*\n"
    tmp_md = os.path.join(ROOT, "target", "stage2-report.tmp.md")
    os.makedirs(os.path.dirname(tmp_md), exist_ok=True)
    open(tmp_md, "w", encoding="utf-8").write(html_text)
    css_path = os.path.join(ROOT, "target", "report.css")
    open(css_path, "w").write(CSS)
    html_out = os.path.join(ROOT, "target", "stage2-report.html")
    subprocess.run(["pandoc", "-f", "markdown-tex_math_dollars", tmp_md, "-s", "--toc", "--toc-depth=2", "-c", css_path,
                    "--metadata", "lang=en", "-o", html_out], check=True, cwd=ROOT)
    pdf_out = os.path.join(ROOT, "stage2-report.pdf")
    subprocess.run(["wkhtmltopdf", "--quiet", "--enable-local-file-access", "--page-size", "Letter",
                    "--margin-top", "16mm", "--margin-bottom", "16mm", "--margin-left", "14mm",
                    "--margin-right", "14mm", "--footer-center", "CoopPilot · Stage 2 report · [page]/[topage]",
                    "--footer-font-size", "8", html_out, pdf_out], check=False, cwd=ROOT)
    print(f"wrote {md_out}\nwrote {pdf_out}\n{len(todos)} TODO markers left")
    for t in todos:
        print("  ", t[:110])
    return 0


if __name__ == "__main__":
    sys.exit(main())
