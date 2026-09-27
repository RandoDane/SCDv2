package com.scd.client.ui.clickgui;

import com.scd.client.ScdMod;
import com.scd.client.config.ScdConfig;
import com.scd.client.feature.accessory.AccessoryFeature;
import com.scd.client.feature.accessory.AccessoryScreen;
import com.scd.client.feature.carry.CarryFormScreen;
import com.scd.client.feature.carry.CarryScreen;
import com.scd.client.feature.carry.CarryService;
import com.scd.client.feature.dungeon.DungeonFeature;
import com.scd.client.feature.dungeon.route.RouteFeature;
import com.scd.client.feature.dungeon.route.RouteLibrary;
import com.scd.client.feature.perf.LagScanner;
import com.scd.client.feature.slayer.AbilityCue;
import com.scd.client.feature.slayer.SlayerFeature;
import com.scd.client.feature.slayer.SlayerScreen;
import com.scd.client.feature.slayer.SlayerType;
import com.scd.client.hud.HudEditorScreen;
import com.scd.client.screen.AppearanceScreen;
import com.scd.client.screen.GeneralScreen;
import com.scd.client.screen.MainScreen;
import com.scd.client.screen.MarketScreen;
import com.scd.client.ui.Theme;
import com.scd.client.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** What the click GUI shows: every SCD setting, grouped into columns, plus the side panel. */
final class ClickGuiContent {
	private ClickGuiContent() {
	}

	private static void open(Screen s) {
		Minecraft.getInstance().gui.setScreen(s);
	}

	private static Screen current() {
		return Minecraft.getInstance().gui.screen();
	}

	static List<Category> categories(ScdMod mod) {
		ScdConfig c = mod.config();
		List<Category> out = new ArrayList<>();

		// ---- Slayer ----
		var sl = c.slayer;
		SlayerFeature slayer = mod.feature(SlayerFeature.class);
		List<Module> slayerMods = new ArrayList<>();
		slayerMods.add(new Module("Boss HUD", "Fight/hunt timers, HP bar, mechanic cues, RNG meter", () -> sl.hud, v -> sl.hud = v)
				.toggle("Session stats", () -> sl.sessionStats, v -> sl.sessionStats = v)
				.opt(new Opt.Info("Hunt pause", () -> Math.round(slayer.tracker().huntWindowMs() / 1000.0) + "s")));
		slayerMods.add(new Module("Spawn alert", "Chat line when your boss spawns", () -> sl.spawnAlert, v -> sl.spawnAlert = v)
				.toggle("Title + sound", () -> sl.spawnAlertTitle, v -> sl.spawnAlertTitle = v)
				.toggle("Show spawn time", () -> sl.spawnShowTime, v -> sl.spawnShowTime = v));
		slayerMods.add(new Module("Kill message", "Fight time to 0.1s", () -> sl.killMessage, v -> sl.killMessage = v)
				.toggle("NEW BEST tag", () -> sl.killShowBest, v -> sl.killShowBest = v)
				.toggle("Server lag note", () -> sl.killShowLag, v -> sl.killShowLag = v));
		slayerMods.add(new Module("Miniboss alert", null, () -> sl.minibossAlert, v -> sl.minibossAlert = v)
				.toggle("Title + sound", () -> sl.minibossTitle, v -> sl.minibossTitle = v));
		slayerMods.add(new Module("Boss glow", "Outline your boss through walls", () -> sl.highlightGlow, v -> sl.highlightGlow = v));
		slayerMods.add(new Module("Boss hitbox", null, () -> sl.highlightBox, v -> sl.highlightBox = v));
		slayerMods.add(new Module("Boss tracer", null, () -> sl.highlightLine, v -> sl.highlightLine = v));
		slayerMods.add(new Module("Drop tracking", "Count drops during a quest (inventory + sacks)", () -> sl.dropTracking, v -> sl.dropTracking = v));
		slayerMods.add(new Module("Explosive arrows", "Arrows left while holding a bow (Enderman)", () -> sl.explosiveArrowCounter, v -> sl.explosiveArrowCounter = v));
		for (SlayerType type : SlayerType.values()) {
			List<AbilityCue> cues = AbilityCue.forType(type);
			if (cues.isEmpty()) continue;
			String group = type.name();
			Module m = new Module(type.bossName() + " cues", "Mechanic cues for " + type.bossName(),
					() -> sl.cueGroupEnabled(group), v -> sl.cueGroups.put(group, v));
			for (AbilityCue cue : cues) m.toggle(cue.label() + " (" + cue.minTier() + "+)", () -> sl.cueEnabled(cue.id()), v -> sl.cues.put(cue.id(), v));
			slayerMods.add(m);
		}
		out.add(new Category("Slayer", slayerMods));

		// ---- Dungeons ----
		var d = c.dungeon;
		List<Module> dun = new ArrayList<>();
		dun.add(new Module("Score HUD", "Live score estimate", () -> d.scoreHud, v -> d.scoreHud = v)
				.toggle("Skill / Explore / Speed / Bonus", () -> d.scoreBreakdown, v -> d.scoreBreakdown = v)
				.toggle("Rooms and secrets", () -> d.scoreRoomsSecrets, v -> d.scoreRoomsSecrets = v)
				.toggle("Crypts, deaths, puzzles", () -> d.scoreCryptsDeathsPuzzles, v -> d.scoreCryptsDeathsPuzzles = v)
				.toggle("Splits", () -> d.scoreSplits, v -> d.scoreSplits = v)
				.toggle("Spirit pet in party", () -> d.assumeSpiritPet, v -> d.assumeSpiritPet = v)
				.toggle("S / S+ alerts", () -> d.scoreMilestoneAlerts, v -> d.scoreMilestoneAlerts = v));
		dun.add(new Module("Dungeon map", "Rooms, doors, checks, secrets per room, players", () -> d.mapHud, v -> d.mapHud = v)
				.toggle("Secrets per room", () -> d.mapSecrets, v -> d.mapSecrets = v)
				.toggle("Check marks", () -> d.mapChecks, v -> d.mapChecks = v)
				.toggle("Doors", () -> d.mapDoors, v -> d.mapDoors = v)
				.toggle("Player dots", () -> d.mapPlayers, v -> d.mapPlayers = v));
		dun.add(new Module("Room HUD", "Name, secrets, crypts, clear-time PB of your room", () -> d.roomHud, v -> d.roomHud = v)
				.toggle("Route-making details", () -> d.roomDebug, v -> d.roomDebug = v));
		dun.add(new Module("Chest profit", "Value, cost and profit on reward chests", () -> d.chestProfit, v -> d.chestProfit = v)
				.toggle("Tooltip lines", () -> d.chestProfitTooltip, v -> d.chestProfitTooltip = v)
				.toggle("Outline best chest", () -> d.chestProfitHighlight, v -> d.chestProfitHighlight = v)
				.toggle("\"Best\" label", () -> d.chestProfitLabel, v -> d.chestProfitLabel = v));
		dun.add(new Module("Puzzle solvers", "Show-only: Quiz, Weirdos, Blaze, Beams, Ice, TP Maze, Tic Tac Toe", () -> d.puzzleSolvers, v -> d.puzzleSolvers = v));
		dun.add(new Module("Puzzle HUD", "Puzzle names and status", () -> d.puzzleHud, v -> d.puzzleHud = v));
		dun.add(new Module("Blood camp", "Watcher move timer, mob landing spots", () -> d.bloodCamp, v -> d.bloodCamp = v));
		dun.add(new Module("Door highlight", "Wither/blood doors: red locked, green openable", () -> d.doorHighlight, v -> d.doorHighlight = v)
				.toggle("Key spawn alert", () -> d.keyAlert, v -> d.keyAlert = v));
		dun.add(new Module("Deaths HUD", "Deaths per player", () -> d.deathHud, v -> d.deathHud = v)
				.toggle("Teammate death alert", () -> d.deathAlert, v -> d.deathAlert = v)
				.toggle("Low-health alert", () -> d.lowHealthAlert, v -> d.lowHealthAlert = v));
		dun.add(new Module("Blessings HUD", null, () -> d.blessingHud, v -> d.blessingHud = v)
				.toggle("Power", () -> d.blessPower, v -> d.blessPower = v)
				.toggle("Time", () -> d.blessTime, v -> d.blessTime = v)
				.toggle("Stone", () -> d.blessStone, v -> d.blessStone = v)
				.toggle("Life", () -> d.blessLife, v -> d.blessLife = v)
				.toggle("Wisdom", () -> d.blessWisdom, v -> d.blessWisdom = v));
		dun.add(new Module("Invincibility timers", "Invincible time and cooldown", () -> d.invincibilityHud, v -> d.invincibilityHud = v)
				.toggle("Bonzo's Mask", () -> d.invBonzo, v -> d.invBonzo = v)
				.toggle("Spirit Mask", () -> d.invSpirit, v -> d.invSpirit = v)
				.toggle("Phoenix", () -> d.invPhoenix, v -> d.invPhoenix = v));
		dun.add(new Module("Secret chime", "When the room's secret counter goes up", () -> d.secretChime, v -> d.secretChime = v)
				.toggle("Sound", () -> d.secretSound, v -> d.secretSound = v)
				.toggle("Box the clicked block", () -> d.secretBox, v -> d.secretBox = v));
		dun.add(new Module("Room clear times", "Time from entering a room to its check", () -> d.roomTimeMessage, v -> d.roomTimeMessage = v)
				.toggle("Personal best", () -> d.roomTimePb, v -> d.roomTimePb = v)
				.toggle("Average", () -> d.roomTimeAvg, v -> d.roomTimeAvg = v)
				.toggle("Server lag", () -> d.roomTimeLag, v -> d.roomTimeLag = v)
				.toggle("Only new PBs", () -> d.roomTimeOnlyPb, v -> d.roomTimeOnlyPb = v));
		var sum = d.summary;
		dun.add(new Module("Run summary", "One chat line per completed run", () -> d.completionSummary, v -> d.completionSummary = v)
				.toggle("Floor + boss", () -> sum.floor, v -> sum.floor = v)
				.toggle("Clear time", () -> sum.time, v -> sum.time = v)
				.toggle("Score", () -> sum.score, v -> sum.score = v)
				.toggle("SCD estimate (if different)", () -> sum.estimate, v -> sum.estimate = v)
				.toggle("Secrets", () -> sum.secrets, v -> sum.secrets = v)
				.toggle("Deaths", () -> sum.deaths, v -> sum.deaths = v)
				.toggle("Damage", () -> sum.damage, v -> sum.damage = v)
				.toggle("Catacombs XP", () -> sum.xp, v -> sum.xp = v)
				.toggle("Server lag", () -> sum.lag, v -> sum.lag = v));
		out.add(new Category("Dungeons", dun));

		// ---- Routes ----
		RouteFeature routes = mod.feature(RouteFeature.class);
		Module show = new Module("Secret routes", "Play routes in identified rooms", () -> d.routes, v -> d.routes = v)
				.toggle("Through walls", () -> d.routesThroughWalls, v -> d.routesThroughWalls = v)
				.toggle("Preview next step", () -> d.routesShowNext, v -> d.routesShowNext = v)
				.opt(new Opt.Slider("Smoothing", 0, 4, 0.5, () -> d.routesSmoothing, v -> d.routesSmoothing = v,
						v -> v == 0 ? "raw" : String.format(Locale.ROOT, "%.1f", v)));
		List<Module> routeMods = new ArrayList<>(List.of(show));
		Module packs = new Module("Route packs", "Use pack files from config/scd/routes (your own routes always play)",
				() -> d.routePacks, v -> d.routePacks = v)
				.opt(new Opt.Info("Your routes", () -> String.valueOf(routes.library().mine().rooms.size())));
		for (var pack : routes.library().packs()) {
			String file = pack.file();
			packs.toggle(pack.pack().name.equals("Routes") ? file : pack.pack().name, () -> !d.disabledRoutePacks.contains(file), v -> {
				if (v) d.disabledRoutePacks.remove(file);
				else if (!d.disabledRoutePacks.contains(file)) d.disabledRoutePacks.add(file);
			});
		}
		packs.opt(new Opt.Action("Open folder", () -> net.minecraft.util.Util.getPlatform().openPath(RouteLibrary.folder())));
		packs.opt(new Opt.Action("Reload packs", () -> routes.library().reload()));
		routeMods.add(packs);
		out.add(new Category("Routes", routeMods));

		// ---- Market ----
		List<Module> market = new ArrayList<>();
		market.add(new Module("Bazaar tooltips", "Instant buy/sell and spread", () -> c.bazaar.tooltip, v -> c.bazaar.tooltip = v)
				.toggle("Whole-stack value", () -> c.bazaar.tooltipStackValue, v -> c.bazaar.tooltipStackValue = v)
				.opt(new Opt.Slider("Refresh (restart)", 15, 300, 15, () -> c.market.bazaarRefreshSeconds, v -> c.market.bazaarRefreshSeconds = (int) Math.round(v),
						v -> Math.round(v) + "s")));
		market.add(new Module("Auction tooltips", "Estimate and lowest BIN", () -> c.market.auctionTooltips, v -> c.market.auctionTooltips = v));
		market.add(new Module("Price graph", "Graph HUD while hovering in menus", () -> c.bazaar.graphHud, v -> c.bazaar.graphHud = v)
				.opt(new Opt.Cycle("Range", List.of("1d", "7d", "30d"), () -> c.bazaar.graphRange, v -> c.bazaar.graphRange = v, v -> v)));
		out.add(new Category("Market", market));

		// ---- Accessories ----
		out.add(new Category("Accessories", List.of(
				new Module("Bag overlay", "Missing accessories priced by Magical Power", () -> c.accessories.bagOverlay, v -> c.accessories.bagOverlay = v)
						.opt(new Opt.Cycle("Sort", List.of("MAX", "PRICE", "VALUE"), () -> c.accessories.sort, v -> c.accessories.sort = v, v -> v)))));

		// ---- Performance ----
		out.add(new Category("Performance", List.of(
				new Module("Lag scanner", "What costs the most frames; spikes logged with location", () -> c.perf.lagScanner, v -> {
					c.perf.lagScanner = v;
					mod.feature(LagScanner.class).setEnabled(v);
				}).opt(new Opt.Slider("Spike threshold", 20, 200, 10, () -> c.perf.spikeMs, v -> c.perf.spikeMs = (int) Math.round(v), v -> Math.round(v) + "ms")))));
		return out;
	}

	static List<ClickGuiScreen.PanelSection> panel(ScdMod mod) {
		ScdConfig c = mod.config();
		List<String> themes = Theme.PRESETS.stream().map(Theme::name).toList();
		int[] sizes = Ui.textSizes();
		CarryService carries = mod.feature(CarryService.class);
		return List.of(
				new ClickGuiScreen.PanelSection("General", List.of(
						new Opt.Cycle("Theme", themes, () -> Ui.theme().name(), v -> {
							Ui.setTheme(Theme.byName(v));
							c.general.theme = v;
						}, v -> v),
						new Opt.Toggle("Smooth font", () -> c.general.smoothFont, v -> {
							c.general.smoothFont = v;
							Ui.setSmoothFont(v);
						}),
						new Opt.Slider("Text size", sizes[0], sizes[sizes.length - 1], 10, () -> c.general.menuTextSize, v -> {
							c.general.menuTextSize = (int) Math.round(v);
							Ui.setMenuTextSize(c.general.menuTextSize);
						}, v -> Math.round(v) + "%"),
						new Opt.Slider("Menu size", 80, 130, 5, () -> c.general.menuScale, v -> c.general.menuScale = (int) Math.round(v),
								v -> Math.round(v) + "%"),
						new Opt.Toggle("Only on SkyBlock", () -> c.general.requireSkyblock, v -> c.general.requireSkyblock = v),
						new Opt.Toggle("Developer mode", () -> c.general.developerMode, v -> {
							c.general.developerMode = v;
							com.scd.client.core.ScdLog.setDebug(v);
						}),
						new Opt.Action("HUD layout...", () -> open(new HudEditorScreen(current(), mod.huds, mod.configManager))),
						new Opt.Action("HUD appearance...", () -> open(new AppearanceScreen(current(), mod))),
						new Opt.Action("Backend & messages...", () -> open(new GeneralScreen(current(), mod))),
						new Opt.Action("Market API key...", () -> open(new MarketScreen(current(), mod))),
						new Opt.Action("Overview...", () -> open(new MainScreen(current(), mod))))),
				new ClickGuiScreen.PanelSection("Pages", List.of(
						new Opt.Action("Slayer stats & drops...", () -> open(new SlayerScreen(current(), mod, mod.feature(SlayerFeature.class)))),
						new Opt.Action("Reset slayer session", () -> mod.feature(SlayerFeature.class).session().reset()),
						new Opt.Action("Accessories...", () -> open(new AccessoryScreen(current(), mod, mod.feature(AccessoryFeature.class)))),
						new Opt.Info("Room engine", () -> mod.feature(DungeonFeature.class).rooms().describe()))),
				new ClickGuiScreen.PanelSection("Carries", List.of(
						new Opt.Info("Active", () -> String.valueOf(carries.active().size())),
						new Opt.Toggle("Party chat progress", () -> c.carries.partyProgress, v -> c.carries.partyProgress = v),
						new Opt.Action("New carry...", () -> open(new CarryFormScreen(current(), carries))),
						new Opt.Action("Carries & ledger...", () -> open(new CarryScreen(current(), carries))))));
	}
}
