"""Shared helpers for the repository checks: source loading, comment stripping, function bodies, findings."""
import pathlib
import re
from dataclasses import dataclass

ROOT = pathlib.Path(__file__).resolve().parents[2]
SRC = ROOT / "app" / "src"
SKIP_TEXT = {"verification-metadata.xml", "Readability.min.js", "LICENSE", "gradle-wrapper.jar", "Readability.LICENSE.txt"}


@dataclass
class Finding:
    level: str  # error | warn | info
    check: str
    where: str
    message: str

    def __str__(self):
        return f"{self.level.upper():5} {self.check:9} {self.where}: {self.message}"


def kotlin_files():
    return sorted(SRC.rglob("*.kt"))


def rel(path):
    return str(pathlib.Path(path).resolve().relative_to(ROOT))


def read(path):
    return pathlib.Path(path).read_text(encoding="utf-8")


def text_files():
    out = []
    for p in ROOT.rglob("*"):
        if not p.is_file() or ".git" in p.parts or p.name in SKIP_TEXT or p.suffix in {".jar", ".png", ".webp", ".ico", ".jks", ".keystore"}:
            continue
        try:
            p.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        out.append(p)
    return sorted(out)


def strip_comments(text):
    """Source with // and /* */ comments blanked, line numbers kept."""
    text = re.sub(r"/\*.*?\*/", lambda m: "\n" * m.group(0).count("\n"), text, flags=re.S)
    return re.sub(r"(?<!:)//[^\n]*", "", text)


def function_bodies(text):
    """Yield (name, first_line, last_line, body) for every `fun` with a block body, by brace matching."""
    for m in re.finditer(r"\bfun\s+(?:<[^>]+>\s*)?(?:[\w.]+\.)?(\w+)\s*\(", text):
        i = text.find("{", m.end())
        eq = text.find("\n", m.end())
        header_end = i if i != -1 else len(text)
        # expression body or abstract: no brace before the next declaration line break with '='
        sig = text[m.end():header_end]
        if "\n" in sig and re.search(r"\)\s*(:\s*[\w<>?,. ]+)?\s*=", sig.split("{")[0]):
            continue
        if i == -1:
            continue
        depth, j = 0, i
        while j < len(text):
            c = text[j]
            if c == "{":
                depth += 1
            elif c == "}":
                depth -= 1
                if depth == 0:
                    break
            j += 1
        first = text.count("\n", 0, m.start()) + 1
        last = text.count("\n", 0, j) + 1
        yield m.group(1), first, last, text[i:j + 1]
