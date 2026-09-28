package com.scd.client.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every player-facing setting, grouped by feature. Plain public fields so Gson can round-trip it and
 * option rows can bind straight to them; defaults live here and nowhere else. Per-HUD placement and
 * colors live in {@link #huds}, keyed by the HUD element's id.
 */
public final class ScdConfig {
	public General general = new General();
	public Backend backend = new Backend();
	public Market market = new Market();
	public Bazaar bazaar = new Bazaar();
	public Slayer slayer = new Slayer();
	public Carries carries = new Carries();
	public Dungeon dungeon = new Dungeon();
	public Perf perf = new Perf();
	public Accessories accessories = new Accessories();
	public Map<String, HudLayout> huds = new LinkedHashMap<>();

	public static final class General {
		/** Name of the {@code Theme} preset every SCD screen and HUD is skinned with. */
		public String theme = "Midnight";
		/** Smooth (TTF) font in SCD screens and HUDs; off = Minecraft's pixel font. */
		public boolean smoothFont = true;
		/** Text size in SCD menus, percent (70-110); HUDs are sized in the HUD editor. */
		public int menuTextSize = 80;
		/** Click GUI size in percent of its base size (the look of GUI scale 2 at 1080p). */
		public int menuScale = 100;
		/** Click GUI column positions (category -> {x, y}), set by dragging headers. */
		public java.util.Map<String, int[]> clickGuiPositions = new java.util.HashMap<>();
		/** Folded click GUI categories. */
		public java.util.List<String> clickGuiCollapsed = new java.util.ArrayList<>();
		/** Unlocks /scd debug and verbose logging - for troubleshooting, off for normal play. */
		public boolean developerMode = false;
		/** Only run SkyBlock features while the sidebar says SKYBLOCK (off = also on other servers, for testing). */
		public boolean requireSkyblock = true;
	}

	public static final class Perf {
		/** Lag scanner: frame/tick timing, per-type entity/block entity/particle costs, spike hotspots. */
		public boolean lagScanner = false;
		/** Frames at least this long (and 2.5x the recent median) are logged as spikes. */
		public int spikeMs = 50;
	}

	public static final class Backend {
		/** Base URL of the SCD backend (mayor, attribute shards, accessory profiles, room reports). */
		public String serverUrl = com.scd.client.net.BuildInfo.SERVER;
	}

	/** The scd.wtf market API (https://market.scd.wtf) - the source for every price SCD shows. */
	public static final class Market {
		/** scd_... key from wiki.scd.wtf Special:ApiKeys. Falls back to the SCD_KEY environment variable when empty. */
		public String apiKey = "";
		public int bazaarRefreshSeconds = 60;
		/** Show Auction House estimate / lowest BIN on tooltips of non-Bazaar items. */
		public boolean auctionTooltips = true;
	}

	public static final class Bazaar {
		public boolean tooltip = true;
		/** Also show the value of the whole hovered stack, not just one item. */
		public boolean tooltipStackValue = true;
		public boolean graphHud = true;
		/** History range requested for the graph: 1d, 7d, 30d. */
		public String graphRange = "7d";
	}

	public static final class Slayer {
		public boolean hud = true;
		public boolean sessionStats = true;
		public boolean spawnAlert = true;
		public boolean spawnAlertTitle = true;
		public boolean killMessage = true;
		public boolean minibossAlert = true;
		public boolean minibossTitle = false;
		public boolean highlightGlow = true;
		public boolean highlightBox = true;
		public boolean highlightLine = true;
		public boolean dropTracking = true;
		public boolean explosiveArrowCounter = true;
		/** Ability cue id -> enabled; ids come from {@code AbilityCue}. Missing = enabled. */
		public Map<String, Boolean> cues = new LinkedHashMap<>();
		/** Master switch per slayer type ("SPIDER" -> false turns all of its cues off). */
		public Map<String, Boolean> cueGroups = new LinkedHashMap<>();
		/** Kill message extras. */
		public boolean killShowBest = true, killShowLag = true;
		/** Spawn alert: include the hunt time. */
		public boolean spawnShowTime = true;

		public boolean cueEnabled(String id) {
			return cues.getOrDefault(id, true);
		}

		public boolean cueGroupEnabled(String type) {
			return cueGroups.getOrDefault(type, true);
		}
	}

	public static final class Carries {
		/** Post "{player}: {done}/{owed}" in party chat after each credited kill/run. */
		public boolean partyProgress = true;
		public String progressTemplate = "{player}: {done}/{owed} {unit}";
		public String finishTemplate = "gg {player} Please leave a review in #reviews in the relevant Discord";
		/** Ticks to wait before a dungeon progress message, so it isn't buried in the end-of-run chat flood. */
		public int dungeonMessageDelayTicks = 20;
	}

	public static final class Dungeon {
		public boolean scoreHud = true;
		public boolean scoreBreakdown = true;
		public boolean scoreRoomsSecrets = true;
		public boolean scoreCryptsDeathsPuzzles = true;
		/** Title + ping the moment the live estimate first reaches 270 (S) and 300 (S+). */
		public boolean scoreMilestoneAlerts = true;
		public boolean completionSummary = true;
		/** Which parts the run summary line shows. */
		public RunSummary summary = new RunSummary();
		/** Room clear time message parts; onlyPb = only announce new personal bests. */
		public boolean roomTimePb = true, roomTimeAvg = true, roomTimeLag = true, roomTimeOnlyPb = false;
		/** Chest profit: tooltip lines, best-chest outline, "Best:" label over the menu. */
		public boolean chestProfitTooltip = true, chestProfitHighlight = true, chestProfitLabel = true;
		/** Panel beside the chest menu listing every chest by profit. */
		public boolean chestProfitPanel = true;
		/** Bazaar items at "Insta-sell" (sell now) or "Sell offer"; auction items at "Lowest BIN" or "Estimate". */
		public String chestBazaarPrice = "Insta-sell", chestAuctionPrice = "Lowest BIN";
		/** Map HUD layers. */
		public boolean mapSecrets = true, mapPlayers = true, mapDoors = true, mapChecks = true;
		/** Player head size on the dungeon map, percent (100 = 8px). */
		public int mapHeadSize = 100;
		/** Which blessings the blessings HUD lists. */
		public boolean blessPower = true, blessTime = true, blessStone = true, blessLife = true, blessWisdom = true;
		/** Which life savers the invincibility HUD tracks. */
		public boolean invBonzo = true, invSpirit = true, invPhoenix = true;
		/** Secret chime parts. */
		public boolean secretSound = true, secretBox = true;
		/** Value, cost and profit on dungeon reward chests (Croesus + run end), best chest highlighted. */
		public boolean chestProfit = true;
		public boolean puzzleHud = true;
		public boolean deathHud = true;
		/** Title when a teammate dies. */
		public boolean deathAlert = true;
		/** Title when a teammate's sidebar health turns red. */
		public boolean lowHealthAlert = true;
		public boolean blessingHud = true;
		/** Bonzo's Mask / Spirit Mask / Phoenix invincibility and cooldown timers. */
		public boolean invincibilityHud = true;
		/** Chime + brief box on the clicked block when the room's secret counter goes up. */
		public boolean secretChime = true;
		/** Wither/blood doors outlined (red locked, green openable) and the key boxed. */
		public boolean doorHighlight = true;
		public boolean keyAlert = true;
		/** Chat line with the clear time (and PB/average) when a room you entered gets its check. */
		public boolean roomTimeMessage = true;
		/** SCD's dungeon map HUD with per-room secret counts. */
		public boolean mapHud = true;
		/** Quiz, Three Weirdos and Higher/Lower Blaze solvers (show only). */
		public boolean puzzleSolvers = true;
		/** Save a copy of every room played through, to rebuild them in singleplayer. */
		public boolean captureRooms = true;
		/** Box starred (✯) mobs; count the ones left in your room. */
		public boolean starredMobs = true, starredThroughWalls = true, starredHud = true;
		/** When a puzzle fails: title + how many Architect's First Drafts you have (or a sack button). */
		public boolean draftReminder = true;
		/** Secret waypoints: every known secret in the room (labels + route secrets); routes require it. */
		public boolean labeledSecrets = true;
		/** Solvers switched off individually, by puzzle room name (e.g. "Ice Fill"). */
		public java.util.List<String> disabledPuzzles = new java.util.ArrayList<>();

		/** Whether the solver for this puzzle room should run and draw. */
		public boolean puzzleOn(String room) {
			return puzzleSolvers && room != null && !disabledPuzzles.contains(room);
		}
		/** Watcher move timer and blood mob landing spots with spawn countdown. */
		public boolean bloodCamp = true;
		/** Split times (blood open, Watcher, boss, clear) with PB deltas on the score HUD. */
		public boolean scoreSplits = true;
		/** Assume a Spirit pet in the party: the first death costs 1 point instead of 2 (Odin assumes this always). */
		public boolean assumeSpiritPet = false;
		/** Current room name / secrets HUD. */
		public boolean roomHud = true;
		/** Adds rotation, anchor and room-relative position to the room HUD (for making routes). */
		public boolean roomDebug = false;
		/** Play secret routes (yours + enabled packs) in identified rooms. */
		public boolean routes = true;
		public boolean routesThroughWalls = true;
		/** Also draw the step after the current one, faded. */
		public boolean routesShowNext = true;
		/** Paths are straightened to within this many blocks of what was recorded (0 = raw). */
		public double routesSmoothing = 1.5;
		/** Route pack file names (config/scd/routes) that are switched off. */
		public java.util.List<String> disabledRoutePacks = new java.util.ArrayList<>();
		/** Master switch for pack files (off: only your own routes play). */
		public boolean routePacks = true;
	}

	public static final class RunSummary {
		public boolean floor = true, time = true, score = true, estimate = true, secrets = true, deaths = true, damage = true, xp = true, lag = true;
	}

	public static final class Accessories {
		public boolean bagOverlay = true;
		/** MAX, PRICE or VALUE (coins per Magical Power). */
		public String sort = "MAX";
	}
}
