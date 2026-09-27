# SCD 2.0 — Hypixel SkyBlock companion for Minecraft 26.2 (Fabric)

A ground-up redesign of [RandoDane/scd](https://github.com/RandoDane/scd) 1.x for Minecraft 26.2.

## Features
- **Market prices (scd.wtf)**: Bazaar insta-buy/sell, Auction House estimate + lowest BIN on tooltips, whole-stack value, hover price graph, `/scd price`.
- **Slayer**: quest/boss tracking from the sidebar, boss located via its "Spawned by" tag, fight + AFK-proof hunt timers, HP bar, data-driven mechanic cues, spawn/kill/miniboss alerts, glow/box/tracer highlight, personal bests, RNG meter (chat + menu + Daemon Shard estimate), drop tally with market value, session stats (kills/hr, XP, drops/hr), Explosive Arrow counter. Kills are only booked on `SLAYER QUEST COMPLETE!`.
- **Carries**: one model for Slayer (per kill) and Dungeon (per run) carries, auto-credited, party-chat progress from templates, clickable Done/+5/+10 prompts, manual adjust, earned/outstanding totals.
- **Dungeons**: live score (Skyblocker/Odin formula plus: room total solved from clear % with the room engine's known rooms as a floor, exact secret total once every room is identified, tab-list team deaths, mimic detected from its death, optional Spirit-pet discount), "S+ at N secrets" hint, S/S+ alerts, splits (blood open, Watcher, boss, clear) with per-floor PBs, end-of-run summary that logs the estimate against Hypixel's final score.
- **Room engine**: identifies every Catacombs room from its centre-column "core" hash (140 rooms bundled), reads the dungeon map for shapes/types/checkmarks, finds each room's blue-terracotta anchor for a rotation-independent room frame. Room HUD (name, secrets, crypts; optional rotation + your room-relative position), `/scd dungeon room`, `/scd dungeon rooms`, and `/scd dungeon room name "<name>" [secrets]` to teach it rooms it doesn't know (saved to `config/scd/dungeon/rooms.json`).
- **Secret routes**: plays a route in every identified room (path, etherwarp/mine/interact/TNT/pearl spots, the secret with step counter; next step previewed) and advances by itself on chest/skull clicks, item pickups, bat kills and exit points. Record your own by playing: `/scd route record` → clear the room → `/scd route record stop` (`mark` for exits/waypoints, `undo`). Every recording is its own route; in a room with several, the one starting nearest where you walked in plays (`/scd route alt` to switch, `/scd route delete [all]`). Secrets are detected from the action-bar secret counter plus clicks, item pickups, bat kills and the Wither Essence message; the start block and each secret are marked while recording and playing. Share a room's route as a code (`/scd route share` copies it, `/scd route import <code>`). Packs in `config/scd/routes/` use SecretRoutes' `routes.json` format both ways (its room names are mapped onto ours), can be toggled per pack, and your own `my_routes.json` always wins. Keybinds for next/previous step.
- **Lag scanner** (General → Performance, `/scd lag on`): frame rate, 1% lows, tick time and GC, plus the CPU cost of each entity type, block entity type and particles with their counts; frame spikes are logged with where you stood and what was expensive; `/scd lag places` ranks the laggiest spots, `/scd lag report` writes it all to `config/scd/lag/`.
- **Accessories**: exact Accessory Power from a live bag scan, missing accessories priced from the market and sortable by coins per Magical Power.
- **UI**: fixed-size window with collapsible settings groups, Setup → Appearance for theme, font, menu text size and every HUD's size/panel/colors; one themed toolkit for every screen and HUD; HUD editor that moves/resizes all overlays at once with snapping and anchors.

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

## Third-party data
- `assets/scd/dungeon/rooms.json` is the room database from [Odin](https://github.com/odtheking/Odin), BSD 3-Clause, © 2025 odtheking — license in `assets/scd/dungeon/rooms.LICENSE.txt`.
- Poppins font: SIL Open Font License (`assets/scd/font/OFL.txt`).
