"""Privacy and security rules from the project rules: engine settings, manifest, network connections, certificate handling."""
import re
from .common import Finding, ROOT, SRC, kotlin_files, read, rel, strip_comments

NAME = "privacy"
# INTERNET for page loads; ACCESS_NETWORK_STATE tells an offline phone from an unreachable site; the install permission is sideload only;
# RECORD_AUDIO for voice search, asked on the first tap (DECISIONS.md 117).
ALLOWED_PERMISSIONS = {
    "android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE", "android.permission.REQUEST_INSTALL_PACKAGES",
    "android.permission.RECORD_AUDIO",
}
ALLOWED_HOSTS = {
    "api.github.com", "github.com", "play.google.com", "market.android.com", "schemas.android.com", "www.w3.org",
    "developer.android.com", "example.com", "www.example.com", "localhost",
    # the search pages the user can pick (DECISIONS.md 117); page visits, not connections of the app
    "duckduckgo.com", "www.google.com", "www.bing.com", "search.brave.com", "www.startpage.com",
}
FORBIDDEN = [
    (r"allowFileAccess\s*=\s*true|setAllowFileAccess\(\s*true", "file access must stay off"),
    (r"allowContentAccess\s*=\s*true|setAllowContentAccess\(\s*true", "content access must stay off"),
    (r"AllowUniversalAccessFromFileURLs\s*\(\s*true|allowUniversalAccessFromFileURLs\s*=\s*true", "no universal access from file URLs"),
    (r"MIXED_CONTENT_ALWAYS_ALLOW|MIXED_CONTENT_COMPATIBILITY_MODE", "mixed content must never be allowed"),
    (r"addJavascriptInterface", "no native object exposed to pages"),
    (r"\.proceed\(\)", "no certificate-check bypass"),
    (r"setWebContentsDebuggingEnabled\(\s*true", "page debugging must not be on in a release"),
    (r"onReceivedClientCertRequest", "client certificate handling needs review"),
]


def run():
    out = []
    for p in kotlin_files():
        raw = strip_comments(read(p))
        for n, line in enumerate(raw.split("\n"), 1):
            for pat, why in FORBIDDEN:
                if re.search(pat, line):
                    out.append(Finding("error", NAME, f"{rel(p)}:{n}", why))
            if re.search(r"HttpURLConnection|OkHttp|URL\(\s*\"|openConnection|java\.net\.Socket", line):
                # The updater is sideload only; search suggestions are approved for both flavors (DECISIONS.md 119, 125).
                if "sideload" not in p.parts and p.name != "Suggestions.kt":
                    out.append(Finding("error", NAME, f"{rel(p)}:{n}", "network code outside the sideload source set: a new app connection needs approval"))
    # every literal host in code and manifests
    hosts = {}
    for p in list(SRC.rglob("*.kt")) + list(SRC.rglob("*.xml")) + [ROOT / "app" / "build.gradle.kts"]:
        for n, line in enumerate(read(p).split("\n"), 1):
            for m in re.finditer(r"https?://([A-Za-z0-9.-]+)", line):
                hosts.setdefault(m.group(1), []).append(f"{rel(p)}:{n}")
    for host, where in sorted(hosts.items()):
        if host not in ALLOWED_HOSTS:
            out.append(Finding("warn", NAME, where[0], f"address of an unlisted host: {host} (a connection of its own needs approval)"))
    for manifest in SRC.rglob("AndroidManifest.xml"):
        text = read(manifest)
        for perm in re.findall(r'uses-permission[^>]*android:name="([^"]+)"', text):
            if perm not in ALLOWED_PERMISSIONS:
                out.append(Finding("error", NAME, rel(manifest), f"permission not on the approved list: {perm}"))
        if re.search(r'usesCleartextTraffic="true"', text):
            out.append(Finding("error", NAME, rel(manifest), "cleartext traffic is enabled"))
        if re.search(r'android:allowBackup="true"', text):
            out.append(Finding("warn", NAME, rel(manifest), "allowBackup is true: browsing data could leave the phone in a backup"))
        if manifest.parent.name == "main":
            for key in ("EnableSafeBrowsing", "StartupFlags", "MetricsOptOut", "WebView"):
                pass
            if not re.search(r"EnableSafeBrowsing", text):
                out.append(Finding("error", NAME, rel(manifest), "Safe Browsing opt-out meta-data missing (decision 29)"))
            if not re.search(r"MetricsOptOut", text):
                out.append(Finding("error", NAME, rel(manifest), "WebView diagnostic-reporting opt-out meta-data missing (decision 29)"))
    play = list((SRC / "play").rglob("*"))
    for p in play:
        if p.is_file() and re.search(r"REQUEST_INSTALL_PACKAGES", read(p)):
            out.append(Finding("error", NAME, rel(p), "the install permission must not be in the play flavor"))
    return out
