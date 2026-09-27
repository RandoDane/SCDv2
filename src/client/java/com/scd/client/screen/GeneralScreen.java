package com.scd.client.screen;

import com.scd.client.ScdMod;
import com.scd.client.config.ScdConfig;
import com.scd.client.ui.Rows;
import com.scd.client.ui.ScdScreen;
import com.scd.client.ui.Ui;
import com.scd.client.ui.widget.TextField;
import net.minecraft.client.gui.screens.Screen;

/** Backend URL, carry message templates, SkyBlock gating and developer mode. */
public final class GeneralScreen extends ScdScreen {
	private final ScdMod mod;
	private String url;
	private String urlError;

	public GeneralScreen(Screen parent, ScdMod mod) {
		super("General", parent);
		this.mod = mod;
		this.url = mod.config().backend.serverUrl;
	}

	@Override
	protected void onClosing() {
		applyUrl();
		mod.configManager.save();
	}

	private void applyUrl() {
		String err = mod.backend.setBaseUrl(url);
		urlError = err;
		if (err == null) mod.config().backend.serverUrl = mod.backend.baseUrl();
	}

	@Override
	protected void build(Rows rows) {
		ScdConfig c = mod.config();
		rows.header("SCD backend");
		rows.note("Your own SCD server: mayor perks, attribute-shard ids, accessory profiles and room reports. Prices never come from here.");
		rows.text("Server URL", "http://host:3000", url, TextField.any(), s -> url = s);
		rows.buttons(java.util.List.of("Apply"), java.util.List.of(() -> {
			applyUrl();
			mod.configManager.save();
			rebuild();
		}));
		rows.line(() -> urlError != null ? urlError : "Status: " + mod.backend.status().message(),
				() -> urlError != null ? Ui.DANGER : mod.backend.status().ok() ? Ui.SUCCESS : Ui.theme().textMuted());

		rows.header("Carry messages");
		rows.toggle("Post progress in party chat", null, () -> c.carries.partyProgress, v -> c.carries.partyProgress = v);
		rows.text("Progress message", null, c.carries.progressTemplate, TextField.any(), s -> c.carries.progressTemplate = s);
		rows.text("Finish message", null, c.carries.finishTemplate, TextField.any(), s -> c.carries.finishTemplate = s);
		rows.note("Placeholders: {player} {done} {owed} {left} {unit} {target} {price} {total}");

		rows.header("Other");
		rows.toggle("Only on SkyBlock", "Keep features idle on other servers/modes", () -> c.general.requireSkyblock, v -> c.general.requireSkyblock = v);
		rows.toggle("Developer mode", "Enables /scd debug and verbose logging", () -> c.general.developerMode, v -> {
			c.general.developerMode = v;
			com.scd.client.core.ScdLog.setDebug(v);
		});
	}
}
