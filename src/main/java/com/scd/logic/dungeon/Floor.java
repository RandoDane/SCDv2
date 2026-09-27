package com.scd.logic.dungeon;

import com.scd.logic.Numbers;

import java.util.List;
import java.util.Locale;

/**
 * Canonical Catacombs floor key: "E" (Entrance), "F1".."F7", "M1".."M7" - the same form the
 * sidebar shows in "The Catacombs (F6)" and every carry/score feature keys off.
 */
public final class Floor {
	public static final String ENTRANCE = "E";
	public static final List<String> CARRYABLE = List.of(
			"F1", "F2", "F3", "F4", "F5", "F6", "F7", "M1", "M2", "M3", "M4", "M5", "M6", "M7");

	private Floor() {
	}

	/** Normalizes sidebar/user text ("f6", "M6", "Entrance", "E") to a canonical key; null if not a floor. */
	public static String normalize(String text) {
		if (text == null) return null;
		String upper = text.trim().toUpperCase(Locale.ROOT);
		if (upper.equals("ENTRANCE") || upper.equals("E") || upper.equals("F0")) return ENTRANCE;
		if (upper.matches("[FM][1-7]")) return upper;
		return null;
	}

	/** Roman numeral + Master Mode flag from the completion report ("VI", true) -> "M6". */
	public static String fromRoman(String roman, boolean masterMode) {
		Integer n = Numbers.romanToInt(roman);
		if (n == null || n < 1 || n > 7) return null;
		return (masterMode ? "M" : "F") + n;
	}

	public static boolean isMasterMode(String key) {
		return key != null && key.startsWith("M");
	}
}
