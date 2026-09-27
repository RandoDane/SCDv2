package com.scd.client.hud;

import com.scd.client.config.ConfigManager;
import com.scd.client.config.HudLayout;
import com.scd.client.ui.Rows;
import com.scd.client.ui.ScdScreen;
import com.scd.client.ui.Ui;
import com.scd.client.ui.widget.FlatButton;
import com.scd.client.ui.widget.TextField;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;

/**
 * Appearance of one HUD: size, background and a color per {@link HudColor} role. The HUD itself is
 * shown live (sample content) at its real position while this screen is open, so every change is
 * visible immediately. A cleared hex field falls back to the theme color.
 */
public final class HudStyleScreen extends ScdScreen {
	private final HudManager huds;
	private final ConfigManager config;
	private final HudElement element;

	public HudStyleScreen(Screen parent, HudManager huds, ConfigManager config, HudElement element) {
		super(element.name() + " Appearance", parent);
		this.huds = huds;
		this.config = config;
		this.element = element;
	}

	@Override
	protected void init() {
		huds.setPreview(element.id());
		super.init();
	}

	@Override
	protected void onClosing() {
		huds.setPreview(null);
		config.save();
	}

	@Override
	protected void build(Rows rows) {
		HudLayout layout = huds.layout(element);
		rows.header("Layout");
		rows.slider("Size", 0.5, 2.5, 0.1, () -> layout.scale, v -> layout.scale = (float) (double) v,
				v -> Math.round(v * 100) + "%");
		rows.toggle("Background panel", null, () -> layout.background, v -> layout.background = v);

		rows.header("Colors");
		rows.note("Hex colors like #FF5555. Leave a field empty to follow the theme.");
		for (HudColor role : HudColor.values()) {
			int fieldW = 70;
			int x0 = rows.x();
			TextField field = new TextField(x0 + rows.width() - fieldW - 22, 0, fieldW, 16, "theme",
					layout.colors.containsKey(role.id()) ? HudEditorScreen.hex(layout.colors.get(role.id())) : "",
					c -> Character.digit(c, 16) >= 0 || c == '#', s -> apply(layout, role, s));
			FlatButton reset = new FlatButton(x0 + rows.width() - 16, 0, 16, 16, "×", () -> {
				layout.colors.remove(role.id());
				rebuild();
			}).tooltip("Use the theme color");
			rows.custom(16, (g, x, y, w, mx, my) -> {
				Ui.text(g, role.label(), x0, y + 4, Ui.theme().textSecondary());
				int sx = x0 + w - fieldW - 42;
				g.fill(sx, y + 1, sx + 14, y + 15, 0xFF000000 | HudManager.color(layout, role));
				g.outline(sx, y + 1, 14, 14, Ui.theme().border());
			}, List.of(field, reset));
		}
		rows.space(4);
		rows.button("Reset appearance", () -> {
			layout.colors.clear();
			layout.scale = 1f;
			layout.background = true;
			rebuild();
		}).danger();
	}

	private static void apply(HudLayout layout, HudColor role, String text) {
		String digits = text.startsWith("#") ? text.substring(1) : text;
		if (digits.isEmpty()) {
			layout.colors.remove(role.id());
		} else if (digits.length() == 6) {
			try {
				int alpha = role == HudColor.BACKGROUND ? 0xE0000000 : 0xFF000000; // keep the panel translucent
				layout.colors.put(role.id(), alpha | Integer.parseInt(digits, 16));
			} catch (NumberFormatException ignored) {
				// keep the last valid color until the input is complete
			}
		}
	}
}
