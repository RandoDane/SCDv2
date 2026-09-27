package com.scd.client.feature.dungeon;

import com.scd.client.hypixel.GameState;
import com.scd.logic.dungeon.RunMessages;
import com.scd.logic.dungeon.ScoreCalculator;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Live state of one Catacombs run and its score estimate. Reset on entering a dungeon and on every
 * completion (quick re-queues don't always leave the dungeon area long enough to notice).
 *
 * Score = {@link ScoreCalculator} over tab-list stats (rooms, secrets %, crypts, puzzles) plus chat
 * events (deaths, mimic/prince/bat, Watcher trial won, boss room entered). Once the run is 100%
 * cleared Hypixel's own sidebar score becomes trustworthy; it is captured once as a baseline and
 * only Speed decay and later deaths are applied on top, since those can still change mid-boss.
 */
public final class DungeonRun {
	private static final Pattern ROOMS = Pattern.compile("Completed Rooms:\\s*(\\d+)");
	private static final Pattern SECRETS = Pattern.compile("Secrets Found:\\s*([\\d.]+)%");
	private static final Pattern CRYPTS = Pattern.compile("Crypts:\\s*(\\d+)");
	private static final Pattern PUZZLES = Pattern.compile("Puzzles:\\s*\\((\\d+)\\)");
	/** "???: [✦]" undiscovered, "[✖]" failed, "[✔]" done. */
	private static final Pattern PUZZLE = Pattern.compile(".+?: \\[(.)]");

	public record Score(ScoreCalculator.Breakdown breakdown, int total, DungeonState state, int completedRooms,
			double secretsPercent, int crypts, int incompletePuzzles, int deaths, boolean inBoss, boolean baselined) {
	}

	private int deaths;
	private boolean mimic, prince, bat, bloodDone, inBoss;
	private Integer baselineScore;
	private int baselineSpeed, baselineDeaths;
	private boolean announced270, announced300;

	void reset() {
		deaths = 0;
		mimic = prince = bat = bloodDone = inBoss = false;
		baselineScore = null;
		announced270 = announced300 = false;
	}

	/** After a completion: clear run state but keep milestones muted until the next dungeon entry. */
	void markCompleted() {
		reset();
		announced270 = announced300 = true;
	}

	boolean inBoss() {
		return inBoss;
	}

	void onMessage(String text) {
		RunMessages.Event e = RunMessages.classify(text);
		if (e == null) return;
		switch (e) {
			case DEATH -> deaths++;
			case MIMIC_KILLED -> mimic = true;
			case PRINCE_KILLED -> prince = true;
			case BAT_KILLED -> bat = true;
			case BLOOD_ROOM_COMPLETED -> bloodDone = true;
			case BOSS_ENTERED -> inBoss = true;
		}
	}

	Score compute(DungeonState state, GameState game, boolean paul) {
		if (!state.inDungeon() || state.floor() == null) return null;
		List<String> tab = game.tabList();
		int rooms = intMatch(tab, ROOMS);
		double secrets = doubleMatch(tab, SECRETS);
		int crypts = intMatch(tab, CRYPTS);
		int puzzleCount = intMatch(tab, PUZZLES);
		int incomplete = 0;
		if (puzzleCount > 0) {
			for (String line : tab) {
				Matcher m = PUZZLE.matcher(line);
				if (m.matches() && "✖✦".contains(m.group(1))) incomplete++;
			}
		}
		var inputs = new ScoreCalculator.Inputs(state.floor(), rooms, state.clearPercent() != null ? state.clearPercent() : 0,
				secrets, crypts, incomplete, deaths, state.elapsedSeconds(), inBoss, bloodDone, mimic, prince, bat, paul);
		ScoreCalculator.Breakdown b = ScoreCalculator.compute(inputs);
		if (b == null) return null;
		int total = b.total();
		boolean baselined = false;
		if (state.clearPercent() != null && state.clearPercent() >= 100 && state.liveScore() != null) {
			if (baselineScore == null) {
				baselineScore = state.liveScore();
				baselineSpeed = b.speed();
				baselineDeaths = deaths;
			}
			total = baselineScore + (b.speed() - baselineSpeed) - (deaths - baselineDeaths) * 2;
			baselined = true;
		}
		return new Score(b, total, state, rooms, secrets, crypts, incomplete, deaths, inBoss, baselined);
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

	private static int intMatch(List<String> lines, Pattern p) {
		for (String l : lines) {
			Matcher m = p.matcher(l);
			if (m.find()) return Integer.parseInt(m.group(1));
		}
		return 0;
	}

	private static double doubleMatch(List<String> lines, Pattern p) {
		for (String l : lines) {
			Matcher m = p.matcher(l);
			if (m.find()) return Double.parseDouble(m.group(1));
		}
		return 0;
	}
}
