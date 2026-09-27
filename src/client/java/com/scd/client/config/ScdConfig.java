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
	public Accessories accessories = new Accessories();
	public Map<String, HudLayout> huds = new LinkedHashMap<>();

	public static final class General {
		/** Name of the {@code Theme} preset every SCD screen and HUD is skinned with. */
		public String theme = "Classic";
		/** Unlocks /scd debug and verbose logging - for troubleshooting, off for normal play. */
		public boolean developerMode = false;
		/** Only run SkyBlock features while the sidebar says SKYBLOCK (off = also on other servers, for testing). */
		public boolean requireSkyblock = true;
	}

	public static final class Backend {
		/** Base URL of the SCD backend (mayor, attribute shards, accessory profiles, room reports). */
		public String serverUrl = "http://localhost:3000";
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
		/** Seconds without hitting anything before the hunt timer pauses (AFK-proof spawn times). */
		public int huntIdleSeconds = 5;
		/** Ability cue id -> enabled; ids come from {@code AbilityCue}. Missing = enabled. */
		public Map<String, Boolean> cues = new LinkedHashMap<>();

		public boolean cueEnabled(String id) {
			return cues.getOrDefault(id, true);
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
		/** Opt-in: upload anonymous room block fingerprints to the backend's mapping database. */
		public boolean roomMapping = false;
	}

	public static final class Accessories {
		public boolean bagOverlay = true;
		/** MAX, PRICE or VALUE (coins per Magical Power). */
		public String sort = "MAX";
	}
}
