package com.scd.logic.chat;

import com.scd.logic.Numbers;
import com.scd.logic.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Slayer-related chat lines. Patterns are anchored at the start of the cleaned message: a genuine
 * Hypixel notification is the whole message, whereas the same words pasted into party/guild/public
 * chat always carry a channel or player-name prefix.
 */
public final class SlayerMessages {
	private static final Pattern LEVEL_UP = Pattern.compile("^(\\w+) Slayer LVL (\\d+)");
	private static final Pattern RNG_METER = Pattern.compile("^RNG Meter\\s*-\\s*([\\d,]+) Stored XP");
	private static final Pattern SACK_ENTRY = Pattern.compile("([+-][\\d,]+)\\s+(.+?)\\s+\\(([^()]+)\\)");

	private SlayerMessages() {
	}

	/** "Revenant Horror ... Slayer LVL 7" -> "Zombie"-style type word; null if not that message. */
	public static String levelUpTypeName(String message) {
		Matcher m = LEVEL_UP.matcher(Text.clean(message));
		return m.find() ? m.group(1) : null;
	}

	public static Long rngMeterStoredXp(String message) {
		Matcher m = RNG_METER.matcher(Text.clean(message));
		return m.find() ? Numbers.parseLongOrNull(m.group(1)) : null;
	}

	public enum QuestResult { STARTED, COMPLETE, FAILED }

	/** "  SLAYER QUEST STARTED!" / "COMPLETE!" / "FAILED!" (SkyHanni's SlayerApi patterns). */
	public static QuestResult questResult(String message) {
		String t = Text.clean(message);
		return switch (t) {
			case "SLAYER QUEST STARTED!" -> QuestResult.STARTED;
			case "SLAYER QUEST COMPLETE!" -> QuestResult.COMPLETE;
			case "SLAYER QUEST FAILED!" -> QuestResult.FAILED;
			default -> null;
		};
	}

	/** "YOU COCOONED YOUR SLAYER BOSS" (The Primordial) - the boss respawns at full HP ~6s later. */
	public static boolean isCocoon(String message) {
		return Text.clean(message).toUpperCase(Locale.ROOT).contains("COCOONED YOUR SLAYER BOSS");
	}

	/** One "+N Item Name (Sack Name)" entry of a sack-pickup hover tooltip. */
	public record SackGain(String itemName, int amount, String sackName) {
	}

	/**
	 * Every positive entry in a sack notification's hover text ("Added items: +2 Enchanted Ender
	 * Pearl (Enchanted Combat Sack)..."). Removals ("-N ...") are skipped. The visible chat line is
	 * only a collapsed "[Sacks] +2 items" summary, so callers pass the hover text when available.
	 */
	public static List<SackGain> sackGains(String text) {
		List<SackGain> out = new ArrayList<>();
		Matcher m = SACK_ENTRY.matcher(Text.clean(text));
		while (m.find()) {
			if (m.group(1).startsWith("-")) continue;
			Integer amount = Numbers.parseIntOrNull(m.group(1).substring(1));
			if (amount == null || amount <= 0) continue;
			out.add(new SackGain(m.group(2).trim(), amount, m.group(3).trim()));
		}
		return out;
	}
}
