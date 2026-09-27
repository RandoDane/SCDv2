package com.scd.logic.dungeon.loot;

import com.scd.logic.Numbers;
import com.scd.logic.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A dungeon reward chest as Hypixel describes it in lore (Croesus and the end-of-run chest menu):
 * <pre>
 * Contents
 * Necron's Handle
 * Enchanted Book (Ultimate Wise V)
 * Wither Essence x20
 *
 * Cost
 * 2,000,000 Coins
 * Dungeon Chest Key
 * </pre>
 * "FREE" costs nothing. Names are mapped to item ids by {@link #idHint} where the rule is certain
 * (essence, enchanted books); everything else is looked up by name.
 */
public final class ChestLoot {
	public static final Pattern CHEST_NAME = Pattern.compile("^(Wood|Gold|Diamond|Emerald|Obsidian|Bedrock)(?: Chest)?$");
	private static final Pattern AMOUNT = Pattern.compile("^(.+?) x([\\d,]+)$");
	private static final Pattern ESSENCE = Pattern.compile("^(\\w+) Essence$");
	private static final Pattern BOOK = Pattern.compile("^Enchanted Book \\((.+) ([IVXLC]+)\\)$");
	private static final Pattern COINS = Pattern.compile("^([\\d,]+) Coins$");

	public record Entry(String name, int amount) {
	}

	/**
	 * @param contents what's inside
	 * @param coinCost coins to open (0 if free or not shown)
	 * @param needsKey a Dungeon Chest Key is part of the cost
	 */
	public record Chest(List<Entry> contents, long coinCost, boolean needsKey) {
	}

	private ChestLoot() {
	}

	/** Parses chest lore; null if it has no "Contents" section. */
	public static Chest parse(List<String> lore) {
		List<Entry> contents = new ArrayList<>();
		long coins = 0;
		boolean key = false;
		boolean inContents = false, inCost = false, sawContents = false;
		for (String raw : lore) {
			String line = Text.clean(raw).trim();
			if (line.equals("Contents")) {
				inContents = sawContents = true;
				inCost = false;
				continue;
			}
			if (line.equals("Cost")) {
				inCost = true;
				inContents = false;
				continue;
			}
			if (line.isEmpty()) {
				inContents = false;
				continue;
			}
			if (inContents) {
				Matcher m = AMOUNT.matcher(line);
				if (m.matches()) contents.add(new Entry(m.group(1), Integer.parseInt(m.group(2).replace(",", ""))));
				else contents.add(new Entry(line, 1));
			} else if (inCost) {
				Matcher m = COINS.matcher(line);
				if (m.matches()) coins = Long.parseLong(m.group(1).replace(",", ""));
				else if (line.equalsIgnoreCase("Dungeon Chest Key")) key = true;
			}
		}
		return sawContents ? new Chest(List.copyOf(contents), coins, key) : null;
	}

	/** Item id when the name alone determines it (essence, enchanted books); null otherwise. */
	public static String idHint(String name) {
		Matcher m = ESSENCE.matcher(name);
		if (m.matches()) return "ESSENCE_" + m.group(1).toUpperCase(Locale.ROOT);
		m = BOOK.matcher(name);
		if (m.matches()) {
			Integer level = Numbers.romanToInt(m.group(2));
			if (level == null) return null;
			String ench = m.group(1).toUpperCase(Locale.ROOT).replace("'", "").replace('-', '_').replace(' ', '_');
			return "ENCHANTMENT_" + ench + "_" + level;
		}
		return null;
	}
}
