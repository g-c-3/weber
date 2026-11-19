"""Features and docs agreement: decision references in code exist, preferences are used, manifest components exist."""
import re
from .common import Finding, ROOT, SRC, kotlin_files, read, rel, strip_comments

NAME = "features"


def decision_numbers(docs):
    if not docs:
        return None
    nums = set()
    for name in ("DECISIONS.md",):
        path = docs / name
        if path.exists():
            nums |= {int(m.group(1)) for m in re.finditer(r"^## (\d+)\.", path.read_text(encoding="utf-8"), re.M)}
    for archive in (docs / "archive").glob("DECISIONS-*.md") if (docs / "archive").exists() else []:
        nums |= {int(m.group(1)) for m in re.finditer(r"^## (\d+)\.", archive.read_text(encoding="utf-8"), re.M)}
    return nums


def run(docs=None):
    out = []
    known = decision_numbers(docs)
    allsrc = "\n".join(read(p) for p in kotlin_files())
    for p in kotlin_files():
        for n, line in enumerate(read(p).split("\n"), 1):
            for m in re.finditer(r"DECISIONS\.md\s+(\d+(?:\s*(?:,|and|to)\s*\d+)*)", line):
                for num in re.findall(r"\d+", m.group(1)):
                    if known is not None and int(num) not in known:
                        out.append(Finding("error", NAME, f"{rel(p)}:{n}", f"comment cites decision {num}, which is not in DECISIONS.md"))
    if known is None:
        out.append(Finding("info", NAME, "-", "no docs folder given (--docs): decision references were not checked"))
    prefs = read(SRC / "main" / "java" / "io" / "github" / "g_c_3" / "ardour" / "Prefs.kt")
    for const in re.findall(r"const val (\w+)\s*=", prefs):
        uses = len(re.findall(r"Prefs\." + const + r"\b", allsrc))
        if uses == 0:
            out.append(Finding("warn", NAME, "Prefs.kt", f"preference {const} is never read or written"))
    for manifest in SRC.rglob("AndroidManifest.xml"):
        for name in re.findall(r'<activity[^>]*android:name="\.?([\w.]+)"', read(manifest)):
            if not re.search(r"class\s+" + re.escape(name.split(".")[-1]) + r"\b", allsrc):
                out.append(Finding("error", NAME, rel(manifest), f"activity {name} is declared but its class does not exist"))
    for res in (SRC / "main" / "res").rglob("*.xml") if (SRC / "main" / "res").exists() else []:
        pass
    return out
