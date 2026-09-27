package com.scd.client.ui;

import java.util.List;

/**
 * A complete, immutable palette. Presets are defined by five seed colors (the same five that the
 * 1.x theme picker used - a background tint, the accent and three text shades); every surface color
 * is derived from the neutral base palette multiplied by the tint, so all themes keep identical
 * proportions and contrast, just recolored. Brightest text stays near-white on near-black panels
 * in every preset (well past WCAG's 4.5:1), and Monochrome relies on luminance alone.
 */
public record Theme(
		String name,
		int accent,
		int textPrimary,
		int textSecondary,
		int textMuted,
		int panelTop,
		int panelBottom,
		int border,
		int cardTop,
		int cardBottom,
		int cardHoverTop,
		int cardHoverBottom,
		int disabledTop,
		int disabledBottom,
		int trackOff,
		int field,
		int hudTint) {

	public static final List<Theme> PRESETS = List.of(
			seed("Classic", 0xFFFFFFFF, 0xFFEF5B5B, 0xFFA7ADBB, 0xFF6B7280, 0xFFF4F6FA),
			seed("Cyberpunk", 0xFFB9C6FF, 0xFF00E5FF, 0xFFB8C6E0, 0xFFB06BC9, 0xFFF2F2FF),
			seed("Emerald", 0xFFC9F5D9, 0xFFFFD54A, 0xFFA9D8B8, 0xFF7FA98D, 0xFFF5F0DC),
			seed("Arctic", 0xFFCFE8FF, 0xFF6FD3FF, 0xFFBFD9EC, 0xFF7996AD, 0xFFEAF6FF),
			seed("Monochrome", 0xFFFFFFFF, 0xFFFFFFFF, 0xFFCBCBCB, 0xFF8A8A8A, 0xFFFFFFFF));

	public static Theme byName(String name) {
		for (Theme t : PRESETS) if (t.name.equalsIgnoreCase(name)) return t;
		return PRESETS.get(0);
	}

	private static Theme seed(String name, int bgTint, int accent, int textSecondary, int textMuted, int textPrimary) {
		return new Theme(name, accent, textPrimary, textSecondary, textMuted,
				tint(0xF0181B24, bgTint), tint(0xF00F1117, bgTint), tint(0x30FFFFFF, bgTint),
				tint(0xFF2A303D, bgTint), tint(0xFF1C212B, bgTint),
				tint(0xFF343B4C, bgTint), tint(0xFF242A37, bgTint),
				tint(0xFF23262E, bgTint), tint(0xFF17191F, bgTint),
				tint(0xFF3A3F4B, bgTint), tint(0xFF0B0D12, bgTint), bgTint);
	}

	/** Channel-wise multiply (the same math sprite tinting uses), keeping {@code base}'s alpha. */
	static int tint(int base, int tint) {
		int a = base >>> 24;
		int r = ((base >> 16) & 0xFF) * ((tint >> 16) & 0xFF) / 255;
		int g = ((base >> 8) & 0xFF) * ((tint >> 8) & 0xFF) / 255;
		int b = (base & 0xFF) * (tint & 0xFF) / 255;
		return (a << 24) | (r << 16) | (g << 8) | b;
	}
}
