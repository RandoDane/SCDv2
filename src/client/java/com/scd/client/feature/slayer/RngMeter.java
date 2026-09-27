package com.scd.client.feature.slayer;

import com.scd.client.core.ScdLog;
import com.scd.client.storage.JsonStore;
import com.scd.logic.slayer.SlayerTier;

import java.util.Map;

/**
 * RNG Meter progress per type, kept current between the two authoritative sources - the
 * "RNG Meter - N Stored XP" chat line after every quest, and the Slayer menu itself (see
 * {@link RngMenuReader}) - by adding each kill's expected XP in the meantime.
 *
 * Daemon Shard estimate: that shard multiplies meter XP by 1.00-1.10 and can't be read from the
 * client. Each real sync is compared against the un-boosted XP predicted since the previous one;
 * the ratio (snapped to the 11 possible levels, and only trusted over >= 2,000 predicted XP) is
 * used to make the in-between estimate more accurate. Real syncs always overwrite the estimate.
 */
public final class RngMeter {
	/** Base Slayer XP per tier (identical across bosses per the wiki's stat tables). */
	private static final Map<String, Long> XP_BY_TIER = Map.of("I", 5L, "II", 25L, "III", 100L, "IV", 500L, "V", 1500L);
	/** The meter only fills from Tier III upward. */
	private static final String MIN_TIER = "III";
	private static final double DAEMON_STEP = 0.01;
	private static final int DAEMON_MAX = 10;
	private static final long MIN_EXPECTED_TO_TRUST = 2_000;

	private final JsonStore<SlayerData> store;

	RngMeter(JsonStore<SlayerData> store) {
		this.store = store;
	}

	private SlayerData.Rng state(SlayerType type) {
		return store.get().rng.computeIfAbsent(type.name(), k -> new SlayerData.Rng());
	}

	public static Long baseXp(String tier) {
		return tier == null ? null : XP_BY_TIER.get(tier);
	}

	/** Authoritative value from Hypixel - reconciles the Daemon estimate, then overwrites. */
	void recordStoredXp(SlayerType type, long storedXp) {
		SlayerData.Rng s = state(type);
		if (Long.valueOf(storedXp).equals(s.lastSyncXp) && s.expectedSinceSync == 0) return;
		SlayerData data = store.get();
		if (s.lastSyncXp != null && s.expectedSinceSync >= MIN_EXPECTED_TO_TRUST && storedXp > s.lastSyncXp) {
			double implied = (storedXp - s.lastSyncXp) / (double) s.expectedSinceSync;
			double snapped = Math.round(implied / DAEMON_STEP) * DAEMON_STEP;
			double clamped = Math.max(1.0, Math.min(1.0 + DAEMON_MAX * DAEMON_STEP, snapped));
			if (clamped != data.daemonMultiplier) {
				ScdLog.info("Daemon Shard estimate " + data.daemonMultiplier + " -> " + clamped + " (" + type.displayName()
						+ " gained " + (storedXp - s.lastSyncXp) + " vs expected " + s.expectedSinceSync + ")");
			}
			data.daemonMultiplier = clamped;
		}
		s.lastSyncXp = storedXp;
		s.expectedSinceSync = 0;
		s.storedXp = storedXp;
		store.markDirty();
	}

	/** Adds a kill's XP (already mayor-boosted) to the running estimate. */
	void recordKillEstimate(SlayerType type, String tier, long xpGained) {
		if (!SlayerTier.atLeast(tier, MIN_TIER)) return;
		SlayerData.Rng s = state(type);
		s.expectedSinceSync += xpGained;
		long boosted = Math.round(xpGained * store.get().daemonMultiplier);
		s.storedXp = (s.storedXp != null ? s.storedXp : 0) + boosted;
		store.markDirty();
	}

	void recordSelectedDrop(SlayerType type, String drop) {
		state(type).selectedDrop = drop;
		store.markDirty();
	}

	void recordRequired(SlayerType type, String drop, long total) {
		state(type).requiredByDrop.put(drop, total);
		store.markDirty();
	}

	void recordChance(SlayerType type, double percent) {
		state(type).chancePercent = percent;
		store.markDirty();
	}

	public Long storedXp(SlayerType type) {
		return state(type).storedXp;
	}

	public String selectedDrop(SlayerType type) {
		return state(type).selectedDrop;
	}

	public Long required(SlayerType type, String drop) {
		return drop == null ? null : state(type).requiredByDrop.get(drop);
	}

	public Double chancePercent(SlayerType type) {
		return state(type).chancePercent;
	}

	/** Meter fill 0..1 toward the selected drop, if both numbers are known. */
	public Float progress(SlayerType type) {
		Long xp = storedXp(type);
		Long req = required(type, selectedDrop(type));
		if (xp == null || req == null || req <= 0) return null;
		return (float) Math.min(1.0, xp / (double) req);
	}

	public double daemonMultiplier() {
		return store.get().daemonMultiplier;
	}
}
