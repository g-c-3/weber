"""Page load rules: the adapter is the only engine caller, every callback and every load leaves a trace, the loading line state machine."""
import re
from .common import Finding, ROOT, SRC, kotlin_files, read, rel, strip_comments

NAME = "pageload"
CALLBACKS = ["onPageStarted", "onPageFinished", "onPageCommitVisible", "onReceivedError", "onReceivedHttpError", "onReceivedSslError",
             "onRenderProcessGone", "doUpdateVisitedHistory", "shouldOverrideUrlLoading", "onProgressChanged"]


def block_after(text, marker):
    i = text.find(marker)
    if i == -1:
        return None
    j = text.find("{", i)
    depth = 0
    for k in range(j, len(text)):
        depth += (text[k] == "{") - (text[k] == "}")
        if depth == 0:
            return text[j:k + 1]
    return None


def run():
    out = []
    adapter = SRC / "main" / "java" / "io" / "github" / "g_c_3" / "ardour" / "EngineAdapter.kt"
    for p in kotlin_files():
        if p == adapter:
            continue
        for n, line in enumerate(read(p).split("\n"), 1):
            if re.match(r"import\s+(android|androidx)\.webkit\.", line):
                out.append(Finding("error", NAME, f"{rel(p)}:{n}", "engine API used outside EngineAdapter.kt (decision 26)"))
    code = read(adapter)
    for cb in CALLBACKS:
        body = block_after(code, f"fun {cb}(")
        if body is None:
            out.append(Finding("error", NAME, rel(adapter), f"engine callback {cb} is not handled"))
        elif "Trace." not in body:
            out.append(Finding("warn", NAME, rel(adapter), f"callback {cb} leaves no trace event"))
    lines = code.split("\n")
    for n, line in enumerate(lines, 1):
        if re.search(r"\.(loadUrl|reload|stopLoading|goBack|goForward)\(", line):
            window = "\n".join(lines[max(0, n - 8):n + 6])
            if "Trace.log" not in window:
                out.append(Finding("warn", NAME, f"{rel(adapter)}:{n}", "engine navigation call without a trace line just before it"))
    progress = read(SRC / "main" / "java" / "io" / "github" / "g_c_3" / "ardour" / "PageProgress.kt")
    update = block_after(progress, "fun update(")
    if update is None or "finishing" not in update:
        out.append(Finding("error", NAME, "PageProgress.kt", "update() does not ignore reports while the line is finishing (the line would stay on screen)"))
    finish = block_after(progress, "fun finish(")
    if finish is None or "finishing" not in finish:
        out.append(Finding("error", NAME, "PageProgress.kt", "finish() does not guard against a second call"))
    main = read(SRC / "main" / "java" / "io" / "github" / "g_c_3" / "ardour" / "MainActivity.kt")
    if "removeCallbacks(heartbeat)" not in main:
        out.append(Finding("warn", NAME, "MainActivity.kt", "the load heartbeat is never cancelled"))
    for pat, why in [(r"loadUrl\(\s*\"javascript:", "javascript: address loaded directly")]:
        for p in kotlin_files():
            for n, line in enumerate(read(p).split("\n"), 1):
                if re.search(pat, line):
                    out.append(Finding("error", NAME, f"{rel(p)}:{n}", why))
    return out
