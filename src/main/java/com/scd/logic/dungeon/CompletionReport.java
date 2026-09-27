package com.scd.logic.dungeon;

/**
 * Fields parsed from Hypixel's raw end-of-run report. Hypixel sends two bordered blocks per
 * completion - a short "EXTRA STATS" one immediately and a fuller "Floor N Stats" one about a
 * second later - so any field can be null depending on which block this came from.
 */
public record CompletionReport(
		String floorRoman,
		String floorKey,
		boolean masterMode,
		Integer teamScore,
		String scoreRank,
		String boss,
		String clearTime,
		Double cataExp,
		String classExpClass,
		Double classExp,
		String damageClass,
		Long totalDamage,
		Long allyHealing,
		Long enemiesKilled,
		Integer deaths,
		Integer secretsFound) {

	/** Same run's two blocks share floor + boss + time, so this identifies one real completion. */
	public String runSignature() {
		return floorKey + "|" + boss + "|" + clearTime;
	}
}
