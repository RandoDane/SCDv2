package com.scd.client.screen;

import com.scd.client.ScdMod;
import com.scd.client.config.HudLayout;
import com.scd.client.config.ScdConfig;
import com.scd.client.hud.HudColor;
import com.scd.client.hud.HudEditorScreen;
import com.scd.client.hud.HudElement;
import com.scd.client.hud.HudManager;
import com.scd.client.ui.Rows;
import com.scd.client.ui.ScdScreen;
import com.scd.client.ui.Theme;
import com.scd.client.ui.Ui;
import com.scd.client.ui.widget.FlatButton;
import com.scd.client.ui.widget.TextField;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;

/** Everything visual in one place: menu theme, font and text size, and each HUD's size, panel and colors. */
public final class AppearanceScreen extends ScdScreen {
	private final ScdMod mod;

	public AppearanceScreen(Screen parent, ScdMod mod) {
		super("Appearance", parent);
		this.mod = mod;
	}

	/** Opens the page with one HUD's block expanded (from the HUD editor). */
	public static AppearanceScreen forHud(Screen parent, ScdMod mod, HudElement element) {
		AppearanceScreen s = new AppearanceScreen(parent, mod);
		s.expand("hud." + element.id());
		return s;
	}

	@Override
	protected String navKey() {
		return "appearance";
	}

	@Override
	protected void onClosing() {
		mod.configManager.save();
	}

	@Override
	protected void build(Rows rows) {
		ScdConfig c = mod.config();
		rows.group("menus", "Menus", null, true, g -> {
			g.cycle("Theme", Theme.PRESETS, Ui::theme, t -> {
				Ui.setTheme(t);
				c.general.theme = t.name();
				rebuild();
			}, Theme::name);
			g.toggle("Smooth font", "Anti-aliased font in SCD menus and HUDs", () -> c.general.smoothFont, v -> {
				c.general.smoothFont = v;
				Ui.setSmoothFont(v);
				rebuild();
			});
			int[] sizes = Ui.textSizes();
			g.slider("Text size", sizes[0], sizes[sizes.length - 1], 10, () -> c.general.menuTextSize, v -> {
				int size = (int) Math.round(v);
				if (size == c.general.menuTextSize) return;
				c.general.menuTextSize = size;
				// Text is measured when drawn, so no rebuild (that would drop the slider mid-drag).
				Ui.setMenuTextSize(size);
			}, v -> Math.round(v) + "%");
		});

		rows.header("HUDs");
		rows.buttons(List.of("Move HUDs..."), List.of(
				() -> Minecraft.getInstance().gui.setScreen(new HudEditorScreen(this, mod.huds, mod.configManager))));
		HudManager huds = mod.huds;
		for (HudElement e : huds.elements()) {
			rows.group("hud." + e.id(), e.name(), () -> e.enabled() ? "shown" : "off", false, g -> hudStyle(g, huds.layout(e)));
		}
	}

	private void hudStyle(Rows rows, HudLayout layout) {
		rows.slider("Size", 0.5, 2.5, 0.1, () -> layout.scale, v -> layout.scale = (float) (double) v, v -> Math.round(v * 100) + "%");
		rows.toggle("Background panel", null, () -> layout.background, v -> layout.background = v);
		rows.note("Colors: hex like #FF5555, empty = follow the theme.");
		for (HudColor role : HudColor.values()) {
			int fieldW = 70;
			int x0 = rows.x();
			TextField field = new TextField(x0 + rows.width() - fieldW - 22, 0, fieldW, 16, "theme",
					layout.colors.containsKey(role.id()) ? HudEditorScreen.hex(layout.colors.get(role.id())) : "",
					ch -> Character.digit(ch, 16) >= 0 || ch == '#', s -> apply(layout, role, s));
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
