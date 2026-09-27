package com.scd.client.feature.dungeon;

import com.scd.client.ScdMod;
import com.scd.client.config.ScdConfig;
import com.scd.client.feature.carry.CarryScreen;
import com.scd.client.feature.carry.CarryService;
import com.scd.client.ui.Rows;
import com.scd.client.ui.ScdScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;

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
		var routes = mod.feature(com.scd.client.feature.dungeon.route.RouteFeature.class);
		rows.group("score", "Score HUD", null, true, g -> {
			g.toggle("Live score", "Estimate from tab list, sidebar and chat events", () -> c.scoreHud, v -> c.scoreHud = v);
			g.toggle("Skill / Explore / Speed / Bonus", null, () -> c.scoreBreakdown, v -> c.scoreBreakdown = v);
			g.toggle("Rooms and secrets", null, () -> c.scoreRoomsSecrets, v -> c.scoreRoomsSecrets = v);
			g.toggle("Crypts, deaths, puzzles", null, () -> c.scoreCryptsDeathsPuzzles, v -> c.scoreCryptsDeathsPuzzles = v);
			g.toggle("Splits", "Blood open, Watcher, boss and clear times with PB deltas", () -> c.scoreSplits, v -> c.scoreSplits = v);
			g.toggle("Spirit pet in party", "First death costs 1 point instead of 2", () -> c.assumeSpiritPet, v -> c.assumeSpiritPet = v);
			g.toggle("S / S+ alerts", "Title + sound the first time the estimate reaches 270 and 300", () -> c.scoreMilestoneAlerts, v -> c.scoreMilestoneAlerts = v);
		});
		rows.group("runs", "Runs", null, true, g -> {
			g.toggle("Chest profit", "Value, cost and profit on reward chests (Croesus and run end); best chest outlined", () -> c.chestProfit, v -> c.chestProfit = v);
			g.toggle("Completion summary", "One chat line with floor, time, score, secrets, damage", () -> c.completionSummary, v -> c.completionSummary = v);
			CarryService carries = mod.feature(CarryService.class);
			long active = carries.active().stream().filter(x -> x.kind == com.scd.client.feature.carry.Carry.Kind.DUNGEON).count();
			g.button(active > 0 ? "Dungeon carries (" + active + " active)..." : "Carries...",
					() -> Minecraft.getInstance().gui.setScreen(new CarryScreen(this, carries)));
		});
		rows.group("helpers", "Helpers", null, true, g -> {
			g.toggle("Dungeon map", "Rooms, doors, checks, secrets found per room (teammates' too) and player dots", () -> c.mapHud, v -> c.mapHud = v);
			g.toggle("Puzzle solvers", "Quiz, Three Weirdos, Higher/Lower Blaze, Creeper Beams, Ice Fill, Ice Path, Teleport Maze, Tic Tac Toe (shown, never clicked)", () -> c.puzzleSolvers, v -> c.puzzleSolvers = v);
			g.toggle("Blood camp helper", "When the Watcher moves, and where / when each blood mob will spawn", () -> c.bloodCamp, v -> c.bloodCamp = v);
			g.toggle("Puzzle HUD", "Each puzzle's name and status (✔ done, ✖ failed, ✦ open)", () -> c.puzzleHud, v -> c.puzzleHud = v);
			g.toggle("Deaths HUD", "Deaths per player this run", () -> c.deathHud, v -> c.deathHud = v);
			g.toggle("Teammate death alert", null, () -> c.deathAlert, v -> c.deathAlert = v);
			g.toggle("Teammate low-health alert", "When a teammate's health on the sidebar turns red", () -> c.lowHealthAlert, v -> c.lowHealthAlert = v);
			g.toggle("Blessings HUD", "Power, Time, Stone, Life and Wisdom levels", () -> c.blessingHud, v -> c.blessingHud = v);
			g.toggle("Invincibility timers", "Bonzo's Mask, Spirit Mask and Phoenix: invincible time and cooldown", () -> c.invincibilityHud, v -> c.invincibilityHud = v);
			g.toggle("Door highlight", "Wither and blood doors: red while locked, green once the party has the key", () -> c.doorHighlight, v -> c.doorHighlight = v);
			g.toggle("Key spawn alert", null, () -> c.keyAlert, v -> c.keyAlert = v);
			g.toggle("Room clear times", "Chat line with the time from entering a room to its check, vs your PB and average", () -> c.roomTimeMessage, v -> c.roomTimeMessage = v);
			g.toggle("Secret chime", "Sound and a brief box on the clicked block when a secret counts", () -> c.secretChime, v -> c.secretChime = v);
		});
		rows.group("rooms", "Rooms", null, false, g -> {
			g.toggle("Room HUD", "Name, secrets and crypts of the room you're in", () -> c.roomHud, v -> c.roomHud = v);
			g.toggle("Route-making details", "Adds rotation, anchor and your room-relative position to the room HUD", () -> c.roomDebug, v -> c.roomDebug = v);
			g.value("Room engine", dungeon::roomEngineState);
		});
		rows.group("routes", "Secret routes", null, false, g -> {
			g.toggle("Show routes", "Path, etherwarps, mines and the next secret in identified rooms", () -> c.routes, v -> c.routes = v);
			g.toggle("Through walls", null, () -> c.routesThroughWalls, v -> c.routesThroughWalls = v);
			g.toggle("Preview the next step", "Draws the following step faded", () -> c.routesShowNext, v -> c.routesShowNext = v);
			g.slider("Path smoothing", 0, 4, 0.5, () -> c.routesSmoothing, v -> c.routesSmoothing = v,
					v -> v == 0 ? "off (raw)" : String.format(java.util.Locale.ROOT, "%.1f blocks", v));
			g.value("Your routes", () -> routes.library().mine().rooms.size() + " rooms in " + com.scd.client.feature.dungeon.route.RouteLibrary.MINE);
			for (var pack : routes.library().packs()) {
				String file = pack.file();
				g.toggle(pack.pack().name.equals("Routes") ? file : pack.pack().name, pack.pack().rooms.size() + " rooms · " + file,
						() -> !c.disabledRoutePacks.contains(file), v -> {
							if (v) c.disabledRoutePacks.remove(file);
							else if (!c.disabledRoutePacks.contains(file)) c.disabledRoutePacks.add(file);
						});
			}
			g.note("Record: stand in a room, /scd route record, clear it, /scd route record stop. Share: /scd route share. "
					+ "Packs (SCD or SecretRoutes routes.json) go in config/scd/routes.");
			g.buttons(List.of("Open routes folder", "Reload packs"), List.of(
					() -> net.minecraft.util.Util.getPlatform().openPath(com.scd.client.feature.dungeon.route.RouteLibrary.folder()),
					() -> {
						routes.library().reload();
						rebuild();
					}));
		});
	}
}
