#!/usr/bin/env python3
"""Follow a live SCD debug recording: prints one compact line per event as it arrives.

usage: watch_session.py <backend-url> <session> <token> [--after N] [--raw]
"""
import json, sys, time, urllib.request, urllib.error

def fmt(e):
    t = e.get("type"); ts = f'{e.get("t", 0) / 1000:7.1f}s #{e.get("seq")}'
    if t == "chat":
        h = f'   [hover] {e["hover"]!r}' if "hover" in e else ""
        return f'{ts} CHAT/{e.get("channel")}: {e.get("text")!r}{h}'
    if t == "sidebar":
        return f'{ts} SIDEBAR [{e.get("title")}] ' + " | ".join(e.get("lines", []))
    if t == "tab":
        return f'{ts} TAB ' + " | ".join(e.get("lines", []))
    if t == "nameplates":
        ch = "; ".join(f'{p["entity"]}#{p["id"]} {p["name"]!r} @{p["x"]},{p["y"]},{p["z"]}' for p in e.get("changed", []))
        rm = e.get("removed")
        return f'{ts} PLATES +[{ch}]' + (f' -{rm}' if rm else "")
    if t == "menu":
        slots = "; ".join(f'[{s["slot"]}] {s["name"]}' + (" :: " + " / ".join(s["lore"]) if s.get("lore") else "") for s in e.get("slots", []))
        return f'{ts} MENU "{e.get("title")}" {slots}'
    if t == "state":
        return f'{ts} STATE {json.dumps(e.get("scd"))}'
    if t == "log":
        return f'{ts} LOG/{e.get("level")} {e.get("msg")}'
    if t == "mark":
        return f'{ts} >>> MARK: {e.get("note")} <<<'
    return f'{ts} {t.upper()} ' + json.dumps({k: v for k, v in e.items() if k not in ("seq", "t", "type", "received")})

def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    base, session, token = args[0].rstrip("/"), args[1], args[2]
    after = int(sys.argv[sys.argv.index("--after") + 1]) if "--after" in sys.argv else 0
    raw = "--raw" in sys.argv
    failures = 0
    while True:
        url = f"{base}/api/debug/sessions/{session}/events?after={after}&limit=2000"
        req = urllib.request.Request(url, headers={"X-SCD-Debug-Token": token, "User-Agent": "scd-watch"})
        try:
            with urllib.request.urlopen(req, timeout=15) as r:
                body = json.load(r)
            failures = 0
            for e in body.get("events", []):
                print(json.dumps(e) if raw else fmt(e), flush=True)
                after = max(after, e.get("seq", after))
                if e.get("type") == "stop":
                    print("--- session stopped ---", flush=True)
                    return
        except urllib.error.HTTPError as err:
            if err.code == 404:
                time.sleep(2)
                continue
            print(f"HTTP {err.code}: {err.read()[:200]!r}", flush=True)
            failures += 1
        except Exception as err:  # network hiccup
            failures += 1
            print(f"poll failed ({failures}): {err}", flush=True)
        if failures > 30:
            print("giving up after repeated failures", flush=True)
            return
        time.sleep(2)

if __name__ == "__main__":
    main()
