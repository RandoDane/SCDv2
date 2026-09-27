package com.scd.logic.dungeon;

import java.util.Map;

/**
 * Live Catacombs score estimate - a pure function of the run's observable state. The formula is a
 * port of Skyblocker's DungeonScore (LGPL-3.0), cross-checked against Odin's independent
 * implementation, including:
 * <ul>
 *   <li>Odin's "virtual completed rooms": +1 while the boss room isn't reached and +1 until the
 *       Watcher trial is won, compensating for Hypixel's Completed Rooms counter lagging reality;</li>
 *   <li>puzzles count against Skill until solved, whether undiscovered ("✦") or failed ("✖");</li>
 *   <li>a real tiered Speed decay (Odin just assumes a flat 100);</li>
 *   <li>entrance runs scaled by 0.7 per component.</li>
 * </ul>
 * Not modelled: the Legendary Spirit pet halving the first death penalty (needs a profile lookup).
 */
public final class ScoreCalculator {
	/** Required secret percentage and time budget (seconds) per floor - Skyblocker's FloorRequirement table. */
	public record FloorRequirement(int secretPercent, int timeLimitSeconds) {
	}

	private static final Map<String, FloorRequirement> REQUIREMENTS = Map.ofEntries(
			Map.entry(Floor.ENTRANCE, new FloorRequirement(30, 1200)),
			Map.entry("F1", new FloorRequirement(30, 600)),
			Map.entry("F2", new FloorRequirement(40, 600)),
			Map.entry("F3", new FloorRequirement(50, 600)),
			Map.entry("F4", new FloorRequirement(60, 720)),
			Map.entry("F5", new FloorRequirement(70, 600)),
			Map.entry("F6", new FloorRequirement(85, 720)),
			Map.entry("F7", new FloorRequirement(100, 840)),
			Map.entry("M1", new FloorRequirement(100, 480)),
			Map.entry("M2", new FloorRequirement(100, 480)),
			Map.entry("M3", new FloorRequirement(100, 480)),
			Map.entry("M4", new FloorRequirement(100, 480)),
			Map.entry("M5", new FloorRequirement(100, 480)),
			Map.entry("M6", new FloorRequirement(100, 600)),
			Map.entry("M7", new FloorRequirement(100, 840)));

	private ScoreCalculator() {
	}

	public static FloorRequirement requirement(String floorKey) {
		return floorKey == null ? null : REQUIREMENTS.get(floorKey);
	}

	/**
	 * Everything the formula reads. clearPercent is the sidebar's "Cleared: X%" and is used to
	 * estimate the dungeon's total room count from completedRooms.
	 */
	public record Inputs(
			String floorKey,
			int completedRooms,
			double clearPercent,
			double secretsPercent,
			int crypts,
			int incompletePuzzles,
			int deaths,
			Integer timeElapsedSeconds,
			boolean inBossRoom,
			boolean bloodRoomCompleted,
			boolean mimicKilled,
			boolean princeKilled,
			boolean batKilled,
			boolean paulEzpz) {
	}

	public record Breakdown(int total, int skill, int explore, int speed, int bonus, boolean entrance,
			int paddedCompletedRooms, int totalRoomsEstimate) {
	}

	/** Null if the floor isn't recognized. */
	public static Breakdown compute(Inputs in) {
		FloorRequirement req = requirement(in.floorKey());
		if (req == null) return null;
		boolean entrance = Floor.ENTRANCE.equals(in.floorKey());

		double clearFraction = in.clearPercent() / 100.0;
		int totalRooms = clearFraction > 0 ? (int) Math.round(in.completedRooms() / clearFraction) : 0;
		// Entrance has no blood-to-boss lag worth padding for (Skyblocker skips it there too).
		int padded = in.completedRooms() + (in.inBossRoom() || entrance ? 0 : 1) + (in.bloodRoomCompleted() ? 0 : 1);

		int skill = skill(padded, totalRooms, in.incompletePuzzles(), in.deaths());
		int explore = explore(padded, totalRooms, in.secretsPercent(), req.secretPercent());
		int speed = speed(in.timeElapsedSeconds(), req.timeLimitSeconds());
		int bonus = bonus(in.crypts(), in.secretsPercent(), hasMimic(in.floorKey()), in.mimicKilled(), in.princeKilled(), in.batKilled(), in.paulEzpz());

		int total = entrance
				? Math.round(speed * 0.7f) + Math.round(explore * 0.7f) + Math.round(skill * 0.7f) + Math.round(bonus * 0.7f)
				: speed + explore + skill + bonus;
		return new Breakdown(total, skill, explore, speed, bonus, entrance, padded, totalRooms);
	}

	static int skill(int completedRooms, int totalRooms, int incompletePuzzles, int deaths) {
		int roomPortion = totalRooms > 0 ? clamp((int) (80.0 * completedRooms / totalRooms), 0, 80) : 0;
		return 20 + clamp(roomPortion - incompletePuzzles * 10 - deaths * 2, 0, 80);
	}

	static int explore(int completedRooms, int totalRooms, double secretsPercent, int requiredSecretPercent) {
		int roomPortion = totalRooms > 0 ? clamp((int) (60.0 * completedRooms / totalRooms), 0, 60) : 0;
		double effective = Math.min(requiredSecretPercent, secretsPercent);
		int secretsPortion = clamp((int) (40 * effective / requiredSecretPercent), 0, 40);
		return roomPortion + secretsPortion;
	}

	static int speed(Integer elapsedSeconds, int limitSeconds) {
		if (elapsedSeconds == null || elapsedSeconds < limitSeconds) return 100;
		double over = (elapsedSeconds - limitSeconds) / (double) limitSeconds * 100;
		if (over < 20) return 100 - (int) (over / 2);
		if (over < 40) return 100 - (int) (10 + (over - 20) / 4);
		if (over < 50) return 100 - (int) (15 + (over - 40) / 5);
		if (over < 60) return 100 - (int) (17 + (over - 50) / 6);
		return clamp(100 - (int) (18 + 2.0 / 3 + (over - 60) / 7), 0, 100);
	}

	/** Mimics only spawn on floors 6 and 7 (normal and Master Mode). */
	public static boolean hasMimic(String floorKey) {
		return floorKey != null && floorKey.matches("[FM][67]");
	}

	static int bonus(int crypts, double secretsPercent, boolean floorHasMimic, boolean mimic, boolean prince, boolean bat, boolean paul) {
		// On mimic floors 100% secrets is unreachable without opening the mimic chest, so it implies
		// the mimic died. 1.x applied this on every floor, over-scoring F1-F5 full-secret runs by 2.
		int mimicScore = (mimic || (floorHasMimic && secretsPercent >= 100)) ? 2 : 0;
		return (paul ? 10 : 0) + clamp(crypts, 0, 5) + mimicScore + (prince ? 1 : 0) + (bat ? 1 : 0);
	}

	/** Hypixel's rank cut-offs for a score. */
	public static String rank(int score) {
		if (score >= 300) return "S+";
		if (score >= 270) return "S";
		if (score >= 230) return "A";
		if (score >= 160) return "B";
		if (score >= 100) return "C";
		return "D";
	}

	private static int clamp(int v, int min, int max) {
		return Math.max(min, Math.min(max, v));
	}
}
