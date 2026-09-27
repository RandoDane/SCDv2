package com.scd.client.feature.slayer;

import com.scd.client.storage.JsonStore;

import java.util.HashMap;

/** Personal-best fight times and lifetime kill counts per type + tier. */
public final class SlayerRecords {
	private final JsonStore<SlayerData> store;

	SlayerRecords(JsonStore<SlayerData> store) {
		this.store = store;
	}

	public Long best(SlayerType type, String tier) {
		return store.get().bestKillMs.get(SlayerData.recordKey(type, tier));
	}

	/** Records a kill; true if it's a new personal best (including the first ever for that tier). */
	boolean recordKill(SlayerType type, String tier, long fightMs) {
		SlayerData data = store.get();
		data.killsByDay.merge(java.time.LocalDate.now().toString(), 1, Integer::sum);
		if (tier != null) data.kills.computeIfAbsent(type.name(), k -> new HashMap<>()).merge(tier, 1, Integer::sum);
		String key = SlayerData.recordKey(type, tier);
		Long current = data.bestKillMs.get(key);
		boolean best = current == null || fightMs < current;
		if (best) data.bestKillMs.put(key, fightMs);
		store.markDirty();
		return best;
	}

	/** Kills per day for the last {@code days} days, oldest first. */
	public double[] killsPerDay(int days) {
		double[] out = new double[days];
		var today = java.time.LocalDate.now();
		for (int i = 0; i < days; i++) {
			out[i] = store.get().killsByDay.getOrDefault(today.minusDays(days - 1 - i).toString(), 0);
		}
		return out;
	}

	public int kills(SlayerType type, String tier) {
		return store.get().kills.getOrDefault(type.name(), java.util.Map.of()).getOrDefault(tier, 0);
	}
}
