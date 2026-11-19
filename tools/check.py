#!/usr/bin/env python3
"""Runs the repository checks. Usage: python3 tools/check.py [--docs PATH] [--only NAME] [--apk-budget BYTES] [--strict] [--github]
Exit status 1 when any error is found (or any warning with --strict). --github also writes workflow annotations.
A check that crashes is reported as a warning (the tool is at fault, not the code) and the others still run."""
import argparse
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from checks import hygiene, privacy, pageload, features, updates, speed  # noqa: E402
from checks.common import Finding  # noqa: E402


def annotate(f):
    """One GitHub workflow annotation line: shows on the run page and on the changed line."""
    level = {"error": "error", "warn": "warning", "info": "notice"}[f.level]
    message = f.message.replace("%", "%25").replace("\r", "").replace("\n", " ")
    where = f.where.rsplit(":", 1)
    if len(where) == 2 and where[1].isdigit():
        return f"::{level} file={where[0]},line={where[1]},title={f.check}::{message}"
    return f"::{level} title={f.check}::{f.where}: {message}"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--docs", type=pathlib.Path)
    ap.add_argument("--only")
    ap.add_argument("--apk-budget", type=int)
    ap.add_argument("--strict", action="store_true")
    ap.add_argument("--no-info", action="store_true")
    ap.add_argument("--github", action="store_true")
    a = ap.parse_args()
    runs = {
        "hygiene": hygiene.run,
        "privacy": privacy.run,
        "pageload": pageload.run,
        "features": lambda: features.run(a.docs),
        "updates": updates.run,
        "speed": lambda: speed.run(a.apk_budget),
    }
    findings = []
    for name, fn in runs.items():
        if a.only and a.only != name:
            continue
        try:
            findings += fn()
        except Exception as e:  # a bug in a check must not block a build
            findings.append(Finding("warn", name, "tools/checks", f"check crashed: {e.__class__.__name__}: {e}"))
    order = {"error": 0, "warn": 1, "info": 2}
    for f in sorted(findings, key=lambda f: (order[f.level], f.check, f.where)):
        if a.no_info and f.level == "info":
            continue
        print(f)
        if a.github and f.level != "info":
            print(annotate(f))
    counts = {k: sum(1 for f in findings if f.level == k) for k in order}
    print(f"\nerrors={counts['error']} warnings={counts['warn']} info={counts['info']}")
    return 1 if counts["error"] or (a.strict and counts["warn"]) else 0


if __name__ == "__main__":
    sys.exit(main())
