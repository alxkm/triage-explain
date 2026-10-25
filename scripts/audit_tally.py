"""Tallies the author's rating of 50 accepted explanations (results/audit_sheet.csv) into results/audit_summary.md."""
import csv
from collections import defaultdict
from pathlib import Path

R = Path(__file__).resolve().parent.parent / "results"
rows = list(csv.DictReader(open(R / "audit_sheet.csv", encoding="utf-8-sig")))
by = defaultdict(list)
for r in rows:
    by[r["port"]].append(r)


def count(g, col):
    return sum(r[col].strip().lower() == "y" for r in g)


F, U = "faithful (y/n)", "unsupported claim (y/n)"
md = ["# Author's rating of accepted explanations (X1)", "",
      "Sample: 50 explanations accepted by rules R1-R6, 10 per model and framework, drawn with seed 20261006 by",
      "`scripts/summarize.py`. Rated by the author, one rater; notes in `audit_sheet.csv` are the rater's (row numbers in them count data rows, header excluded).", "",
      "| Model / framework | Rated | Faithful | Unsupported claim |", "|---|---|---|---|"]
for port, g in sorted(by.items()):
    md.append(f"| {port} | {len(g)} | {count(g, F)} | {count(g, U)} |")
md.append(f"| **total** | {len(rows)} | {count(rows, F)} | {count(rows, U)} |")
md += ["", "## Notes, grouped", "",
       "- **Unfaithful (4, all qwen2.5:7b, two per framework):** three describe evidence of noise growth as a bias",
       "  (one adds \"drift or stuck value\"); one describes an abrupt step above the plausible rate as \"bias or drift\".",
       "- **Unsupported but faithful (1, qwen2.5:32b):** lists every instrument mechanism for a single abrupt step.",
       "- **Generic (8 of 10 llama3.2):** restates the definition of the cause without using the evidence.",
       "- **Narrowed (7):** data-path explanations name one kind of corruption where the evidence lists three alternatives.",
       "- **Prejudged (1, qwen2.5:32b):** calls an undetermined decision a sensor issue.",
       "- **Misattributed number (2, qwen2.5:7b via LangChain4j):** quotes the 40 s between first trigger and decision",
       "  as the duration of a stuck value; the prompt states it as the decision delay (`Facts.java`).", ""]
(R / "audit_summary.md").write_text("\n".join(md), encoding="utf-8")
print("\n".join(md))
