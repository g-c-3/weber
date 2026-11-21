"""Docs consistency (including NOTES, TRACE and CRASHLOG, which the app's saved files name on their first line): numbering, TRACK against SESSIONS, references, INDEX ranges, banned words, secrets, e-mail addresses, dates."""
import re
from .common import Finding

NAME = "docs"
MAIN = ["INDEX.md", "ROADMAP.md", "DECISIONS.md", "SESSIONS.md", "TRACK.md", "ARCHITECTURE.md", "NOTES.md", "TRACE.md", "CRASHLOG.md"]
SIZE_LIMIT = 60 * 1024
INDEX_LIMIT = 5 * 1024
# built from parts so this file does not hold the banned words itself
LABEL = "A" + "I"
NAMES = "|".join(["Cl" + "aude", "Anthro" + "pic", "Chat" + "GPT", "GP" + "T-?\\d*", "Open" + "A" + "I", "Gem" + "ini", "Copi" + "lot", "Son" + "net", "O" + "pus", "Hai" + "ku"])
BANNED = [(r"\b" + LABEL + r"\b", "the banned label word"), (r"\b(" + NAMES + r")\b", "an assistant or model name")]
SECRET = r"(github_p" + "at_[A-Za-z0-9_]{20,}|ghp_[A-Za-z0-9]{20,}|AKIA[0-9A-Z]{12,}|-----BEGIN [A-Z ]*PRIVATE KEY|sk-[A-Za-z0-9]{20,})"
EMAIL = r"[\w.+-]+@[\w-]+(?:\.[\w-]+)+"
MON = r"(?:Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|June?|July?|Aug(?:ust)?|Sep(?:t(?:ember)?)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)"
DATES = [r"\b(?:19|20)\d\d-\d\d-\d\d\b", r"\b" + MON + r"\.? \d{1,2}\b", r"\b\d{1,2} " + MON + r"\b", r"\b\d{1,2}:\d{2} ?(?:AM|PM|am|pm)\b", r"\b\d{1,2}/\d{1,2}/(?:19|20)\d\d\b"]
LIST = r"((?:\s*(?:,|and|to|-)\s*\d+)*)"


def numbers(first, rest):
    return [int(first)] + [int(x) for x in re.findall(r"\d+", rest or "")]


def run(docs=None):
    if not docs:
        return [Finding("info", NAME, "-", "no docs folder given (--docs): the docs were not checked")]
    out = []

    def err(where, msg):
        out.append(Finding("error", NAME, where, msg))

    def warn(where, msg):
        out.append(Finding("warn", NAME, where, msg))

    text, lines = {}, {}
    for name in MAIN:
        p = docs / name
        if not p.exists():
            err(name, "missing")
            continue
        text[name] = p.read_text(encoding="utf-8")
    archive = {p.name: p.read_text(encoding="utf-8") for p in sorted((docs / "archive").glob("*.md"))} if (docs / "archive").exists() else {}
    allfiles = {**text, **{"archive/" + k: v for k, v in archive.items()}}
    for name, t in allfiles.items():
        lines[name] = t.split("\n")

    # numbering
    main_dec = [int(m.group(1)) for m in re.finditer(r"^## (\d+)\.", text.get("DECISIONS.md", ""), re.M)]
    arch_dec = [int(m.group(1)) for k, v in archive.items() if k.startswith("DECISIONS") for m in re.finditer(r"^## (\d+)\.", v, re.M)]
    known = set(main_dec) | set(arch_dec)
    maxdec = max(known) if known else 0
    for a, b in zip(main_dec, main_dec[1:]):
        if b <= a:
            err("DECISIONS.md", f"decision {b} follows {a}")
    for n in sorted(set(main_dec) & set(arch_dec)):
        err("DECISIONS.md", f"decision {n} is in the main file and in the archive")
    missing = [n for n in range(1, maxdec + 1) if n not in known]
    if missing:
        warn("DECISIONS.md", f"no decision with number {', '.join(map(str, missing))} (main file and archive)")
    ses = [int(m.group(1)) for m in re.finditer(r"^## (\d+)\s*$", text.get("SESSIONS.md", ""), re.M)]
    for a, b in zip(ses, ses[1:]):
        if b != a - 1:
            warn("SESSIONS.md", f"session {b} follows {a} (newest first, no gaps)")
    maxses = ses[0] if ses else 0

    # TRACK against SESSIONS
    rows = []
    for n, l in enumerate(lines.get("TRACK.md", []), 1):
        m = re.match(r"\|\s*(\d+)(?: \((?:cont\.|end)\))?\s*\|([^|]*)\|[^|]*\|\s*(\d{4}-\d\d-\d\d)\s*\|", l)
        if m:
            rows.append((int(m.group(1)), m.group(3), n, m.group(2)))
    if rows:
        if rows[-1][0] != maxses:
            err("TRACK.md", f"last row is session {rows[-1][0]}, SESSIONS.md starts at {maxses}")
        dates = {}
        for s, d, n, _ in rows:
            dates.setdefault(s, set()).add(d)
        for a, b in zip(rows, rows[1:]):
            if b[0] < a[0]:
                err(f"TRACK.md:{b[2]}", f"session {b[0]} follows {a[0]}")
            if b[1] < a[1]:
                err(f"TRACK.md:{b[2]}", f"date {b[1]} is earlier than the row above")
        for s in sorted(set(r[0] for r in rows)):
            if s > maxses:
                err("TRACK.md", f"session {s} is not in SESSIONS.md")
        for s in range(1, maxses + 1):
            if s not in dates:
                warn("TRACK.md", f"session {s} has no row")
        for s, d, n, decs in rows:
            for m in re.finditer(r"\d+", decs):
                if int(m.group(0)) > maxdec:
                    err(f"TRACK.md:{n}", f"decision {m.group(0)} does not exist")

    # references to decisions and sessions
    for name in ("INDEX.md", "ROADMAP.md", "SESSIONS.md", "ARCHITECTURE.md", "DECISIONS.md", "NOTES.md", "TRACE.md", "CRASHLOG.md"):
        for n, l in enumerate(lines.get(name, []), 1):
            for m in re.finditer(r"(?:\b[Dd]ecisions?|DECISIONS\.md) (\d+)" + LIST, l):
                for k in numbers(m.group(1), m.group(2)):
                    if k not in known:
                        err(f"{name}:{n}", f"decision {k} does not exist")
            for m in re.finditer(r"\b[Ss]essions? (\d+)" + LIST, l):
                for k in numbers(m.group(1), m.group(2)):
                    if k > maxses:
                        err(f"{name}:{n}", f"session {k} does not exist (last is {maxses})")

    # INDEX against the real files
    idx = lines.get("INDEX.md", [])
    for n, l in enumerate(idx, 1):
        m = re.match(r"\|\s*(\d+(?:\s*,\s*\d+)*)\s*\|[^|]*\|\s*DECISIONS\.md\s*\|", l)
        if m:
            for k in re.findall(r"\d+", m.group(1)):
                if int(k) not in known:
                    err(f"INDEX.md:{n}", f"standing decision {k} does not exist")
        m = re.match(r"\|\s*(DECISIONS|SESSIONS|TRACK)\.md\s*\|[^|]*\|\s*(?:Sessions )?(\d+)-(\d+)\s*\|([^|]*)\|", l)
        if m:
            real = {"DECISIONS": (min(known) if known else 0, maxdec), "SESSIONS": (min(ses) if ses else 0, maxses),
                    "TRACK": (min((r[0] for r in rows), default=0), max((r[0] for r in rows), default=0))}[m.group(1)]
            if (int(m.group(2)), int(m.group(3))) != real:
                err(f"INDEX.md:{n}", f"{m.group(1)}.md range reads {m.group(2)}-{m.group(3)}, the file holds {real[0]}-{real[1]}")
            for f in re.findall(r"archive/[\w.-]+\.md", m.group(4)):
                if f.split("/")[1] not in archive:
                    err(f"INDEX.md:{n}", f"{f} does not exist")
    if len(text.get("INDEX.md", "").encode()) > INDEX_LIMIT:
        warn("INDEX.md", f"over {INDEX_LIMIT // 1024} KB")

    # ROADMAP: checklist against the detail
    road = lines.get("ROADMAP.md", [])
    top = {int(m.group(2)): m.group(1) for l in road for m in [re.match(r"- \[( |x)\] (\d+) ", l)] if m}
    det = {int(m.group(1)): m.group(2) for l in road for m in [re.match(r"### (\d+) .*?(?:\((\w+)\))?$", l)] if m}
    for k, mark in sorted(top.items()):
        if k not in det:
            err("ROADMAP.md", f"phase {k} is in the checklist and has no detailed entry")
        elif mark == "x" and det[k] not in (None, "done"):
            warn("ROADMAP.md", f"phase {k} is checked and its heading says ({det[k]})")
        elif mark == " " and det[k] == "done":
            err("ROADMAP.md", f"phase {k} is unchecked and its heading says (done)")
    for k in det:
        if k not in top:
            err("ROADMAP.md", f"phase {k} has a detailed entry and is not in the checklist")

    # conventions in every doc
    for name, ls in lines.items():
        for n, l in enumerate(ls, 1):
            for rx, what in BANNED:
                if re.search(rx, l):
                    err(f"{name}:{n}", what)
            if re.search(SECRET, l):
                err(f"{name}:{n}", "a secret")
            if re.search(EMAIL, l):
                err(f"{name}:{n}", "an e-mail address (decision 98)")
            if name in ("DECISIONS.md", "SESSIONS.md", "NOTES.md") or name.startswith("archive/DECISIONS") or name.startswith("archive/SESSIONS"):
                if any(re.search(rx, l) for rx in DATES):
                    err(f"{name}:{n}", "a date or time reference")
    for name, t in text.items():
        size = len(t.encode())
        if name != "INDEX.md" and size > SIZE_LIMIT:
            warn(name, f"{size // 1024} KB: propose the archive")
    return out
