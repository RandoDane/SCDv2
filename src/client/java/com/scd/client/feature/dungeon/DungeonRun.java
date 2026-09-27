package com.scd.client.feature.dungeon;

import com.scd.client.hypixel.GameState;
import com.scd.logic.dungeon.RunMessages;
import com.scd.logic.dungeon.ScoreCalculator;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Live state of one Catacombs run and its score estimate. Reset on entering a dungeon and on every
 * completion (quick re-queues don't always leave the dungeon area long enough to notice).
 *
 * Score = {@link ScoreCalculator} over tab-list stats (rooms, secrets, crypts, puzzles, team
 * deaths), the room engine (known rooms as a floor for the room total; exact secret total once
 * every room is identified) and chat/entity events (deaths, mimic death, prince/bat, Watcher, boss
 * entry). Once the run is 100% cleared Hypixel's own sidebar score becomes trustworthy; it is
 * captured once as a baseline and only Speed decay and later deaths are applied on top.
 */
public final class DungeonRun {
	private static final Pattern ROOMS = Pattern.compile("Completed Rooms:\\s*(\\d+)");
	private static final Pattern SECRETS = Pattern.compile("Secrets Found:\\s*([\\d.]+)%");
	private static final Pattern SECRET_COUNT = Pattern.compile("^\\s*Secrets Found:\\s*(\\d+)\\s*$");
	private static final Pattern CRYPTS = Pattern.compile("Crypts:\\s*(\\d+)");
	private static final Pattern TEAM_DEATHS = Pattern.compile("Team Deaths:\\s*(\\d+)");
	private static final Pattern PUZZLES = Pattern.compile("Puzzles:\\s*\\((\\d+)\\)");
	/** "???: [✦]" undiscovered, "[✖]" failed, "[✔]" done. */
	private static final Pattern PUZZLE = Pattern.compile(".+?: \\[(.)]");

	/** Run phases, in order; each split is the time from the run start to reaching it. */
	public enum Split {
		BLOOD_OPEN("Blood open"), BLOOD_DONE("Watcher"), BOSS("Boss"), CLEAR("Clear");

		public final String label;

		Split(String label) {
			this.label = label;
		}
	}

	public record Score(ScoreCalculator.Breakdown breakdown, int total, DungeonState state, int completedRooms,
			double secretsPercent, int secretsFound, int crypts, int incompletePuzzles, int deaths, boolean inBoss,
			boolean baselined, boolean mimic) {
	}

	/** What the room engine knows, for the score. */
	public record RoomFacts(int known, int identified, boolean allIdentified, int secretSum) {
		static final RoomFacts NONE = new RoomFacts(0, 0, false, 0);
	}

	private int chatDeaths;
	private boolean mimic, prince, bat, bloodDone, inBoss;
	private Integer baselineScore;
	private int baselineSpeed, baselinePenalty;
	private boolean announced270, announced300;
	private long startNanos;
	private final Map<Split, Long> splits = new EnumMap<>(Split.class);

	void reset() {
		chatDeaths = 0;
		mimic = prince = bat = bloodDone = inBoss = false;
		baselineScore = null;
		announced270 = announced300 = false;
		startNanos = 0;
		splits.clear();
	}

	/** After a completion: clear run state but keep milestones muted until the next dungeon entry. */
	void markCompleted() {
		reset();
		announced270 = announced300 = true;
	}

	boolean inBoss() {
		return inBoss;
	}

	void mimicKilled() {
		mimic = true;
	}

	boolean mimicDead() {
		return mimic;
	}

	/** Split times in ms from the run start (only those reached). */
	Map<Split, Long> splits() {
		return Map.copyOf(splits);
	}

	/** ms since the run started, or -1 before Mort spoke / the sidebar timer was seen. */
	long elapsedMs() {
		return startNanos == 0 ? -1 : (System.nanoTime() - startNanos) / 1_000_000;
	}

	void split(Split s) {
		if (startNanos != 0) splits.putIfAbsent(s, elapsedMs());
	}

	void onMessage(String text) {
		RunMessages.Event e = RunMessages.classify(text);
		if (e == null) return;
		switch (e) {
			// Mort is the real start; it replaces a clock anchored on the whole-second sidebar timer.
			case RUN_STARTED -> {
				if (splits.isEmpty()) startNanos = System.nanoTime();
			}
			case BLOOD_OPENED -> split(Split.BLOOD_OPEN);
			case DEATH -> chatDeaths++;
			case MIMIC_KILLED -> mimic = true;
			case PRINCE_KILLED -> prince = true;
			case BAT_KILLED -> bat = true;
			case BLOOD_ROOM_COMPLETED -> {
				bloodDone = true;
				split(Split.BLOOD_DONE);
			}
			case BOSS_ENTERED -> {
				inBoss = true;
				split(Split.BOSS);
			}
		}
	}

	Score compute(DungeonState state, GameState game, boolean paul, boolean spiritPet, RoomFacts rooms) {
		if (!state.inDungeon() || state.floor() == null) return null;
		// Joined mid-run (or missed Mort): anchor the clock to Hypixel's timer.
		if (startNanos == 0 && state.elapsedSeconds() != null && state.elapsedSeconds() > 0) {
			startNanos = System.nanoTime() - state.elapsedSeconds() * 1_000_000_000L;
		}
		List<String> tab = game.tabList();
		int completed = intMatch(tab, ROOMS, 0);
		double secrets = doubleMatch(tab, SECRETS);
		int secretCount = intMatch(tab, SECRET_COUNT, -1);
		int crypts = intMatch(tab, CRYPTS, 0);
		int deaths = Math.max(chatDeaths, intMatch(tab, TEAM_DEATHS, 0));
		int puzzleCount = intMatch(tab, PUZZLES, 0);
		int incomplete = 0;
		if (puzzleCount > 0) {
			for (String line : tab) {
				Matcher m = PUZZLE.matcher(line);
				if (m.matches() && "✖✦".contains(m.group(1))) incomplete++;
			}
		}
		double clear = state.clearPercent() != null ? state.clearPercent() : 0;
		int totalRooms = ScoreCalculator.totalRooms(completed, clear, rooms.known());
		int exactSecrets = rooms.allIdentified() && rooms.identified() == totalRooms ? rooms.secretSum() : 0;
		var inputs = new ScoreCalculator.Inputs(state.floor(), completed, clear, rooms.known(), secretCount, secrets, exactSecrets,
				crypts, incomplete, deaths, spiritPet, state.elapsedSeconds(), inBoss, bloodDone, mimic, prince, bat, paul);
		ScoreCalculator.Breakdown b = ScoreCalculator.compute(inputs);
		if (b == null) return null;
		int total = b.total();
		boolean baselined = false;
		if (clear >= 100 && state.liveScore() != null) {
			if (baselineScore == null) {
				baselineScore = state.liveScore();
				baselineSpeed = b.speed();
				baselinePenalty = b.deathPenalty();
			}
			total = baselineScore + (b.speed() - baselineSpeed) - (b.deathPenalty() - baselinePenalty);
			baselined = true;
		}
		return new Score(b, total, state, completed, secrets, secretCount, crypts, incomplete, deaths, inBoss, baselined, mimic);
	}

	/** Returns 270/300 the first time the estimate reaches it this run, else 0. */
	int milestone(int total) {
		if (total >= 300 && !announced300) {
			announced300 = true;
			announced270 = true;
			return 300;
		}
		if (total >= 270 && !announced270) {
			announced270 = true;
			return 270;
		}
		return 0;
	}

	private static int intMatch(List<String> lines, Pattern p, int fallback) {
		for (String l : lines) {
			Matcher m = p.matcher(l);
			if (m.find()) return Integer.parseInt(m.group(1));
		}
		return fallback;
	}

	private static double doubleMatch(List<String> lines, Pattern p) {
		for (String l : lines) {
			Matcher m = p.matcher(l);
			if (m.find()) return Double.parseDouble(m.group(1));
		}
		return 0;
	}
}
