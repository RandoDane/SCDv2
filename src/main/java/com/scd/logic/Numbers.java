package com.scd.logic;

import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Number parsing and formatting shared by every feature - Hypixel mixes "1,234", "1.2k" and roman numerals freely. */
public final class Numbers {
	private static final Pattern COMPACT = Pattern.compile("^([\\d,]*\\.?\\d+)\\s*([kKmMbB]?)$");
	private static final Map<String, Integer> ROMAN = Map.of(
			"I", 1, "II", 2, "III", 3, "IV", 4, "V", 5, "VI", 6, "VII", 7, "VIII", 8, "IX", 9, "X", 10);

	private Numbers() {
	}

	/** Multiplier for a k/m/b suffix (case-insensitive); 1 for an empty or unknown suffix. */
	public static double suffixMultiplier(String suffix) {
		if (suffix == null) return 1;
		return switch (suffix.toUpperCase(Locale.ROOT)) {
			case "K" -> 1_000d;
			case "M" -> 1_000_000d;
			case "B" -> 1_000_000_000d;
			default -> 1d;
		};
	}

	/** "1,234.5" + "k" -> 1234500. Throws NumberFormatException on bad digits - callers use it on regex-validated groups. */
	public static double compact(String digits, String suffix) {
		return Double.parseDouble(digits.replace(",", "")) * suffixMultiplier(suffix);
	}

	/** Parses user-typed or displayed amounts such as "50000", "1,300,000", "1.3m", "800k". Empty if not a valid number. */
	public static OptionalDouble parseCompact(String text) {
		if (text == null) return OptionalDouble.empty();
		Matcher m = COMPACT.matcher(text.trim());
		if (!m.matches()) return OptionalDouble.empty();
		try {
			return OptionalDouble.of(compact(m.group(1), m.group(2)));
		} catch (NumberFormatException e) {
			return OptionalDouble.empty();
		}
	}

	/** {@link #parseCompact} rounded to a whole number. */
	public static OptionalLong parseCompactLong(String text) {
		OptionalDouble d = parseCompact(text);
		return d.isPresent() ? OptionalLong.of(Math.round(d.getAsDouble())) : OptionalLong.empty();
	}

	/** Lenient integer parse that tolerates thousands separators; null on failure. */
	public static Integer parseIntOrNull(String text) {
		if (text == null) return null;
		try {
			return Integer.parseInt(text.replace(",", "").trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	public static Long parseLongOrNull(String text) {
		if (text == null) return null;
		try {
			return Long.parseLong(text.replace(",", "").trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	public static Double parseDoubleOrNull(String text) {
		if (text == null) return null;
		try {
			return Double.parseDouble(text.replace(",", "").trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** "IV" -> 4 for I..X; null for anything else. */
	public static Integer romanToInt(String roman) {
		return roman == null ? null : ROMAN.get(roman.trim().toUpperCase(Locale.ROOT));
	}

	/** 4 -> "IV" for 1..10; the arabic number as text otherwise. */
	public static String intToRoman(int n) {
		for (var e : ROMAN.entrySet()) {
			if (e.getValue() == n) return e.getKey();
		}
		return Integer.toString(n);
	}

	/** Coin amounts: 950 -> "950", 1_234 -> "1.2K", 3_400_000 -> "3.4M", 2e9 -> "2.0B". */
	public static String coins(double n) {
		return coins(n, 1);
	}

	public static String coins(double n, int decimals) {
		double abs = Math.abs(n);
		String fmt = "%." + decimals + "f";
		if (abs >= 1_000_000_000) return String.format(Locale.ROOT, fmt + "B", n / 1_000_000_000);
		if (abs >= 1_000_000) return String.format(Locale.ROOT, fmt + "M", n / 1_000_000);
		if (abs >= 1_000) return String.format(Locale.ROOT, fmt + "K", n / 1_000);
		if (n == Math.rint(n)) return String.format(Locale.ROOT, "%d", (long) n);
		return String.format(Locale.ROOT, fmt, n);
	}

	/** Item counts: 2_541 -> "2.5K", 15_000_000 -> "15M" - like coins() but drops a trailing ".0". */
	public static String compactCount(long n) {
		long abs = Math.abs(n);
		if (abs >= 1_000_000_000) return trimZero(n / 1_000_000_000.0) + "B";
		if (abs >= 1_000_000) return trimZero(n / 1_000_000.0) + "M";
		if (abs >= 1_000) return trimZero(n / 1_000.0) + "K";
		return Long.toString(n);
	}

	/** Exact value (with separators) below {@code minToShorten}, compact above it. */
	public static String compactCount(long n, long minToShorten) {
		return Math.abs(n) < minToShorten ? String.format(Locale.ROOT, "%,d", n) : compactCount(n);
	}

	/** 125_000 ms -> "2:05"; an hour or more -> "1:02:05". */
	public static String duration(long ms) {
		long totalSeconds = Math.max(0, ms) / 1000;
		long h = totalSeconds / 3600, m = (totalSeconds % 3600) / 60, s = totalSeconds % 60;
		return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) : String.format(Locale.ROOT, "%d:%02d", m, s);
	}

	/** "04m 46s", "1h 02m 03s", "4m46s" or "42s" -> milliseconds; 0 if unparseable. */
	public static long parseClearTimeMs(String clearTime) {
		if (clearTime == null) return 0;
		Matcher m = Pattern.compile("(?:(\\d+)h)?\\s*(?:(\\d+)m)?\\s*(\\d+)s").matcher(clearTime);
		if (!m.find()) return 0;
		long hours = m.group(1) != null ? Long.parseLong(m.group(1)) : 0;
		long minutes = m.group(2) != null ? Long.parseLong(m.group(2)) : 0;
		return ((hours * 60 + minutes) * 60 + Long.parseLong(m.group(3))) * 1000;
	}

	public static String percent(double value, int decimals) {
		return String.format(Locale.ROOT, "%." + decimals + "f%%", value);
	}

	private static String trimZero(double n) {
		String s = String.format(Locale.ROOT, "%.1f", n);
		return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
	}
}
