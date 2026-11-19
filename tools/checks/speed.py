"""Smoothness and size rules found by reading the source: allocation while drawing, work on the main thread, size budget."""
import os
import re
import urllib.request
import json
from .common import Finding, kotlin_files, read, rel, strip_comments, function_bodies

NAME = "speed"
DRAW = {"onDraw", "dispatchDraw", "draw"}
ALLOCATING = re.compile(r"\b(Paint|Path|RectF|Rect|LinearGradient|RadialGradient|SweepGradient|Matrix|Typeface\.create|ArrayList|mutableListOf|StringBuilder)\s*\(")
BLOCKING = re.compile(r"Thread\.sleep|\.readText\(|\.readBytes\(|\.openStream\(|runBlocking|\.get\(\)\s*//\s*blocking")


def run(apk_budget=None):
    out = []
    for p in kotlin_files():
        code = strip_comments(read(p))
        for name, first, last, body in function_bodies(code):
            if name in DRAW:
                for off, line in enumerate(body.split("\n")):
                    if ALLOCATING.search(line):
                        out.append(Finding("warn", NAME, f"{rel(p)}:{first + off}", f"object created inside {name}(): allocate once as a field, drawing runs every frame"))
            if name in {"onTouchEvent", "onScroll", "onProgressChanged", "onMeasure", "onLayout"}:
                if BLOCKING.search(body):
                    out.append(Finding("warn", NAME, f"{rel(p)}:{first}", f"blocking call inside {name}()"))
        for n, line in enumerate(code.split("\n"), 1):
            if re.search(r"Thread\.sleep", line):
                out.append(Finding("error", NAME, f"{rel(p)}:{n}", "Thread.sleep: use a posted delay or an animation"))
            if re.search(r"\bwhile\s*\(\s*true\s*\)", line) and "break" not in "\n".join(code.split("\n")[n:n + 8]):
                out.append(Finding("warn", NAME, f"{rel(p)}:{n}", "unbounded loop"))
    try:
        headers = {"User-Agent": "ardour-check"}
        if os.environ.get("GITHUB_TOKEN"):
            headers["Authorization"] = "Bearer " + os.environ["GITHUB_TOKEN"]
        req = urllib.request.Request("https://api.github.com/repos/g-c-3/weber/releases/latest", headers=headers)
        data = json.load(urllib.request.urlopen(req, timeout=10))
        for asset in data.get("assets", []):
            if asset["name"].endswith(".apk"):
                size = asset["size"]
                msg = f"latest release {data['tag_name']} APK is {size:,} bytes"
                if apk_budget and size > apk_budget:
                    out.append(Finding("error", NAME, "release", msg + f", over the budget of {apk_budget:,}"))
                else:
                    out.append(Finding("info", NAME, "release", msg))
    except Exception as e:  # network is optional for this check
        out.append(Finding("info", NAME, "release", f"APK size not checked ({e.__class__.__name__})"))
    return out
