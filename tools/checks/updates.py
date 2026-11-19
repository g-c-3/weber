"""In-app update rules: sideload only, one GitHub request on tap, checksum verified, numeric version compare."""
import re
from .common import Finding, SRC, kotlin_files, read, rel, strip_comments

NAME = "updates"


def run():
    out = []
    sideload = [p for p in kotlin_files() if "sideload" in p.parts]
    check = next((p for p in sideload if p.name == "UpdateCheck.kt"), None)
    install = next((p for p in sideload if p.name == "ApkInstaller.kt"), None)
    if check is None or install is None:
        return [Finding("error", NAME, "app/src/sideload", "UpdateCheck.kt or ApkInstaller.kt is missing")]
    code = strip_comments(read(check) + read(install))
    if "SHA-256" not in code and "SHA256" not in code:
        out.append(Finding("error", NAME, rel(check), "the downloaded APK is not verified against a SHA-256"))
    if "releases/latest" not in code:
        out.append(Finding("warn", NAME, rel(check), "the update request does not use releases/latest (decision 33)"))
    if not re.search(r"split\(\s*['\"]\.['\"]\s*\)", code):
        out.append(Finding("warn", NAME, rel(check), "no number-by-number version comparison found"))
    if re.search(r"Authorization|Bearer|api[_-]?key|token", code, re.I):
        out.append(Finding("error", NAME, rel(check), "update requests must carry no token or identifier"))
    for p in kotlin_files():
        if "sideload" in p.parts or "play" in p.parts:
            continue
        for n, line in enumerate(read(p).split("\n"), 1):
            if re.search(r"\bUpdateCheck\b|\bApkInstaller\b", line):
                out.append(Finding("error", NAME, f"{rel(p)}:{n}", "update code referenced from the shared source set: it would be compiled into the play flavor"))
    play = [p for p in kotlin_files() if "play" in p.parts]
    for p in play:
        if re.search(r"HttpURLConnection|openConnection|PackageInstaller", read(p)):
            out.append(Finding("error", NAME, rel(p), "the play flavor must not download or install"))
    return out
