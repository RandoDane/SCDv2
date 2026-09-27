package com.scd.client.screen;

import com.scd.client.ScdMod;
import com.scd.client.feature.accessory.AccessoryFeature;
import com.scd.client.feature.accessory.AccessoryScreen;
import com.scd.client.feature.carry.CarryScreen;
import com.scd.client.feature.carry.CarryService;
import com.scd.client.feature.dungeon.DungeonFeature;
import com.scd.client.feature.dungeon.DungeonScreen;
import com.scd.client.feature.slayer.SlayerFeature;
import com.scd.client.feature.slayer.SlayerScreen;
import com.scd.client.hud.HudEditorScreen;
import com.scd.client.ui.Rows;
import com.scd.client.ui.ScdScreen;
import com.scd.client.ui.Theme;
import com.scd.client.ui.Ui;
import com.scd.logic.Numbers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** /scd - the hub every other SCD screen is reached from. */
public final class MainScreen extends ScdScreen {
	private final ScdMod mod;

	public MainScreen(Screen parent, ScdMod mod) {
		super("SCD", parent, 320);
		this.mod = mod;
	}

	@Override
	protected String subtitle() {
		return "v" + mod.version + (mod.active() ? " · SkyBlock" : "");
	}

	@Override
	protected void onClosing() {
		mod.configManager.save();
	}

	private void go(Screen s) {
		Minecraft.getInstance().gui.setScreen(s);
	}

	@Override
	protected void build(Rows rows) {
		rows.cycle("Theme", Theme.PRESETS, Ui::theme, t -> {
			Ui.setTheme(t);
			mod.config().general.theme = t.name();
			mod.configManager.save();
		}, Theme::name);
		rows.line(() -> mod.market.hasKey()
						? "Market: " + mod.market.status().message()
						: "Market unavailable - prices off",
				() -> mod.market.hasKey() && mod.market.status().ok() ? Ui.SUCCESS : mod.market.hasKey() ? Ui.theme().textMuted() : Ui.WARNING);
		rows.space(2);

		CarryService carries = mod.feature(CarryService.class);
		rows.nav("Market & Bazaar", "scd.wtf key, price tooltips, price graph", () -> go(new MarketScreen(this, mod)));
		rows.nav("Slayer", "Boss tracker, mechanic cues, RNG meter, drops", () -> go(new SlayerScreen(this, mod, mod.feature(SlayerFeature.class))));
		rows.nav("Carries", carries.active().size() + " active · " + Numbers.coins(carries.outstandingTotal()) + " outstanding",
				() -> go(new CarryScreen(this, carries)));
		rows.nav("Dungeons", "Live score, S/S+ alerts, run summary, room mapping", () -> go(new DungeonScreen(this, mod, mod.feature(DungeonFeature.class))));
		rows.nav("Accessories", "Accessory Power, missing accessories by coins per MP", () -> go(new AccessoryScreen(this, mod, mod.feature(AccessoryFeature.class))));
		rows.nav("Edit HUD layout", "Move, resize and recolor every overlay at once", () -> go(new HudEditorScreen(this, mod.huds, mod.configManager)));
		rows.nav("General", "Backend, carry messages, developer tools", () -> go(new GeneralScreen(this, mod)));
	}
}
