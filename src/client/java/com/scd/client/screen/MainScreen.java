package com.scd.client.screen;

import com.scd.client.ScdMod;
import com.scd.client.feature.carry.CarryService;
import com.scd.client.feature.slayer.SlayerFeature;
import com.scd.client.hud.HudEditorScreen;
import com.scd.client.hypixel.Players;
import com.scd.client.ui.Rows;
import com.scd.client.ui.ScdScreen;
import com.scd.client.ui.Ui;
import com.scd.logic.Numbers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** /scd - the Overview dashboard: headline numbers, recent activity and quick actions. */
public final class MainScreen extends ScdScreen {
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d", Locale.ROOT);

	private final ScdMod mod;

	public MainScreen(Screen parent, ScdMod mod) {
		super("Overview", parent);
		this.mod = mod;
	}

	@Override
	protected String navKey() {
		return "overview";
	}

	@Override
	protected String subtitle() {
		return "Welcome back, " + Players.selfName();
	}

	@Override
	protected void onClosing() {
		mod.configManager.save();
	}

	@Override
	protected void build(Rows rows) {
		SlayerFeature slayer = mod.feature(SlayerFeature.class);
		CarryService carries = mod.feature(CarryService.class);
		rows.stats(List.of(
				new Rows.Stat("Purse", () -> {
					String line = mod.game.sidebarFind(l -> l.startsWith("Purse:") || l.startsWith("Piggy:"));
					return line != null ? compactPurse(line) : "-";
				}, Ui.GOLD),
				new Rows.Stat("Kills today", () -> {
					double[] d = slayer.records().killsPerDay(1);
					return String.valueOf((int) d[0]);
				}, Ui.DANGER),
				new Rows.Stat("Owed", () -> Numbers.coins(carries.outstandingTotal()), Ui.SUCCESS),
				new Rows.Stat("Market", () -> !mod.market.hasKey() ? "Off" : mod.market.status().ok() ? "Online" : "Offline",
						mod.market.status().ok() ? Ui.SUCCESS : Ui.WARNING)));
		LocalDate today = LocalDate.now();
		rows.chart("Activity", "Slayer kills per day · last 14 days", () -> slayer.records().killsPerDay(14),
				today.minusDays(13).format(DAY), today.format(DAY));
		rows.header("Quick actions");
		rows.buttons(List.of("Edit HUD layout", "Slayer drops", "New carry"), List.of(
				() -> Minecraft.getInstance().gui.setScreen(new HudEditorScreen(this, mod.huds, mod.configManager)),
				() -> Minecraft.getInstance().gui.setScreen(new com.scd.client.feature.slayer.SlayerScreen(this, mod, slayer)),
				() -> Minecraft.getInstance().gui.setScreen(new com.scd.client.feature.carry.CarryFormScreen(this, carries))));
	}

	private static String compactPurse(String line) {
		String digits = line.replaceAll("^(Purse|Piggy):\\s*", "").replaceAll("\\s*\\(.*\\)$", "").replace(",", "").trim();
		try {
			return Numbers.coins(Double.parseDouble(digits));
		} catch (NumberFormatException e) {
			return digits;
		}
	}
}
