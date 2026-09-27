package com.scd.client.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.scd.client.core.ScdLog;
import com.scd.client.storage.JsonStore;
import com.scd.client.storage.ScdPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;


/** Owns the config file; on the very first 2.x launch, imports settings from the 1.x config/scd.json. */
public final class ConfigManager {
	private static final int SCHEMA = 1;

	private final JsonStore<ScdConfig> store;
	private final List<Runnable> changeListeners = new ArrayList<>();

	public ConfigManager() {
		Path path = ScdPaths.file("config.json");
		boolean fresh = !Files.exists(path);
		store = JsonStore.open(path, ScdConfig.class, SCHEMA, ScdConfig::new, (root, from) -> {
		});
		if (fresh) importLegacy(store.get());
		ScdConfig c = store.get();
		// Old installs defaulted to a local server: move them to the one built into the mod.
		if (c.backend.serverUrl == null || c.backend.serverUrl.isBlank() || c.backend.serverUrl.equals("http://localhost:3000")) {
			c.backend.serverUrl = com.scd.client.net.BuildInfo.SERVER;
		}
		ScdLog.info("Config loaded: server=" + c.backend.serverUrl + " theme=" + c.general.theme
				+ " developerMode=" + c.general.developerMode);
	}

	public ScdConfig get() {
		return store.get();
	}

	/** Persist (debounced) and tell interested services (backend URL, theme) to re-read. */
	public void save() {
		store.markDirty();
		for (Runnable r : changeListeners) ScdLog.guard("config change listener", r);
	}

	public void onChange(Runnable listener) {
		changeListeners.add(listener);
	}

	public HudLayout hud(String id, HudLayout defaults) {
		return store.get().huds.computeIfAbsent(id, k -> defaults.copy());
	}

	private static void importLegacy(ScdConfig c) {
		Path old = ScdPaths.legacy("scd.json");
		if (!Files.exists(old)) old = ScdPaths.legacy("ccbz.json");
		if (!Files.exists(old)) return;
		try {
			JsonObject root = JsonParser.parseString(Files.readString(old)).getAsJsonObject();
			JsonObject bazaar = obj(root, "bazaar");
			// 1.0 kept the bazaar keys at the top level.
			if (bazaar == null) bazaar = root;
			c.backend.serverUrl = str(bazaar, "serverUrl", c.backend.serverUrl);
			c.bazaar.tooltip = bool(bazaar, "tooltipEnabled", c.bazaar.tooltip);
			c.bazaar.graphHud = bool(bazaar, "graphEnabled", c.bazaar.graphHud);
			c.bazaar.graphRange = str(bazaar, "graphRange", c.bazaar.graphRange);
			pos(bazaar, "graphPosition").ifPresent(l -> c.huds.put("bazaar_graph", l));

			JsonObject slayer = obj(root, "slayer");
			if (slayer != null) {
				c.slayer.hud = bool(slayer, "bossTrackerEnabled", c.slayer.hud);
				c.slayer.minibossAlert = bool(slayer, "minibossAlertEnabled", c.slayer.minibossAlert);
				boolean highlight = bool(slayer, "bossHighlightEnabled", true);
				c.slayer.highlightGlow = highlight;
				c.slayer.highlightBox = highlight;
				c.slayer.highlightLine = highlight;
				c.slayer.sessionStats = bool(slayer, "statsHudEnabled", c.slayer.sessionStats);
				pos(slayer, "bossTrackerPosition").ifPresent(l -> c.huds.put("slayer", l));
				cue(c, slayer, "vampire", "twinclawEnabled", "vampire.twinclaw");
				cue(c, slayer, "vampire", "maniaEnabled", "vampire.mania");
				cue(c, slayer, "spider", "eggSacEnabled", "spider.egg_sacs");
				cue(c, slayer, "blaze", "firePillarEnabled", "blaze.fire_pillars");
				cue(c, slayer, "blaze", "demonsplitEnabled", "blaze.demonsplit");
				cue(c, slayer, "enderman", "beamPhaseEnabled", "enderman.beam_phase");
				cue(c, slayer, "enderman", "hitshieldEnabled", "enderman.hitshield");
				JsonObject ender = obj(slayer, "enderman");
				if (ender != null) {
					c.slayer.explosiveArrowCounter = bool(ender, "explosiveArrowCounterEnabled", c.slayer.explosiveArrowCounter);
					pos(ender, "explosiveArrowCounterPosition").ifPresent(l -> c.huds.put("explosive_arrows", l));
				}
			}

			JsonObject dungeon = obj(root, "dungeon");
			if (dungeon != null) {
				c.dungeon.scoreHud = bool(dungeon, "scoreHudEnabled", c.dungeon.scoreHud);
				c.dungeon.scoreBreakdown = bool(dungeon, "scoreHudShowBreakdown", c.dungeon.scoreBreakdown);
				c.dungeon.scoreRoomsSecrets = bool(dungeon, "scoreHudShowRoomsSecrets", c.dungeon.scoreRoomsSecrets);
				c.dungeon.scoreCryptsDeathsPuzzles = bool(dungeon, "scoreHudShowCryptsDeathsPuzzles", c.dungeon.scoreCryptsDeathsPuzzles);
				pos(dungeon, "scoreHudPosition").ifPresent(l -> c.huds.put("dungeon_score", l));
			}

			JsonObject acc = obj(root, "accessories");
			if (acc != null) c.accessories.bagOverlay = bool(acc, "missingAccessoriesOverlayEnabled", c.accessories.bagOverlay);

			c.general.theme = str(root, "menuTheme", c.general.theme);
			c.general.developerMode = bool(root, "devUnlocked", false);
			ScdLog.info("Imported settings from " + old.getFileName());
		} catch (Exception e) {
			ScdLog.warn("Could not import legacy settings from " + old + " - using defaults", e);
		}
	}

	private static void cue(ScdConfig c, JsonObject slayer, String section, String key, String cueId) {
		JsonObject s = obj(slayer, section);
		if (s != null && s.has(key)) c.slayer.cues.put(cueId, s.get(key).getAsBoolean());
	}

	private static java.util.Optional<HudLayout> pos(JsonObject parent, String key) {
		JsonObject p = obj(parent, key);
		if (p == null) return java.util.Optional.empty();
		HudLayout l = HudLayout.at(HudLayout.AnchorX.LEFT, HudLayout.AnchorY.TOP,
				p.has("x") ? p.get("x").getAsInt() : 8, p.has("y") ? p.get("y").getAsInt() : 8);
		float scale = p.has("scale") ? p.get("scale").getAsFloat() : 1f;
		l.scale = scale >= 0.5f && scale <= 2.5f ? scale : 1f;
		return java.util.Optional.of(l);
	}

	private static JsonObject obj(JsonObject parent, String key) {
		JsonElement e = parent.get(key);
		return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
	}

	private static String str(JsonObject o, String key, String fallback) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : fallback;
	}

	private static boolean bool(JsonObject o, String key, boolean fallback) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsBoolean() : fallback;
	}

}
