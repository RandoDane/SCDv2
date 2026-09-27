package com.scd.logic.dungeon;

import com.scd.logic.Numbers;
import com.scd.logic.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stateful line-by-line parser for the dungeon completion report. Feed it every raw system message
 * in order; it buffers lines between two "▬▬▬" border lines and emits a {@link CompletionReport}
 * for each closed block that names a defeated boss (the guard against unrelated bordered text).
 * Wording confirmed against live F6/Sadan captures; Master Mode prefix per SkyHanni's patterns.
 */
public final class CompletionParser {
	private static final Pattern BORDER = Pattern.compile("^▬{8,}$");
	private static final Pattern FLOOR_LINE = Pattern.compile("Catacombs\\s*-\\s*Floor\\s+([IVX]+)", Pattern.CASE_INSENSITIVE);
	private static final Pattern ENTRANCE_LINE = Pattern.compile("Catacombs\\s*-\\s*Entrance", Pattern.CASE_INSENSITIVE);
	private static final Pattern MASTER_MODE = Pattern.compile("Master Mode", Pattern.CASE_INSENSITIVE);
	private static final Pattern SCORE_LINE = Pattern.compile("Team Score:\\s*(\\d+)\\s*\\(([^)]+)\\)");
	/** "☠ Defeated Sadan in 04m 46s", optionally with hours and a trailing "(NEW RECORD!)". */
	private static final Pattern DEFEATED_LINE = Pattern.compile("Defeated\\s+(.+?)\\s+in\\s+((?:\\d+h\\s*)?(?:\\d+m\\s*)?\\d+s)");
	private static final Pattern CATA_EXP_LINE = Pattern.compile("\\+([\\d,.]+)\\s+Catacombs Experience");
	private static final Pattern CLASS_EXP_LINE = Pattern.compile("\\+([\\d,.]+)\\s+(\\w+)\\s+Experience");
	private static final Pattern DAMAGE_LINE = Pattern.compile("Total Damage as (\\w+):\\s*([\\d,]+)");
	private static final Pattern HEALING_LINE = Pattern.compile("Ally Healing:\\s*([\\d,]+)");
	private static final Pattern KILLS_LINE = Pattern.compile("Enemies Killed:\\s*([\\d,]+)");
	private static final Pattern DEATHS_LINE = Pattern.compile("Deaths:\\s*(\\d+)");
	private static final Pattern SECRETS_LINE = Pattern.compile("Secrets Found:\\s*(\\d+)");
	/** A block is never this long - bail out rather than buffer forever if a closing border is missed. */
	private static final int MAX_BLOCK_LINES = 60;

	private final Consumer<CompletionReport> listener;
	private final List<String> buffer = new ArrayList<>();
	private boolean insideBlock;

	public CompletionParser(Consumer<CompletionReport> listener) {
		this.listener = listener;
	}

	public void accept(String rawMessage) {
		if (rawMessage == null) return;
		// A single system message can itself contain newlines (Hypixel sometimes batches lines).
		for (String line : Text.stripFormatting(rawMessage).split("\n")) acceptLine(line);
	}

	private void acceptLine(String line) {
		String trimmed = line.replace(' ', ' ').trim();
		if (BORDER.matcher(trimmed).matches()) {
			if (insideBlock) {
				CompletionReport report = parse(buffer);
				buffer.clear();
				insideBlock = false;
				if (report != null) listener.accept(report);
			} else {
				insideBlock = true;
				buffer.clear();
			}
			return;
		}
		if (!insideBlock) return;
		buffer.add(trimmed);
		if (buffer.size() > MAX_BLOCK_LINES) {
			buffer.clear();
			insideBlock = false;
		}
	}

	static CompletionReport parse(List<String> lines) {
		String floor = null;
		boolean entrance = false;
		boolean masterMode = false;
		Integer teamScore = null;
		String scoreRank = null;
		String boss = null;
		String clearTime = null;
		Double cataExp = null;
		String classExpClass = null;
		Double classExp = null;
		String damageClass = null;
		Long totalDamage = null;
		Long allyHealing = null;
		Long enemiesKilled = null;
		Integer deaths = null;
		Integer secretsFound = null;

		for (String line : lines) {
			Matcher m;
			if ((m = FLOOR_LINE.matcher(line)).find()) {
				floor = m.group(1);
				masterMode |= MASTER_MODE.matcher(line).find();
			} else if (ENTRANCE_LINE.matcher(line).find()) {
				entrance = true;
			}
			if ((m = SCORE_LINE.matcher(line)).find()) {
				teamScore = Numbers.parseIntOrNull(m.group(1));
				scoreRank = m.group(2).trim();
			}
			if ((m = DEFEATED_LINE.matcher(line)).find()) {
				boss = m.group(1).trim();
				clearTime = m.group(2).trim();
			}
			// Catacombs first: CLASS_EXP_LINE's generic \w+ would otherwise also match it.
			if ((m = CATA_EXP_LINE.matcher(line)).find()) {
				cataExp = Numbers.parseDoubleOrNull(m.group(1));
			} else if ((m = CLASS_EXP_LINE.matcher(line)).find()) {
				classExp = Numbers.parseDoubleOrNull(m.group(1));
				classExpClass = m.group(2);
			}
			if ((m = DAMAGE_LINE.matcher(line)).find()) {
				damageClass = m.group(1);
				totalDamage = Numbers.parseLongOrNull(m.group(2));
			}
			if ((m = HEALING_LINE.matcher(line)).find()) allyHealing = Numbers.parseLongOrNull(m.group(1));
			if ((m = KILLS_LINE.matcher(line)).find()) enemiesKilled = Numbers.parseLongOrNull(m.group(1));
			if ((m = DEATHS_LINE.matcher(line)).find()) deaths = Numbers.parseIntOrNull(m.group(1));
			if ((m = SECRETS_LINE.matcher(line)).find()) secretsFound = Numbers.parseIntOrNull(m.group(1));
		}

		if (boss == null) return null;
		String floorKey = entrance ? Floor.ENTRANCE : Floor.fromRoman(floor, masterMode);
		return new CompletionReport(floor, floorKey, masterMode, teamScore, scoreRank, boss, clearTime, cataExp,
				classExpClass, classExp, damageClass, totalDamage, allyHealing, enemiesKilled, deaths, secretsFound);
	}
}
