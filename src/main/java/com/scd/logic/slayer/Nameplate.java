package com.scd.logic.slayer;

import com.scd.logic.Numbers;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads state Hypixel appends to a Slayer boss's nameplate. The boss's real HP pool only exists in
 * this text ("Revenant Horror 380,000/400,000❤", "Voidgloom Seraph 12.4M❤") - the vanilla entity's
 * own health attribute is unrelated - so the nameplate is the single source of truth for HP.
 */
public final class Nameplate {
	private static final Pattern CURRENT_OVER_MAX = Pattern.compile(
			"([\\d,.]+)\\s*([kKmMbB]?)\\s*/\\s*([\\d,.]+)\\s*([kKmMbB]?)\\s*❤");
	private static final Pattern CURRENT_ONLY = Pattern.compile("([\\d,.]+)\\s*([kKmMbB]?)\\s*❤");
	private static final Pattern HITS_REMAINING = Pattern.compile("(\\d+)\\s*[Hh]its?\\b");
	private static final Pattern SHIELD_WORD = Pattern.compile("[Ss]hield(ed)?");
	/** "Name 0❤" - a corpse nameplate lingering for a tick or two after death. */
	private static final Pattern DEAD = Pattern.compile("(^|\\s)0\\s*❤");

	private Nameplate() {
	}

	/** current is always present; max only when the nameplate shows a current/max pair. */
	public record Health(double current, Double max) {
	}

	/** Voidgloom Seraph's Malevolent Hitshield - hitsRemaining is null when the nameplate shows the shield without a count. */
	public record Shield(boolean active, Integer hitsRemaining) {
		public static final Shield NONE = new Shield(false, null);
	}

	public static Health health(String nameplate) {
		if (nameplate == null) return null;
		try {
			Matcher both = CURRENT_OVER_MAX.matcher(nameplate);
			if (both.find()) {
				return new Health(Numbers.compact(both.group(1), both.group(2)), Numbers.compact(both.group(3), both.group(4)));
			}
			Matcher single = CURRENT_ONLY.matcher(nameplate);
			if (single.find()) return new Health(Numbers.compact(single.group(1), single.group(2)), null);
		} catch (NumberFormatException ignored) {
			// "1.2.3❤" style garbage - treat as unreadable this tick.
		}
		return null;
	}

	public static Shield shield(String nameplate) {
		if (nameplate == null) return Shield.NONE;
		Matcher hits = HITS_REMAINING.matcher(nameplate);
		if (hits.find()) return new Shield(true, Integer.parseInt(hits.group(1)));
		if (SHIELD_WORD.matcher(nameplate).find()) return new Shield(true, null);
		return Shield.NONE;
	}

	public static boolean isDead(String nameplate) {
		return nameplate != null && DEAD.matcher(nameplate).find();
	}
}
