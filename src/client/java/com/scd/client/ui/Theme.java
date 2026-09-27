package com.scd.client.ui;

import java.util.List;

/**
 * A complete palette. Surfaces are layered by lightness, not by shadows: the sidebar is darkest,
 * then the window, then cards; a hairline border one step lighter separates them. Text comes in
 * three weights of contrast, and a single accent carries every interactive/selected state.
 * The HUD background keeps a little transparency so the world still reads through.
 */
public record Theme(
		String name,
		int accent,
		int textPrimary,
		int textSecondary,
		int textMuted,
		int window,
		int sidebar,
		int card,
		int cardHover,
		int border,
		int field,
		int trackOff,
		int hudBackground) {

	public static final List<Theme> PRESETS = List.of(
			new Theme("Midnight", 0xFFA37BFF, 0xFFE8EAF2, 0xFFA6ABC2, 0xFF6C728C,
					0xFF1E2130, 0xFF191C28, 0xFF262A3B, 0xFF2E3347, 0xFF343A50, 0xFF181B26, 0xFF3A4058, 0xE01E2130),
			new Theme("Classic", 0xFFEF5B5B, 0xFFF4F6FA, 0xFFA7ADBB, 0xFF6B7280,
					0xFF1D1F24, 0xFF18191E, 0xFF26282F, 0xFF2F323B, 0xFF363944, 0xFF17181C, 0xFF3B3E48, 0xE01D1F24),
			new Theme("Cyberpunk", 0xFF00E5FF, 0xFFF2F2FF, 0xFFB8C6E0, 0xFFB06BC9,
					0xFF1A1B2E, 0xFF15162A, 0xFF24253D, 0xFF2D2F4B, 0xFF3A3C5E, 0xFF141526, 0xFF3A3C5E, 0xE01A1B2E),
			new Theme("Emerald", 0xFF3DDC97, 0xFFEEF7F1, 0xFFA9D8B8, 0xFF7FA98D,
					0xFF18221F, 0xFF141C19, 0xFF1F2B27, 0xFF263530, 0xFF2E3F38, 0xFF131A17, 0xFF34463F, 0xE018221F),
			new Theme("Arctic", 0xFF6FD3FF, 0xFFEAF6FF, 0xFFBFD9EC, 0xFF7996AD,
					0xFF1A2230, 0xFF151C28, 0xFF222C3C, 0xFF293547, 0xFF33415A, 0xFF141A25, 0xFF364359, 0xE01A2230),
			new Theme("Monochrome", 0xFFFFFFFF, 0xFFFFFFFF, 0xFFCBCBCB, 0xFF8A8A8A,
					0xFF1C1C1C, 0xFF171717, 0xFF262626, 0xFF2E2E2E, 0xFF3A3A3A, 0xFF151515, 0xFF3F3F3F, 0xE01C1C1C));

	public static Theme byName(String name) {
		for (Theme t : PRESETS) if (t.name.equalsIgnoreCase(name)) return t;
		return PRESETS.get(0);
	}

	/** The accent blended over a surface at the given strength (0..1) - for selected/soft-accent backgrounds. */
	public int accentOver(int surface, float strength) {
		return Ui.blend(surface, accent, strength);
	}
}
