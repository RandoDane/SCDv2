package com.scd.client.feature.dungeon;

import com.scd.client.ScdMod;
import com.scd.client.config.ScdConfig;
import com.scd.client.feature.carry.CarryScreen;
import com.scd.client.feature.carry.CarryService;
import com.scd.client.ui.Rows;
import com.scd.client.ui.ScdScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Dungeon settings. */
public final class DungeonScreen extends ScdScreen {
	private final ScdMod mod;
	private final DungeonFeature dungeon;

	public DungeonScreen(Screen parent, ScdMod mod, DungeonFeature dungeon) {
		super("Dungeons", parent);
		this.mod = mod;
		this.dungeon = dungeon;
	}

	@Override
	protected String subtitle() {
		var s = dungeon.score();
		return s != null ? s.state().floor() + " · score " + s.total() : null;
	}

	@Override
	protected void onClosing() {
		mod.configManager.save();
	}

	@Override
	protected String navKey() {
		return "dungeons";
	}

	@Override
	protected void build(Rows rows) {
		ScdConfig.Dungeon c = mod.config().dungeon;
		rows.header("Score HUD");
		rows.toggle("Live score", "Estimate from tab list, sidebar and chat events", () -> c.scoreHud, v -> c.scoreHud = v);
		rows.toggle("Skill / Explore / Speed / Bonus", null, () -> c.scoreBreakdown, v -> c.scoreBreakdown = v);
		rows.toggle("Rooms and secrets", null, () -> c.scoreRoomsSecrets, v -> c.scoreRoomsSecrets = v);
		rows.toggle("Crypts, deaths, puzzles", null, () -> c.scoreCryptsDeathsPuzzles, v -> c.scoreCryptsDeathsPuzzles = v);
		rows.toggle("S / S+ alerts", "Title + sound the first time the estimate reaches 270 and 300", () -> c.scoreMilestoneAlerts, v -> c.scoreMilestoneAlerts = v);

		rows.header("Runs");
		rows.toggle("Completion summary", "One chat line with floor, time, score, secrets, damage", () -> c.completionSummary, v -> c.completionSummary = v);
		CarryService carries = mod.feature(CarryService.class);
		long active = carries.active().stream().filter(x -> x.kind == com.scd.client.feature.carry.Carry.Kind.DUNGEON).count();
		rows.button(active > 0 ? "Dungeon carries (" + active + " active)..." : "Carries...",
				() -> Minecraft.getInstance().gui.setScreen(new CarryScreen(this, carries)));

		rows.header("Rooms");
		rows.toggle("Room HUD", "Name, secrets and crypts of the room you're in", () -> c.roomHud, v -> c.roomHud = v);
		rows.toggle("Route-making details", "Adds rotation, anchor and your room-relative position to the room HUD", () -> c.roomDebug, v -> c.roomDebug = v);
		rows.value("Room engine", dungeon::roomEngineState);
	}
}
