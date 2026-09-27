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
				.toggle("Title + sound", () -> sl.spawnAlertTitle, v -> sl.spawnAlertTitle = v));
		slayerMods.add(new Module("Kill message", "Fight time (0.1s), NEW BEST, server lag", () -> sl.killMessage, v -> sl.killMessage = v));
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
			Module m = Module.group(type.bossName() + " cues", "Mechanic cues for " + type.bossName());
			for (AbilityCue cue : cues) m.toggle(cue.label() + " (" + cue.minTier() + "+)", () -> sl.cueEnabled(cue.id()), v -> sl.cues.put(cue.id(), v));
			slayerMods.add(m);
		}
		slayerMods.add(Module.group("Stats & drops", "Personal bests, RNG meter, drop history")
				.opt(new Opt.Action("Open slayer page...", () -> open(new SlayerScreen(current(), mod, slayer))))
				.opt(new Opt.Action("Reset session stats", () -> slayer.session().reset())));
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
		dun.add(new Module("Dungeon map", "Rooms, doors, checks, secrets per room, players", () -> d.mapHud, v -> d.mapHud = v));
		dun.add(new Module("Room HUD", "Name, secrets, crypts, clear-time PB of your room", () -> d.roomHud, v -> d.roomHud = v)
				.toggle("Route-making details", () -> d.roomDebug, v -> d.roomDebug = v));
		dun.add(new Module("Chest profit", "Value, cost and profit on reward chests", () -> d.chestProfit, v -> d.chestProfit = v));
		dun.add(new Module("Puzzle solvers", "Show-only: Quiz, Weirdos, Blaze, Beams, Ice, TP Maze, Tic Tac Toe", () -> d.puzzleSolvers, v -> d.puzzleSolvers = v));
		dun.add(new Module("Puzzle HUD", "Puzzle names and status", () -> d.puzzleHud, v -> d.puzzleHud = v));
		dun.add(new Module("Blood camp", "Watcher move timer, mob landing spots", () -> d.bloodCamp, v -> d.bloodCamp = v));
		dun.add(new Module("Door highlight", "Wither/blood doors: red locked, green openable", () -> d.doorHighlight, v -> d.doorHighlight = v)
				.toggle("Key spawn alert", () -> d.keyAlert, v -> d.keyAlert = v));
		dun.add(new Module("Deaths HUD", "Deaths per player", () -> d.deathHud, v -> d.deathHud = v)
				.toggle("Teammate death alert", () -> d.deathAlert, v -> d.deathAlert = v)
				.toggle("Low-health alert", () -> d.lowHealthAlert, v -> d.lowHealthAlert = v));
		dun.add(new Module("Blessings HUD", null, () -> d.blessingHud, v -> d.blessingHud = v));
		dun.add(new Module("Invincibility timers", "Bonzo's Mask, Spirit Mask, Phoenix", () -> d.invincibilityHud, v -> d.invincibilityHud = v));
		dun.add(new Module("Secret chime", "Sound + box when the secret counter goes up", () -> d.secretChime, v -> d.secretChime = v));
		dun.add(new Module("Room clear times", "Time per room vs PB and average", () -> d.roomTimeMessage, v -> d.roomTimeMessage = v));
		dun.add(new Module("Run summary", "One chat line per completed run", () -> d.completionSummary, v -> d.completionSummary = v));
		dun.add(Module.group("Room engine", "Which room you're in").opt(new Opt.Info("State", () -> mod.feature(DungeonFeature.class).rooms().describe())));
		out.add(new Category("Dungeons", dun));

		// ---- Routes ----
		RouteFeature routes = mod.feature(RouteFeature.class);
		Module show = new Module("Secret routes", "Play routes in identified rooms", () -> d.routes, v -> d.routes = v)
				.toggle("Through walls", () -> d.routesThroughWalls, v -> d.routesThroughWalls = v)
				.toggle("Preview next step", () -> d.routesShowNext, v -> d.routesShowNext = v)
				.opt(new Opt.Slider("Smoothing", 0, 4, 0.5, () -> d.routesSmoothing, v -> d.routesSmoothing = v,
						v -> v == 0 ? "raw" : String.format(Locale.ROOT, "%.1f", v)));
		List<Module> routeMods = new ArrayList<>(List.of(show));
		Module packs = Module.group("Route packs", "config/scd/routes").opt(new Opt.Info("Your routes", () -> String.valueOf(routes.library().mine().rooms.size())));
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
				.toggle("Whole-stack value", () -> c.bazaar.tooltipStackValue, v -> c.bazaar.tooltipStackValue = v));
		market.add(new Module("Auction tooltips", "Estimate and lowest BIN", () -> c.market.auctionTooltips, v -> c.market.auctionTooltips = v));
		market.add(new Module("Price graph", "Graph HUD while hovering in menus", () -> c.bazaar.graphHud, v -> c.bazaar.graphHud = v)
				.opt(new Opt.Cycle("Range", List.of("1d", "7d", "30d"), () -> c.bazaar.graphRange, v -> c.bazaar.graphRange = v, v -> v)));
		market.add(Module.group("Refresh", "Bazaar refresh interval (restart to apply)")
				.opt(new Opt.Slider("Every", 15, 300, 15, () -> c.market.bazaarRefreshSeconds, v -> c.market.bazaarRefreshSeconds = (int) Math.round(v),
						v -> Math.round(v) + "s")));
		market.add(Module.group("API key", "scd.wtf key (admins only)").opt(new Opt.Action("Open market page...", () -> open(new MarketScreen(current(), mod)))));
		out.add(new Category("Market", market));

		// ---- Accessories ----
		out.add(new Category("Accessories", List.of(
				new Module("Bag overlay", "Missing accessories priced by Magical Power", () -> c.accessories.bagOverlay, v -> c.accessories.bagOverlay = v)
						.opt(new Opt.Cycle("Sort", List.of("MAX", "PRICE", "VALUE"), () -> c.accessories.sort, v -> c.accessories.sort = v, v -> v)),
				Module.group("Accessory Power", "Exact AP and missing list").opt(new Opt.Action("Open accessories page...",
						() -> open(new AccessoryScreen(current(), mod, mod.feature(AccessoryFeature.class))))))));

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
						new Opt.Action("Overview...", () -> open(new MainScreen(current(), mod))))),
				new ClickGuiScreen.PanelSection("Carries", List.of(
						new Opt.Info("Active", () -> String.valueOf(carries.active().size())),
						new Opt.Toggle("Party chat progress", () -> c.carries.partyProgress, v -> c.carries.partyProgress = v),
						new Opt.Action("New carry...", () -> open(new CarryFormScreen(current(), carries))),
						new Opt.Action("Carries & ledger...", () -> open(new CarryScreen(current(), carries))))));
	}
}
