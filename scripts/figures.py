"""Figures for the paper, as SVG, in the style of telemetry-triage's figures (no plotting library).

    python scripts/figures.py            reads results/, writes results/figures/
    python scripts/figures.py DIR        reads DIR (e.g. results/archive_6c3c65d), writes DIR/figures/

fig1_pipeline.svg     who decides, who explains, what is checked
fig2_specificity.svg  X1: accepted explanations that quote a measurement, per model and framework
fig3_attribution.svg  X2: the model as attributor, from evidence and from readings, against the detectors

Intervals are percentile bootstrap, 2,000 resamples, fixed seed.
"""
import csv
import json
import random
import re
import statistics
import sys
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ARGS = [a for a in sys.argv[1:] if not a.startswith("--")]
TITLES = "--titles" in sys.argv  # in-figure titles and notes; off for the paper, where the caption carries them
R = Path(ARGS[0]) if ARGS else ROOT / "results"
OUT = R / "figures"
OUT.mkdir(parents=True, exist_ok=True)

# Validated set (dataviz reference palette, slots 1-3, all-pairs CVD dE >= 9.2 on white);
# aqua is below 3:1 on white, so every bar carries a printed value label.
BLUE, ORANGE, AQUA = "#2a78d6", "#eb6834", "#1baf7a"
INK, INK2, GRID, FRAME = "#111111", "#555555", "#eeeeee", "#999999"
FONT = "font-family='Helvetica,Arial,sans-serif' font-size='11'"
NUM = re.compile(r"(?<![\w.])-?\d+(?:\.\d+)?")
SEED, B = 20261018, 2000

PORT_LABEL = {
    "spring-ai/llama3.2": "llama3.2 (3B) · Spring AI",
    "spring-ai/qwen2.5:7b": "qwen2.5 7B · Spring AI",
    "langchain4j/qwen2.5:7b": "qwen2.5 7B · LangChain4j",
    "spring-ai/mistral:7b": "mistral 7B · Spring AI",
    "spring-ai/qwen2.5:32b": "qwen2.5 32B · Spring AI",
}
ORDER = list(PORT_LABEL)


def esc(s):
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def svg(w, h, body):
    return (f"<svg xmlns='http://www.w3.org/2000/svg' width='{w}' height='{h}' viewBox='0 0 {w} {h}' {FONT}>\n"
            f"<rect width='{w}' height='{h}' fill='white'/>\n" + "\n".join(body) + "\n</svg>\n")


def text(x, y, s, anchor="start", size=11, weight="normal", fill=INK, extra=""):
    return (f"<text x='{x:.1f}' y='{y:.1f}' text-anchor='{anchor}' font-size='{size}' font-weight='{weight}' "
            f"fill='{fill}' {extra}>{esc(s)}</text>")


def boot(values, stat=statistics.mean, rnd=None):
    rnd = rnd or random.Random(SEED)
    n = len(values)
    xs = sorted(stat([values[rnd.randrange(n)] for _ in range(n)]) for _ in range(B))
    return xs[int(0.025 * B)], xs[int(0.975 * B) - 1]


def load_incidents():
    inc = {}
    for line in (R / "incidents.jsonl").read_text(encoding="utf-8").splitlines():
        if line.strip():
            d = json.loads(line.replace(":null", ":NaN").replace(",null", ",NaN").replace("[null", "[NaN"))
            inc[d["id"]] = d
    return inc


def specific(textv, i):
    allowed = [float(x) for e in i["evidence"] for x in NUM.findall(e)]
    for tok in NUM.findall(textv):
        dec = len(tok.split(".")[1]) if "." in tok else 0
        if any(abs(float(tok) - a) <= 0.5 * 10 ** -dec + 1e-9 for a in allowed):
            return True
    return False


def hbars(title, axis_label, rows, xmax, fmt, note=None, ref=None):
    """rows: [(label, [(value, lo, hi, color, series_name)])]; one group per row, bars stacked vertically."""
    left, right, top = 200, 40, (64 if TITLES else 40)
    note = note if TITLES else None
    per = max(len(r[1]) for r in rows)
    bar_h, gap_in, gap_out = 13, 2, 14
    group_h = per * bar_h + (per - 1) * gap_in
    plot_h = len(rows) * group_h + (len(rows) - 1) * gap_out + 16
    w, h = 760, top + plot_h + 44 + (18 if note else 0)
    pw = w - left - right
    sx = lambda v: left + pw * v / xmax
    b = [text(left, 22, title, size=14, weight="bold")] if TITLES else []
    series = []
    for _, bars in rows:
        for v in bars:
            if v[4] not in [s[0] for s in series]:
                series.append((v[4], v[3]))
    lx = left
    for name, col in series:  # legend, above the plot
        b.append(f"<rect x='{lx}' y='{top - 28}' width='10' height='10' rx='2' fill='{col}'/>")
        b.append(text(lx + 14, top - 19, name, fill=INK2))
        lx += 24 + 6.2 * len(name)
    b.append(f"<rect x='{left}' y='{top}' width='{pw}' height='{plot_h}' fill='none' stroke='{FRAME}'/>")
    for k in range(0, 6):
        v = xmax * k / 5
        b.append(f"<line x1='{sx(v):.1f}' x2='{sx(v):.1f}' y1='{top}' y2='{top + plot_h}' stroke='{GRID}'/>")
        b.append(text(sx(v), top + plot_h + 14, f"{v:.0f}", anchor="middle", fill=INK2))
    b.append(text(left + pw / 2, top + plot_h + 32, axis_label, anchor="middle", fill=INK2))
    if ref is not None:
        val, lab = ref
        b.append(f"<line x1='{sx(val):.1f}' x2='{sx(val):.1f}' y1='{top}' y2='{top + plot_h}' stroke='{INK}' "
                 f"stroke-width='1.5' stroke-dasharray='4 3'/>")
        b.append(text(sx(val) - 4, top - 6, lab, anchor="end", fill=INK))
    y = top + 8
    for label, bars in rows:
        b.append(text(left - 8, y + group_h / 2 + 4, label, anchor="end"))
        for value, lo, hi, col, _ in bars:
            x0, x1 = sx(0), sx(value)
            b.append(f"<rect x='{x0:.1f}' y='{y:.1f}' width='{max(0.5, x1 - x0):.1f}' height='{bar_h}' rx='2' fill='{col}'/>")
            if lo is not None:
                cy = y + bar_h / 2
                b.append(f"<line x1='{sx(lo):.1f}' x2='{sx(hi):.1f}' y1='{cy:.1f}' y2='{cy:.1f}' stroke='{INK}' stroke-width='1.2'/>")
                for e in (lo, hi):
                    b.append(f"<line x1='{sx(e):.1f}' x2='{sx(e):.1f}' y1='{cy - 3:.1f}' y2='{cy + 3:.1f}' stroke='{INK}' stroke-width='1.2'/>")
            lab_x = sx(hi if hi is not None else value) + 5
            b.append(text(lab_x, y + bar_h - 2.5, fmt(value), size=10, fill=INK))
            y += bar_h + gap_in
        y += gap_out - gap_in
    if note:
        b.append(text(left, h - 10, note, size=10, fill=INK2))
    return svg(w, h, b)


def fig1():
    w, h = 760, 312
    b = [text(20, 22, "Detectors decide, the language model explains, the rules admit", size=14, weight="bold")] if TITLES else []

    def box(x, y, bw, bh, title, lines, stroke=FRAME, fill="white"):
        out = [f"<rect x='{x}' y='{y}' width='{bw}' height='{bh}' rx='6' fill='{fill}' stroke='{stroke}' stroke-width='1.2'/>",
               text(x + bw / 2, y + 18, title, anchor="middle", weight="bold")]
        for k, l in enumerate(lines):
            out.append(text(x + bw / 2, y + 36 + 14 * k, l, anchor="middle", size=10, fill=INK2))
        return out

    def arrow(x1, y1, x2, y2, label=None, dash=False):
        d = " stroke-dasharray='4 3'" if dash else ""
        out = [f"<line x1='{x1}' y1='{y1}' x2='{x2}' y2='{y2}' stroke='{INK}' stroke-width='1.2'{d} marker-end='url(#a)'/>"]
        if label:
            out.append(text((x1 + x2) / 2, min(y1, y2) - 6, label, anchor="middle", size=10, fill=INK2))
        return out

    b.append("<defs><marker id='a' viewBox='0 0 10 10' refX='9' refY='5' markerWidth='7' markerHeight='7' orient='auto'>"
             f"<path d='M0 0 L10 5 L0 10 z' fill='{INK}'/></marker></defs>")
    b += box(20, 60, 140, 76, "Readings", ["one channel,", "streaming"])
    b += box(196, 60, 160, 76, "Detectors [1]", ["decide the cause:", "process · instrument · data path"], stroke=BLUE)
    b += box(392, 60, 150, 76, "Facts", ["decided cause, evidence,", "times, permitted checks"])
    b += box(578, 60, 162, 76, "Local language model", ["writes explanation", "and next check"], stroke=ORANGE)
    b += box(578, 190, 162, 76, "Rules R1–R6", ["format · cause · numbers ·", "check · contradiction · length"], stroke=AQUA)
    b += box(196, 190, 160, 76, "Template", ["decided cause, evidence,", "first permitted check"])
    b += box(392, 190, 150, 76, "Operator", ["explanation + check;", "the cause never changes"])
    b += arrow(160, 98, 194, 98)
    b += arrow(356, 98, 390, 98)
    b += arrow(542, 98, 576, 98)
    b += arrow(659, 136, 659, 188)
    b += arrow(578, 222, 544, 222, "pass")
    b += arrow(620, 266, 620, 284, None)
    b.append(f"<path d='M620 284 L276 284 L276 268' fill='none' stroke='{INK}' stroke-width='1.2' stroke-dasharray='4 3' marker-end='url(#a)'/>")
    b.append(text(448, 280, "fail, or model call fails", anchor="middle", size=10, fill=INK2))
    b += arrow(356, 228, 390, 228)
    if not TITLES:
        return svg(w, h - 44, ["<g transform='translate(0,-44)'>"] + b + ["</g>"])
    return svg(w, h, b)


def main():
    inc = load_incidents()
    x1 = list(csv.DictReader(open(R / "X1_explanations.csv", encoding="utf-8")))
    by = defaultdict(list)
    for r in x1:
        by[r["port"]].append(r)
    ports = [p for p in ORDER if p in by] + [p for p in by if p not in ORDER]

    (OUT / "fig1_pipeline.svg").write_text(fig1(), encoding="utf-8")

    rows = []
    for p in ports:
        acc = [r for r in by[p] if r["source"] == "model"]
        s = [1.0 if specific(r["final_text"], inc[r["id"]]) else 0.0 for r in acc]
        lo, hi = boot(s)
        rows.append((PORT_LABEL.get(p, p), [(100 * statistics.mean(s), 100 * lo, 100 * hi, BLUE, "quotes a measurement from the evidence")]))
    n_acc = sum(r["source"] == "model" for r in x1)
    (OUT / "fig2_specificity.svg").write_text(hbars(
        "Accepted explanations that quote a measurement (X1)", "% of accepted explanations", rows, 100,
        lambda v: f"{v:.0f} %", note=f"{n_acc:,} of {len(x1):,} explanations accepted by the rules; whiskers: 95 % bootstrap interval."),
        encoding="utf-8")

    p2 = R / "X2_attribution.csv"
    if p2.exists():
        x2 = list(csv.DictReader(open(p2, encoding="utf-8")))
        cells = defaultdict(list)
        for r in x2:
            cells[(r["port"], r["input"])].append(1.0 if r["model_cause"] == r["truth"] else 0.0)
        ids = sorted({r["id"] for r in x2})
        det = 100 * statistics.mean(1.0 if inc[i]["cause"] == inc[i]["truth"] else 0.0 for i in ids)
        rows = []
        for p in [q for q in ORDER if any(k[0] == q for k in cells)]:
            bars = []
            for inp, col, name in (("EVIDENCE", BLUE, "model, given the detectors' evidence"),
                                   ("READINGS", ORANGE, "model, given the raw readings")):
                v = cells.get((p, inp))
                if v:
                    lo, hi = boot(v)
                    bars.append((100 * statistics.mean(v), 100 * lo, 100 * hi, col, name))
            rows.append((PORT_LABEL.get(p, p).split(" · ")[0], bars))
        (OUT / "fig3_attribution.svg").write_text(hbars(
            "The language model as attributor (X2)", "% of incidents attributed to the injected cause", rows, 100,
            lambda v: f"{v:.0f} %", ref=(det, f"detectors {det:.1f} %"),
            note=f"{len(ids)} incidents; whiskers: 95 % bootstrap interval."), encoding="utf-8")

    print("figures written:", ", ".join(sorted(f.name for f in OUT.glob("*.svg"))))


main()
