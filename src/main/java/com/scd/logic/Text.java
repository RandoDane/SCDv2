package com.scd.logic;

import java.util.regex.Pattern;

/**
 * Normalizes text read off Hypixel's UI surfaces (sidebar, tab list, nameplates, lore, chat) into a
 * form that plain string/regex matching can rely on.
 *
 * Every surface SCD reads carries some mix of the same noise:
 * <ul>
 *   <li>legacy section-sign formatting codes - including bogus non-standard ones some other client
 *       mods inject mid-word, which the vanilla renderer skips but {@code getString()} keeps;</li>
 *   <li>Private Use Area glyphs Hypixel's resource-pack font renders as icons (e.g. the location pin
 *       before the sidebar's area line);</li>
 *   <li>U+00A0 non-breaking spaces used as padding, which {@link String#trim()} does not strip.</li>
 * </ul>
 * {@link #clean(String)} removes all three in one pass so no caller has to remember each case.
 */
public final class Text {
	private static final Pattern FORMATTING_CODE = Pattern.compile("§.?");
	private static final Pattern PRIVATE_USE_AREA = Pattern.compile("[-]");

	private Text() {
	}

	/** Strips formatting codes and icon glyphs, normalizes non-breaking spaces, and trims. Null-safe (returns ""). */
	public static String clean(String raw) {
		if (raw == null || raw.isEmpty()) return "";
		String s = FORMATTING_CODE.matcher(raw).replaceAll("");
		s = PRIVATE_USE_AREA.matcher(s).replaceAll("");
		return s.replace(' ', ' ').trim();
	}

	/** Only strips formatting codes, keeping every other character as-is. */
	public static String stripFormatting(String raw) {
		if (raw == null || raw.isEmpty()) return "";
		return FORMATTING_CODE.matcher(raw).replaceAll("");
	}

	/** Each character, with anything outside printable ASCII shown as [U+XXXX] - for diagnosing invisible-character mismatches. */
	public static String describeCodepoints(String s) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (i > 0) sb.append(' ');
			if (c >= 0x20 && c < 0x7F) sb.append(c);
			else sb.append(String.format(java.util.Locale.ROOT, "[U+%04X]", (int) c));
		}
		return sb.toString();
	}
}
