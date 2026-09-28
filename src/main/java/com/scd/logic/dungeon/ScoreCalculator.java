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
 * On top of that: the total room count is solved from the clear percentage with the room engine's
 * known rooms as a lower bound, total secrets are exact once every room is identified, the Spirit
 * pet death discount is an input, and it reports how many secrets S / S+ still need.
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
	 * Everything the formula reads.
	 *
	 * @param completedRooms    tab list "Completed Rooms"
	 * @param clearPercent      sidebar "Cleared: X%" - with completedRooms, pins down the total room count
	 * @param knownRooms        rooms the room engine has seen (map + world); a lower bound for the total, 0 if unknown
	 * @param secretsFound      tab list "Secrets Found: N" (count), -1 if unknown
	 * @param secretsPercent    tab list "Secrets Found: X%"
	 * @param exactTotalSecrets sum of every room's secrets when all rooms are identified, else 0
	 * @param spiritPet         a Spirit pet in the party halves the first death's penalty (2 -> 1)
	 */
	public record Inputs(
			String floorKey,
			int completedRooms,
			double clearPercent,
			int knownRooms,
			int secretsFound,
			double secretsPercent,
			int exactTotalSecrets,
			int crypts,
			int incompletePuzzles,
			int deaths,
			boolean spiritPet,
			Integer timeElapsedSeconds,
			boolean inBossRoom,
			boolean bloodRoomCompleted,
			boolean mimicKilled,
			boolean princeKilled,
			boolean batKilled,
			boolean paulEzpz) {
		/** Short form for callers without room-engine data. */
		public Inputs(String floorKey, int completedRooms, double clearPercent, double secretsPercent, int crypts,
				int incompletePuzzles, int deaths, Integer timeElapsedSeconds, boolean inBossRoom, boolean bloodRoomCompleted,
				boolean mimicKilled, boolean princeKilled, boolean batKilled, boolean paulEzpz) {
			this(floorKey, completedRooms, clearPercent, 0, -1, secretsPercent, 0, crypts, incompletePuzzles, deaths, false,
					timeElapsedSeconds, inBossRoom, bloodRoomCompleted, mimicKilled, princeKilled, batKilled, paulEzpz);
		}
	}

	/**
	 * @param totalSecrets      exact (all rooms identified) or estimated from count + percent; 0 if unknown
	 * @param secretsForS       total secrets needed for 270 assuming a full clear, all puzzles and full speed; -1 if unknown
	 * @param secretsForSPlus   same for 300; may exceed totalSecrets when it's out of reach
	 */
	public record Breakdown(int total, int skill, int explore, int speed, int bonus, boolean entrance,
			int paddedCompletedRooms, int totalRoomsEstimate, int totalSecrets, boolean exactSecrets,
			int deathPenalty, int secretsForS, int secretsForSPlus) {
	}

	/** Null if the floor isn't recognized. */
	public static Breakdown compute(Inputs in) {
		FloorRequirement req = requirement(in.floorKey());
		if (req == null) return null;
		boolean entrance = Floor.ENTRANCE.equals(in.floorKey());

		int totalRooms = totalRooms(in.completedRooms(), in.clearPercent(), in.knownRooms());
		// Hypixel's Completed Rooms lags: the blood room only counts once the Watcher is beaten, and the
		// last room before boss often isn't ticked. Entrance has no such lag worth padding for.
		int padded = in.completedRooms() + (in.inBossRoom() || entrance ? 0 : 1) + (in.bloodRoomCompleted() ? 0 : 1);

		boolean exact = in.exactTotalSecrets() > 0 && in.secretsFound() >= 0;
		int totalSecrets = exact ? in.exactTotalSecrets() : estimateTotalSecrets(in.secretsFound(), in.secretsPercent());
		double secretsPct = exact ? 100.0 * in.secretsFound() / in.exactTotalSecrets() : in.secretsPercent();
		int deathPenalty = deathPenalty(in.deaths(), in.spiritPet());

		int skill = skill(padded, totalRooms, in.incompletePuzzles(), deathPenalty);
		int explore = explore(padded, totalRooms, secretsPct, req.secretPercent());
		int speed = speed(in.timeElapsedSeconds(), req.timeLimitSeconds());
		int bonus = bonus(in.crypts(), secretsPct, hasMimic(in.floorKey()), in.mimicKilled(), in.princeKilled(), in.batKilled(), in.paulEzpz());

		int total = entrance
				? Math.round(speed * 0.7f) + Math.round(explore * 0.7f) + Math.round(skill * 0.7f) + Math.round(bonus * 0.7f)
				: speed + explore + skill + bonus;
		int forS = entrance ? -1 : secretsNeeded(270, totalSecrets, req.secretPercent(), bonus, deathPenalty);
		int forSPlus = entrance ? -1 : secretsNeeded(300, totalSecrets, req.secretPercent(), bonus, deathPenalty);
		return new Breakdown(total, skill, explore, speed, bonus, entrance, padded, totalRooms, totalSecrets, exact,
				deathPenalty, forS, forSPlus);
	}

	/**
	 * Total rooms in the dungeon. Every count N (at least {@code knownRooms}) for which
	 * completed/N rounds or floors to the sidebar's clear percentage is a candidate - Hypixel's
	 * rounding isn't confirmed yet, so both are accepted - and the candidate nearest the direct
	 * estimate completed/pct wins. Falls back to Odin's {@code floor(completed / pct + 0.4)} when
	 * nothing fits (e.g. the two counters are momentarily out of sync).
	 */
	public static int totalRooms(int completed, double clearPercent, int knownRooms) {
		// Nothing cleared yet: assume the largest dungeon (Odin's default) so early estimates err low.
		if (completed <= 0 || clearPercent <= 0) return 36;
		int pct = (int) Math.round(clearPercent);
		int lower = Math.max(1, Math.max(knownRooms, completed));
		double direct = completed / (clearPercent / 100.0);
		int best = -1;
		for (int n = lower; n <= 36; n++) {
			double p = completed * 100.0 / n;
			boolean fits = (int) Math.floor(p + 1e-9) == pct || (int) Math.round(p) == pct;
			if (fits && (best < 0 || Math.abs(n - direct) < Math.abs(best - direct))) best = n;
		}
		return best > 0 ? best : Math.max(lower, (int) Math.floor(direct + 0.4));
	}

	/** found / pct, rounded - Odin's estimate. */
	public static int estimateTotalSecrets(int found, double percent) {
		if (found <= 0 || percent <= 0) return 0;
		return (int) Math.floor(100.0 / percent * found + 0.5);
	}

	public static int deathPenalty(int deaths, boolean spiritPet) {
		if (deaths <= 0) return 0;
		return deaths * 2 - (spiritPet ? 1 : 0);
	}

	/**
	 * Secrets (total, not remaining) needed to reach {@code target} if every room gets cleared, every
	 * puzzle solved and speed stays at 100: skill 100 - deaths, explore 60 + secret points, speed 100.
	 */
	public static int secretsNeeded(int target, int totalSecrets, int requiredPercent, int bonus, int deathPenalty) {
		if (totalSecrets <= 0) return -1;
		int points = target - 260 - bonus + deathPenalty;
		if (points <= 0) return 0;
		if (points > 40) return totalSecrets + 1;
		return (int) Math.ceil(totalSecrets * (requiredPercent / 100.0) * points / 40.0 - 1e-9);
	}

	static int skill(int completedRooms, int totalRooms, int incompletePuzzles, int deathPenalty) {
		int roomPortion = totalRooms > 0 ? clamp((int) (80.0 * completedRooms / totalRooms), 0, 80) : 0;
		return 20 + clamp(roomPortion - incompletePuzzles * 10 - deathPenalty, 0, 80);
	}

	static int explore(int completedRooms, int totalRooms, double secretsPercent, int requiredSecretPercent) {
		int roomPortion = totalRooms > 0 ? clamp((int) (60.0 * completedRooms / totalRooms), 0, 60) : 0;
		double effective = Math.min(requiredSecretPercent, secretsPercent);
		int secretsPortion = clamp((int) (40 * effective / requiredSecretPercent), 0, 40);
		return roomPortion + secretsPortion;
	}

	/**
	 * Hypixel's Speed score (wiki): T = seconds minus a floor offset (F1-3/F5 120, F4/F6 240, F7 360,
	 * M1-5 0, M6 120, M7 360 - always the time limit minus 8 minutes), then 100 until T = 480 and
	 * one point less every 12s to 90, every 24s to 80, every 30s to 70 and every 40s after, rounded up.
	 */
	public static int speed(Integer elapsedSeconds, int limitSeconds) {
		if (elapsedSeconds == null) return 100;
		double t = elapsedSeconds - (limitSeconds - 480);
		if (t < 480) return 100;
		if (t < 600) return (int) Math.ceil(140 - t / 12);
		if (t < 840) return (int) Math.ceil(115 - t / 24);
		if (t < 1140) return (int) Math.ceil(108 - t / 30);
		if (t < 3940) return (int) Math.ceil(98.5 - t / 40);
		return 0;
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
