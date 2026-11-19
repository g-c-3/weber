"""Stray and undocumented code: leftovers, banned words, dead code, empty catches, unexplained long functions."""
import re
from .common import Finding, kotlin_files, read, rel, strip_comments, text_files, function_bodies, ROOT

NAME = "hygiene"
BANNED = [
    (r"\bA[I]\b", "banned word (project rule: use the concrete alternative)"),
    (r"(?i)\b(cla[u]de|anthrop[i]c|chatg[p]t|gp[t]-\d|gemin[i])\b", "no assistant or model name in any file"),
    (r"github_p[a]t_", "a token must never be in the repository"),
    (r"(?i)\bge[c]ko(view)?\b", "leftover from the retired engine"),
]
MARKER = re.compile(r"\b(T[O]DO|FIXM[E]|X{3}|HAC[K])\b")
LIFECYCLE = {"onCreate", "onResume", "onPause", "onDestroy", "onStart", "onStop", "onDraw", "onMeasure", "onLayout", "onTouchEvent",
             "onSizeChanged", "onAttachedToWindow", "onDetachedFromWindow", "onActivityResult", "onNewIntent", "onBackPressed",
             "onSaveInstanceState", "onWindowFocusChanged", "dispatchDraw", "onInterceptTouchEvent", "onConfigurationChanged",
             "onRequestPermissionsResult", "main", "toString", "equals", "hashCode", "onReceive", "performClick"}


def run():
    out = []
    for p in text_files():
        for n, line in enumerate(read(p).split("\n"), 1):
            for pat, why in BANNED:
                if re.search(pat, line):
                    out.append(Finding("error", NAME, f"{rel(p)}:{n}", why))
            if MARKER.search(line):
                out.append(Finding("warn", NAME, f"{rel(p)}:{n}", "open work marker left in the text"))
    files = kotlin_files()
    allcode = "\n".join(strip_comments(read(p)) for p in files)
    for p in files:
        raw = read(p)
        code = strip_comments(raw)
        body_without_imports = "\n".join(l for l in code.split("\n") if not l.startswith("import "))
        for n, line in enumerate(code.split("\n"), 1):
            m = re.match(r"import\s+([\w.]+)(?:\s+as\s+(\w+))?$", line.strip())
            if m:
                simple = m.group(2) or m.group(1).split(".")[-1]
                if simple != "*" and not re.search(r"\b" + re.escape(simple) + r"\b", body_without_imports):
                    out.append(Finding("warn", NAME, f"{rel(p)}:{n}", f"unused import {simple}"))
            if re.search(r"\b(println|Log\.[dviwe])\(|printStackTrace", line):
                out.append(Finding("warn", NAME, f"{rel(p)}:{n}", "logging call: Ardour logs nothing outside the in-memory trace"))
        quoted = [(m.start(), m.end()) for m in re.finditer(r'"""(.*?)"""', raw, re.S)]
        for m in re.finditer(r"catch\s*\([^)]*\)\s*\{\s*\}", raw):
            if any(a <= m.start() < b for a, b in quoted):
                continue  # script text inside a raw string, not Kotlin
            n = raw.count("\n", 0, m.start()) + 1
            out.append(Finding("warn", NAME, f"{rel(p)}:{n}", "empty catch without a stated reason"))
        for m in re.finditer(r"private\s+(?:const\s+)?(?:fun|val|var)\s+(?:<[^>]+>\s*)?(?:\w+\.)?(\w+)", code):
            name = m.group(1)
            if len(re.findall(r"\b" + re.escape(name) + r"\b", code)) == 1:
                n = code.count("\n", 0, m.start()) + 1
                out.append(Finding("warn", NAME, f"{rel(p)}:{n}", f"private '{name}' is never used"))
        for name, first, last, body in function_bodies(code):
            if last - first + 1 >= 45:
                above = raw.split("\n")[max(0, first - 3):first - 1]
                if not any(l.strip().startswith(("//", "/*", "*", "@")) for l in above):
                    out.append(Finding("info", NAME, f"{rel(p)}:{first}", f"{name}() is {last - first + 1} lines with no comment above it"))
    # non-private functions that nothing calls
    for p in files:
        code = strip_comments(read(p))
        for m in re.finditer(r"^\s*(?:internal\s+)?fun\s+(?:<[^>]+>\s*)?(?:\w+\.)?(\w+)\s*\(", code, re.M):
            name = m.group(1)
            if name in LIFECYCLE or re.search(r"override\s+fun\s+" + name, code):
                continue
            if len(re.findall(r"\b" + re.escape(name) + r"\b", allcode)) == 1:
                n = code.count("\n", 0, m.start()) + 1
                out.append(Finding("warn", NAME, f"{rel(p)}:{n}", f"function '{name}' is declared and never called"))
    settings = read(ROOT / "settings.gradle.kts")
    if 'rootProject.name = "Ardour"' not in settings:
        out.append(Finding("error", NAME, "settings.gradle.kts", "rootProject.name is not Ardour (leftover project name shows in build logs)"))
    return out
