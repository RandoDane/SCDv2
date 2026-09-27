package com.scd.client.feature.slayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This session's grind (not persisted): kills and fight/hunt times bucketed per tier - a Tier I and
 * a Tier IV cycle take wildly different time - plus XP and drop value. Switching to a different
 * <i>type</i> starts a fresh session; switching tier within a type just shows that tier's bucket.
 */
public final class SessionStats {
	private final Map<String, List<Long>> fights = new HashMap<>();
	private final Map<String, List<Long>> hunts = new HashMap<>();
	private SlayerType type;
	private String tier;
	private long xp;
	private double dropValue;
	private long startedMs = System.currentTimeMillis();

	void track(SlayerQuest quest) {
		if (quest == null) return;
		if (type != null && type != quest.type()) reset();
		type = quest.type();
		if (quest.tier() != null) tier = quest.tier();
	}

	private long lastKillMs;

	/** True while a grind is plausibly ongoing: a kill within the last 3 minutes. */
	public boolean recentlyActive() {
		return System.currentTimeMillis() - lastKillMs < 180_000;
	}

	void recordKill(String tier, long fightMs) {
		lastKillMs = System.currentTimeMillis();
		if (tier == null) return;
		this.tier = tier;
		fights.computeIfAbsent(tier, t -> new ArrayList<>()).add(fightMs);
	}

	void recordHunt(String tier, long huntMs) {
		if (tier == null) return;
		hunts.computeIfAbsent(tier, t -> new ArrayList<>()).add(huntMs);
	}

	void addXp(long amount) {
		xp += amount;
	}

	void addDropValue(double coins) {
		dropValue += coins;
	}

	public void reset() {
		fights.clear();
		hunts.clear();
		xp = 0;
		dropValue = 0;
		tier = null;
		startedMs = System.currentTimeMillis();
	}

	public SlayerType type() {
		return type;
	}

	public String tier() {
		return tier;
	}

	public int kills() {
		return bucket(fights).size();
	}

	public long avgFightMs() {
		return avg(bucket(fights));
	}

	public long avgHuntMs() {
		return avg(bucket(hunts));
	}

	/** Extrapolated from this tier's average full cycle (hunt + fight), not wall-clock time. */
	public double killsPerHour() {
		long cycle = avgFightMs() + avgHuntMs();
		return cycle > 0 ? 3_600_000.0 / cycle : 0;
	}

	public long xp() {
		return xp;
	}

	public double dropValue() {
		return dropValue;
	}

	/** Recorded drop value per hour of session time. */
	public double dropValuePerHour() {
		long elapsed = System.currentTimeMillis() - startedMs;
		return elapsed > 60_000 ? dropValue * 3_600_000.0 / elapsed : 0;
	}

	private List<Long> bucket(Map<String, List<Long>> m) {
		return tier != null ? m.getOrDefault(tier, List.of()) : List.of();
	}

	private static long avg(List<Long> xs) {
		if (xs.isEmpty()) return 0;
		long sum = 0;
		for (long x : xs) sum += x;
		return sum / xs.size();
	}
}
