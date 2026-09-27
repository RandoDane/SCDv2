# SCD 2.0 — Hypixel SkyBlock companion for Minecraft 26.2 (Fabric)

A ground-up redesign of [RandoDane/scd](https://github.com/RandoDane/scd) 1.x for Minecraft 26.2.

## Features
- **Market prices (scd.wtf)**: Bazaar insta-buy/sell, Auction House estimate + lowest BIN on tooltips, whole-stack value, hover price graph, `/scd price`.
- **Slayer**: quest/boss tracking from the sidebar, boss located via its "Spawned by" tag, fight + AFK-proof hunt timers, HP bar, data-driven mechanic cues, spawn/kill/miniboss alerts, glow/box/tracer highlight, personal bests, RNG meter (chat + menu + Daemon Shard estimate), drop tally with market value, session stats (kills/hr, XP, drops/hr), Explosive Arrow counter. Kills are only booked on `SLAYER QUEST COMPLETE!`.
- **Carries**: one model for Slayer (per kill) and Dungeon (per run) carries, auto-credited, party-chat progress from templates, clickable Done/+5/+10 prompts, manual adjust, earned/outstanding totals.
- **Dungeons**: live score estimate (tested port of Skyblocker/Odin's formula, with boss-room baseline), S/S+ alerts, end-of-run summary, opt-in room mapping.
- **Accessories**: exact Accessory Power from a live bag scan, missing accessories priced from the market and sortable by coins per Magical Power.
- **UI**: one themed toolkit for every screen and HUD; HUD editor that moves/resizes all overlays at once with snapping and anchors.

## Setup
- `/scd market key <scd_...>` (or Settings → Market & Bazaar, or the `SCD_KEY` env var) — all prices come from `https://market.scd.wtf/api`.
- `/scd server <url>` — your SCD backend (mayor perks, attribute-shard ids, accessory profiles, room reports).
- 1.x settings, records, RNG meter, drops and carries are imported automatically on first launch.
- Ports 20, 443 and 8000 are in use on the dev machine: never bind anything to them (outbound calls are fine).

## Layout
| Source set | Contents |
|---|---|
| `src/main` | `com.scd.logic` — pure Java (parsers, score formula, number/text utils), no Minecraft deps |
| `src/client` | the mod: `core` (event bus, tasks, chat, log), `storage`, `config`, `hypixel` (sidebar/tab snapshot, chat router, items), `net`, `ui`, `hud`, `feature/*`, `screen`, `mixin` |
| `src/test` | JUnit tests for `com.scd.logic` |
| `src/gametest` | in-game scenario test: fakes a SkyBlock sidebar, a Slayer boss and raw chat, asserts SCD state, screenshots every screen |

## Build & test
```bash
./gradlew build                     # jar in build/libs + unit tests
xvfb-run -a ./gradlew runClientGameTest   # full in-game test (needs a display; screenshots in build/run/clientGameTest/screenshots)
```

## Live test sessions (debug recording)
1. In game: `/scd server http://95.216.136.132:3000` (the CCBz dev server, which has `backend/debugSessions.js` mounted at `/api/debug`).
2. `/scd record start` → prints `session / token` (click to copy) and shows a red REC badge.
3. Play; `/scd record mark <note>` flags anything suspicious. `/scd record stop` ends it.
4. Watcher: `python3 tools/watch_session.py http://95.216.136.132:3000 <session> <token>` prints every event live
   (chat + hover text, sidebar/tab changes, nameplates, menus with lore, SCD state/decisions/errors, marks).
Everything is also written locally to `config/scd/recordings/<session>.jsonl` (and server-side to
`/root/CCBz/server/data/debug-sessions/<session>.jsonl`), ready for offline replay.
