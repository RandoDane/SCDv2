package com.scd.client.feature.slayer;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Everything Slayer persists, in one file (config/scd/slayer.json). Keys are SlayerType names. */
public final class SlayerData {
	/** "ZOMBIE_IV" -> fastest fight in ms. */
	public Map<String, Long> bestKillMs = new HashMap<>();
	/** type -> tier -> lifetime kill count seen by SCD. */
	public Map<String, Map<String, Integer>> kills = new HashMap<>();
	public Map<String, Rng> rng = new HashMap<>();
	/** Best-effort Daemon Shard RNG-XP multiplier (1.00-1.10), inferred from real meter syncs. */
	public double daemonMultiplier = 1.0;
	/** type -> item id -> drop tally. */
	public Map<String, Map<String, Drop>> drops = new HashMap<>();

	public static final class Rng {
		public Long storedXp;
		public Double chancePercent;
		public String selectedDrop;
		/** drop name -> meter XP needed to guarantee it. */
		public Map<String, Long> requiredByDrop = new LinkedHashMap<>();
		/** Authoritative value at the last real sync (chat line or menu read). */
		public Long lastSyncXp;
		/** Un-boosted XP we predicted from kills since that sync - compared on the next sync. */
		public long expectedSinceSync;
	}

	public static final class Drop {
		public String name;
		public long count;

		public Drop() {
		}

		public Drop(String name, long count) {
			this.name = name;
			this.count = count;
		}
	}

	static String recordKey(SlayerType type, String tier) {
		return type.name() + "_" + (tier != null ? tier : "?");
	}
}
