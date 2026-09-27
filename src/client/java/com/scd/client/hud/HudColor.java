package com.scd.client.hud;

import com.scd.client.ui.Theme;

import java.util.function.ToIntFunction;

/**
 * The customizable color roles every HUD shares. Defaults follow the active theme; a player
 * override for a role is stored per HUD in {@code HudLayout.colors} under {@link #id()}. The ids
 * match the 1.x slot ids, so imported overrides keep working.
 */
public enum HudColor {
	BACKGROUND("panel", "Background", Theme::hudBackground),
	TITLE("bossTitle", "Title", Theme::accent),
	TEXT("bossText", "Body text", Theme::textSecondary),
	LABEL("statsLabel", "Labels", Theme::textMuted),
	VALUE("statsValue", "Values", Theme::textPrimary);

	private final String id;
	private final String label;
	private final ToIntFunction<Theme> themeDefault;

	HudColor(String id, String label, ToIntFunction<Theme> themeDefault) {
		this.id = id;
		this.label = label;
		this.themeDefault = themeDefault;
	}

	public String id() {
		return id;
	}

	public String label() {
		return label;
	}

	public int themeDefault(Theme theme) {
		return themeDefault.applyAsInt(theme);
	}
}
