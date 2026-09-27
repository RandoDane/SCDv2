package com.scd.logic.slayer;

import java.util.List;
import java.util.Locale;

/** Slayer quest tiers I-V - kept as ordered text since that's how every Hypixel surface shows them. */
public final class SlayerTier {
	public static final List<String> ALL = List.of("I", "II", "III", "IV", "V");

	private SlayerTier() {
	}

	/** 0 for I .. 4 for V, -1 if unknown. */
	public static int rank(String tier) {
		return tier == null ? -1 : ALL.indexOf(tier.toUpperCase(Locale.ROOT));
	}

	public static boolean atLeast(String tier, String min) {
		int have = rank(tier);
		return have >= 0 && have >= rank(min);
	}

	/** Normalized ("iv" -> "IV") or null if not a tier. */
	public static String normalize(String tier) {
		if (tier == null) return null;
		String upper = tier.trim().toUpperCase(Locale.ROOT);
		return ALL.contains(upper) ? upper : null;
	}
}
