package com.scd.client.feature.slayer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.scd.client.core.ScdLog;
import com.scd.client.storage.ScdPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** One-time import of the 1.x per-feature files (scd_slayer_records/rng_meter/drops.json, or their ccbz_ ancestors). */
final class LegacySlayerImport {
	private LegacySlayerImport() {
	}

	static void into(SlayerData data) {
		JsonObject records = read("scd_slayer_records.json", "ccbz_slayer_records.json");
		if (records != null) {
			for (var e : records.entrySet()) data.bestKillMs.put(e.getKey(), e.getValue().getAsLong());
		}

		JsonObject drops = read("scd_slayer_drops.json", "ccbz_slayer_drops.json");
		if (drops != null) {
			for (var type : drops.entrySet()) {
				Map<String, SlayerData.Drop> items = new LinkedHashMap<>();
				for (var item : type.getValue().getAsJsonObject().entrySet()) {
					JsonObject o = item.getValue().getAsJsonObject();
					items.put(item.getKey(), new SlayerData.Drop(o.get("displayName").getAsString(), o.get("count").getAsLong()));
				}
				data.drops.put(type.getKey(), items);
			}
		}

		JsonObject rng = read("scd_slayer_rng_meter.json", "ccbz_slayer_rng_meter.json");
		if (rng != null) {
			// 1.0 stored a flat type -> xp map; later versions a SaveData record.
			boolean flat = rng.entrySet().stream().allMatch(e -> e.getValue().isJsonPrimitive());
			if (flat) {
				for (var e : rng.entrySet()) rngFor(data, e.getKey()).storedXp = e.getValue().getAsLong();
			} else {
				forEach(rng, "storedXpByType", (t, v) -> rngFor(data, t).storedXp = v.getAsLong());
				forEach(rng, "chancePercentByType", (t, v) -> rngFor(data, t).chancePercent = v.getAsDouble());
				forEach(rng, "selectedDropByType", (t, v) -> rngFor(data, t).selectedDrop = v.getAsString());
				forEach(rng, "lastRealSyncXpByType", (t, v) -> rngFor(data, t).lastSyncXp = v.getAsLong());
				forEach(rng, "expectedRawXpSinceSyncByType", (t, v) -> rngFor(data, t).expectedSinceSync = v.getAsLong());
				forEach(rng, "requiredTotalByTypeAndDrop", (t, v) -> {
					for (var d : v.getAsJsonObject().entrySet()) rngFor(data, t).requiredByDrop.put(d.getKey(), d.getValue().getAsLong());
				});
				forEach(rng, "killsByTypeAndTier", (t, v) -> {
					Map<String, Integer> tiers = new HashMap<>();
					for (var d : v.getAsJsonObject().entrySet()) tiers.put(d.getKey(), d.getValue().getAsInt());
					data.kills.put(t, tiers);
				});
				if (rng.has("daemonMultiplier") && !rng.get("daemonMultiplier").isJsonNull()) {
					data.daemonMultiplier = rng.get("daemonMultiplier").getAsDouble();
				}
			}
		}
		if (records != null || drops != null || rng != null) ScdLog.info("Imported 1.x Slayer records/RNG meter/drops");
	}

	private static SlayerData.Rng rngFor(SlayerData data, String type) {
		return data.rng.computeIfAbsent(type, k -> new SlayerData.Rng());
	}

	private static void forEach(JsonObject root, String key, java.util.function.BiConsumer<String, JsonElement> action) {
		JsonElement e = root.get(key);
		if (e == null || !e.isJsonObject()) return;
		for (var entry : e.getAsJsonObject().entrySet()) {
			if (!entry.getValue().isJsonNull()) action.accept(entry.getKey(), entry.getValue());
		}
	}

	private static JsonObject read(String name, String older) {
		for (String n : new String[] {name, older}) {
			Path p = ScdPaths.legacy(n);
			if (!Files.exists(p)) continue;
			try {
				JsonElement e = JsonParser.parseString(Files.readString(p));
				if (e.isJsonObject()) return e.getAsJsonObject();
			} catch (Exception ex) {
				ScdLog.warn("Skipping unreadable legacy file " + p, ex);
			}
		}
		return null;
	}
}
