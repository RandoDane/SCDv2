package com.scd.client.feature.dungeon;

import com.scd.client.hypixel.GameState;
import com.scd.logic.dungeon.Floor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the sidebar says about the current Catacombs run. Confirmed live: the area line reads
 * "The Catacombs (F6)" and "Cleared: 87% (273)" - the parenthesized number is Hypixel's own score,
 * which is only accurate once the boss room is reached.
 */
public record DungeonState(boolean inDungeon, String floor, Double clearPercent, Integer liveScore, Integer elapsedSeconds) {
	public static final DungeonState NONE = new DungeonState(false, null, null, null, null);

	private static final Pattern FLOOR = Pattern.compile("Catacombs\\s*\\((Entrance|E|[MF]\\d)\\)", Pattern.CASE_INSENSITIVE);
	private static final Pattern CLEARED = Pattern.compile("Cleared:\\s*(\\d+)%\\s*(?:\\((\\d+)\\))?");
	private static final Pattern TIME = Pattern.compile("Time Elapsed:\\s*(?:(\\d+)h\\s*)?(?:(\\d+)m\\s*)?(\\d+)s");

	public static DungeonState read(GameState game) {
		boolean in = false;
		String floor = null;
		Double clear = null;
		Integer score = null;
		Integer elapsed = null;
		for (String line : game.sidebar()) {
			if (line.contains("The Catacombs")) in = true;
			Matcher m;
			if ((m = FLOOR.matcher(line)).find()) floor = Floor.normalize(m.group(1));
			if ((m = CLEARED.matcher(line)).find()) {
				clear = Double.parseDouble(m.group(1));
				if (m.group(2) != null) score = Integer.parseInt(m.group(2));
			}
			if ((m = TIME.matcher(line)).find()) {
				int h = m.group(1) != null ? Integer.parseInt(m.group(1)) : 0;
				int min = m.group(2) != null ? Integer.parseInt(m.group(2)) : 0;
				elapsed = h * 3600 + min * 60 + Integer.parseInt(m.group(3));
			}
		}
		return in ? new DungeonState(true, floor, clear, score, elapsed) : NONE;
	}
}
