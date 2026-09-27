package com.scd.client.ui;

import java.util.List;

/**
 * A complete palette, including the semantic colors (positive/warning/negative) so a theme can
 * swap red/green for a colour-blind-safe pair. Surfaces are layered by lightness, not by shadows: the sidebar is darkest,
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
		int hudBackground,
		int positive,
		int warning,
		int negative) {

	private static final int POS = 0xFF4ADE80, WARN = 0xFFFFA45B, NEG = 0xFFFF6B6B;

	public static final List<Theme> PRESETS = List.of(
			new Theme("Midnight", 0xFFA37BFF, 0xFFE8EAF2, 0xFFA6ABC2, 0xFF6C728C,
					0xFF1E2130, 0xFF191C28, 0xFF262A3B, 0xFF2E3347, 0xFF343A50, 0xFF181B26, 0xFF3A4058, 0xE01E2130, POS, WARN, NEG),
			new Theme("Classic", 0xFFEF5B5B, 0xFFF4F6FA, 0xFFA7ADBB, 0xFF6B7280,
					0xFF1D1F24, 0xFF18191E, 0xFF26282F, 0xFF2F323B, 0xFF363944, 0xFF17181C, 0xFF3B3E48, 0xE01D1F24, POS, WARN, NEG),
			new Theme("Cyberpunk", 0xFF00E5FF, 0xFFF2F2FF, 0xFFB8C6E0, 0xFFB06BC9,
					0xFF1A1B2E, 0xFF15162A, 0xFF24253D, 0xFF2D2F4B, 0xFF3A3C5E, 0xFF141526, 0xFF3A3C5E, 0xE01A1B2E, POS, WARN, NEG),
			new Theme("Emerald", 0xFF3DDC97, 0xFFEEF7F1, 0xFFA9D8B8, 0xFF7FA98D,
					0xFF18221F, 0xFF141C19, 0xFF1F2B27, 0xFF263530, 0xFF2E3F38, 0xFF131A17, 0xFF34463F, 0xE018221F, POS, WARN, NEG),
			new Theme("Arctic", 0xFF6FD3FF, 0xFFEAF6FF, 0xFFBFD9EC, 0xFF7996AD,
					0xFF1A2230, 0xFF151C28, 0xFF222C3C, 0xFF293547, 0xFF33415A, 0xFF141A25, 0xFF364359, 0xE01A2230, POS, WARN, NEG),
			new Theme("Monochrome", 0xFFFFFFFF, 0xFFFFFFFF, 0xFFCBCBCB, 0xFF8A8A8A,
					0xFF1C1C1C, 0xFF171717, 0xFF262626, 0xFF2E2E2E, 0xFF3A3A3A, 0xFF151515, 0xFF3F3F3F, 0xE01C1C1C, POS, WARN, NEG),
			// ---- Built in OKLCH: one shared lightness ramp for surfaces (field 0.195 < sidebar 0.205 <
			// window 0.238 < card 0.278 < hover 0.318 < border 0.362) and text (0.955 / 0.80 / 0.70), so
			// every theme has identical contrast and only hue/character changes. Checked against WCAG 2.2:
			// primary text ~13:1 and secondary ~7.9:1 on cards (AAA), muted 5.5:1 (AA), accents 7.5-10:1
			// against surfaces (>= 3:1 for UI components).
			// Dusk: split-complementary: violet-navy surfaces, amber accent (~150 deg away).
			new Theme("Dusk", 0xFFF2AF48, 0xFFEFEFF7, 0xFFBCBCCA, 0xFF9C9CB1,
					0xFF1D1C32, 0xFF151429, 0xFF26253C, 0xFF302F47, 0xFF3B3B53, 0xFF131227, 0xFF46465F, 0xE01D1C32,
					0xFF6FD087, 0xFFFFA460, 0xFFF97770),
			// Tidepool: analogous: teal/blue-green surfaces and a sea-glass accent within 30 deg.
			new Theme("Tidepool", 0xFF6DDCC0, 0xFFE9F2F4, 0xFFB0C2C5, 0xFF8AA4A9,
					0xFF012429, 0xFF001C21, 0xFF0B2E33, 0xFF16383E, 0xFF224349, 0xFF00191F, 0xFF2E4F55, 0xE0012429,
					0xFF6FD087, 0xFFF7AC4D, 0xFFF97770),
			// Ember: complementary: warm charcoal surfaces, cool cyan accent (~170 deg away).
			new Theme("Ember", 0xFF4CD1EE, 0xFFF6EEEB, 0xFFC9BAB3, 0xFFAF998F,
					0xFF2B1A13, 0xFF23130B, 0xFF35241C, 0xFF402E26, 0xFF4B3931, 0xFF201009, 0xFF57443C, 0xE02B1A13,
					0xFF6FD087, 0xFFF7AC4D, 0xFFF97770),
			// Sakura: monochromatic: plum surfaces with a soft pink accent of the same family.
			new Theme("Sakura", 0xFFF89EC1, 0xFFF4EEF3, 0xFFC6B9C4, 0xFFAA98A7,
					0xFF281827, 0xFF20111F, 0xFF332231, 0xFF3D2C3B, 0xFF493747, 0xFF1E0F1D, 0xFF544252, 0xE0281827,
					0xFF56D298, 0xFFF7AC4D, 0xFFF8767A),
			// Signal: colour-vision safe: neutral graphite, yellow accent; success=blue, danger=orange.
			new Theme("Signal", 0xFFF9D544, 0xFFECF0F7, 0xFFB6BECB, 0xFF949FB2,
					0xFF1C1F24, 0xFF15171C, 0xFF26292D, 0xFF2F3238, 0xFF3B3E43, 0xFF12151A, 0xFF46494F, 0xE01C1F24,
					0xFF6DBDFF, 0xFFE7B643, 0xFFEF852E));

	public static Theme byName(String name) {
		for (Theme t : PRESETS) if (t.name.equalsIgnoreCase(name)) return t;
		return PRESETS.get(0);
	}

	/** The accent blended over a surface at the given strength (0..1) - for selected/soft-accent backgrounds. */
	public int accentOver(int surface, float strength) {
		return Ui.blend(surface, accent, strength);
	}
}
