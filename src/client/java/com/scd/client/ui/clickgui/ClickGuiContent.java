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
import java.util.Map;
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
				.toggle("Player heads", () -> d.mapPlayers, v -> d.mapPlayers = v)
				.opt(new Opt.Slider("Head size", 50, 200, 10, () -> d.mapHeadSize, v -> d.mapHeadSize = (int) Math.round(v), v -> Math.round(v) + "%")));
		dun.add(new Module("Room HUD", "Name, secrets, crypts, clear-time PB of your room", () -> d.roomHud, v -> d.roomHud = v)
				.toggle("Route-making details", () -> d.roomDebug, v -> d.roomDebug = v));
		dun.add(new Module("Chest profit", "Value, cost and profit on reward chests", () -> d.chestProfit, v -> d.chestProfit = v)
				.toggle("Tooltip lines", () -> d.chestProfitTooltip, v -> d.chestProfitTooltip = v)
				.toggle("Outline best chest", () -> d.chestProfitHighlight, v -> d.chestProfitHighlight = v)
				.toggle("\"Best\" label", () -> d.chestProfitLabel, v -> d.chestProfitLabel = v)
				.toggle("Profit panel", () -> d.chestProfitPanel, v -> d.chestProfitPanel = v)
				.opt(new Opt.Chips("Bazaar", List.of("Insta-sell", "Sell offer"), () -> d.chestBazaarPrice, v -> d.chestBazaarPrice = v))
				.opt(new Opt.Chips("Auction", List.of("Lowest BIN", "Estimate"), () -> d.chestAuctionPrice, v -> d.chestAuctionPrice = v)));
		Module solvers = new Module("Puzzle solvers", "Show-only helpers; expand to switch single solvers off", () -> d.puzzleSolvers, v -> d.puzzleSolvers = v);
		for (String puzzle : List.of("Quiz", "Three Weirdos", "Higher/Lower Blaze", "Creeper Beams", "Ice Fill", "Ice Path", "Teleport Maze", "Tic Tac Toe", "Boulder")) {
			solvers.opt(new Opt.Toggle(puzzle, () -> !d.disabledPuzzles.contains(puzzle), v -> {
				d.disabledPuzzles.remove(puzzle);
				if (!v) d.disabledPuzzles.add(puzzle);
			}));
		}
		dun.add(solvers);
		dun.add(new Module("Puzzle HUD", "Puzzle names and status", () -> d.puzzleHud, v -> d.puzzleHud = v));
		dun.add(new Module("Starred mobs", "Box every starred (✯) mob and count the ones left in your room", () -> d.starredMobs, v -> d.starredMobs = v)
				.toggle("Through walls", () -> d.starredThroughWalls, v -> d.starredThroughWalls = v)
				.toggle("Count HUD", () -> d.starredHud, v -> d.starredHud = v));
		dun.add(new Module("Draft reminder", "When a puzzle fails: title, your Architect's First Drafts, or a button to get one from sacks",
				() -> d.draftReminder, v -> d.draftReminder = v));
		dun.add(new Module("Livid finder", "F5/M5: box the real Livid and show its name and invulnerability", () -> d.lividFinder, v -> d.lividFinder = v));
		dun.add(new Module("Boss timers", "Sadan terracotta respawns (F6) and the Spirit Bear kills/spawn (F4)", () -> d.bossTimers, v -> d.bossTimers = v));
		dun.add(new Module("Capture rooms", "Save a copy of each room you play, to rebuild in singleplayer (/scd rooms)", () -> d.captureRooms, v -> d.captureRooms = v));
		dun.add(new Module("Blood camp", "Watcher move timer, mob landing spots", () -> d.bloodCamp, v -> d.bloodCamp = v));
		dun.add(new Module("Door highlight", "Wither/blood doors: red locked, green openable", () -> d.doorHighlight, v -> d.doorHighlight = v)
				.toggle("Key spawn alert", () -> d.keyAlert, v -> d.keyAlert = v));
		dun.add(new Module("Deaths HUD", "Deaths per player", () -> d.deathHud, v -> d.deathHud = v)
				.toggle("Teammate death alert", () -> d.deathAlert, v -> d.deathAlert = v)
				.toggle("Low-health alert", () -> d.lowHealthAlert, v -> d.lowHealthAlert = v)
				.toggle("Healer/Tank ultimate calls", () -> d.classAlerts, v -> d.classAlerts = v));
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
		// Waypoints work alone; routes need them (on turns waypoints on, waypoints off turns routes off).
		Module waypoints = new Module("Secret waypoints", "Every known secret in your room: your labels plus the secrets in its routes",
				() -> d.labeledSecrets, v -> {
			d.labeledSecrets = v;
			if (!v) d.routes = false;
		});
		Module show = new Module("Secret routes", "Play routes in identified rooms (needs Secret waypoints)", () -> d.routes, v -> {
			d.routes = v;
			if (v) d.labeledSecrets = true;
		})
				.toggle("Through walls", () -> d.routesThroughWalls, v -> d.routesThroughWalls = v)
				.toggle("Preview next step", () -> d.routesShowNext, v -> d.routesShowNext = v)
				.opt(new Opt.Slider("Smoothing", 0, 4, 0.5, () -> d.routesSmoothing, v -> d.routesSmoothing = v,
						v -> v == 0 ? "raw" : String.format(Locale.ROOT, "%.1f", v)));
		List<Module> routeMods = new ArrayList<>(List.of(waypoints, show));
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
				.toggle("Shift: stack price", () -> c.bazaar.tooltipStackValue, v -> c.bazaar.tooltipStackValue = v)
				);
		market.add(new Module("Auction tooltips", "Estimate and lowest BIN", () -> c.market.auctionTooltips, v -> c.market.auctionTooltips = v));
		market.add(new Module("Price graph", "Graph HUD while hovering in menus", () -> c.bazaar.graphHud, v -> c.bazaar.graphHud = v)
				.opt(new Opt.Cycle("Range", List.of("1d", "7d", "30d"), () -> c.bazaar.graphRange, v -> c.bazaar.graphRange = v, v -> v)));
		out.add(new Category("Market", market));

		// ---- Accessories ----
		out.add(new Category("Accessories", List.of(
				new Module("Bag overlay", "Missing accessories priced by Magical Power", () -> c.accessories.bagOverlay, v -> c.accessories.bagOverlay = v)
						.opt(new Opt.Cycle("Sort", List.of("MAX", "PRICE", "VALUE"), () -> c.accessories.sort, v -> c.accessories.sort = v, v -> v)))));

		// ---- HUD (appearance of every HUD) ----
		List<Module> huds = new ArrayList<>();
		for (var e : mod.huds.elements()) {
			var layout = mod.huds.layout(e);
			Module m = new Module(e.name(), "Show this HUD (its feature must be on too); options: size, panel, colours",
					() -> !layout.hidden, v -> layout.hidden = !v)
					.opt(new Opt.Slider("Size", 0.5, 2.5, 0.1, () -> layout.scale, v -> layout.scale = (float) (double) v, v -> Math.round(v * 100) + "%"))
					.toggle("Background panel", () -> layout.background, v -> layout.background = v);
			for (var role : com.scd.client.hud.HudColor.values()) {
				m.opt(new Opt.Color(role.label(), () -> layout.colors.get(role.id()), v -> {
					if (v == null) layout.colors.remove(role.id());
					else layout.colors.put(role.id(), role == com.scd.client.hud.HudColor.BACKGROUND ? (v & 0x00FFFFFF) | 0xE0000000 : v);
				}));
			}
			huds.add(m);
		}
		out.add(new Category("HUD", huds));

		// ---- Performance ----
		out.add(new Category("Performance", List.of(
				new Module("Lag scanner", "What costs the most frames; spikes logged with location", () -> c.perf.lagScanner, v -> {
					c.perf.lagScanner = v;
					mod.feature(LagScanner.class).setEnabled(v);
				}).opt(new Opt.Slider("Spike threshold", 20, 200, 10, () -> c.perf.spikeMs, v -> c.perf.spikeMs = (int) Math.round(v), v -> Math.round(v) + "ms")))));
		return out;
	}

	/** New-carry form state (kept while the menu is open). */
	private static String newCustomer = "", newPrice = "";
	private static String newWhat = "F7";
	private static int newCount = 1;

	static List<ClickGuiScreen.PanelSection> panel(ScdMod mod) {
		ScdConfig c = mod.config();
		List<String> themes = Theme.PRESETS.stream().map(Theme::name).toList();
		int[] sizes = Ui.textSizes();
		SlayerFeature slayer = mod.feature(SlayerFeature.class);
		return List.of(
				new ClickGuiScreen.PanelSection("General", () -> List.of(
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
						new Opt.Toggle("Only on SkyBlock", () -> c.general.requireSkyblock, v -> c.general.requireSkyblock = v))),
				new ClickGuiScreen.PanelSection("Stats", () -> {
					List<Opt> o = new ArrayList<>();
					o.add(new Opt.Info("Purse", () -> {
						String line = mod.game.sidebarFind(l -> l.startsWith("Purse:") || l.startsWith("Piggy:"));
						return line != null ? line.replaceFirst("^(Purse|Piggy):\\s*", "") : "-";
					}));
					var session = slayer.session();
					if (session.kills() > 0) {
						o.add(new Opt.Info("Slayer session", () -> session.kills() + " kills · " + Math.round(session.killsPerHour()) + "/h"));
						o.add(new Opt.Info("Avg fight / spawn", () -> com.scd.logic.Numbers.durationTenths(session.avgFightMs()) + " / "
								+ com.scd.logic.Numbers.durationTenths(session.avgHuntMs())));
					}
					for (SlayerType type : SlayerType.values()) {
						String best = null;
						for (String tier : com.scd.logic.slayer.SlayerTier.ALL) {
							Long ms = slayer.records().best(type, tier);
							if (ms != null) best = tier + " " + com.scd.logic.Numbers.durationTenths(ms);
						}
						Long xp = slayer.rng().storedXp(type);
						if (best == null && xp == null) continue;
						String pb = best, rng = xp != null ? com.scd.logic.Numbers.compactCount(xp) + " RNG XP" : null;
						o.add(new Opt.Info(type.displayName(), () -> (pb != null ? "PB " + pb : "") + (pb != null && rng != null ? " · " : "") + (rng != null ? rng : "")));
					}
					o.add(new Opt.Buttons("", List.of("Reset slayer session"), List.of(() -> session.reset())));
					var acc = mod.feature(AccessoryFeature.class).service().summary();
					if (acc != null && acc.accessoryPower() != null) {
						o.add(new Opt.Info("Accessory Power", () -> String.valueOf(acc.accessoryPower())));
					}
					return o;
				}),
				new ClickGuiScreen.PanelSection("Advanced features", () -> List.of(
						new Opt.Buttons("", List.of("Carries", "Run stats"), List.of(() -> ClickGuiScreen.openPage(carryPage(mod)),
								() -> ClickGuiScreen.openPage(statsPage(mod)))))));
	}

	// ---- Run stats page ----

	private static String statsFloor;

	private static final List<String> FLOOR_ORDER = List.of("E", "F1", "F2", "F3", "F4", "F5", "F6", "F7", "M1", "M2", "M3", "M4", "M5", "M6", "M7");

	static ClickGuiScreen.PanelPage statsPage(ScdMod mod) {
		var records = mod.feature(com.scd.client.feature.dungeon.DungeonFeature.class).records();
		java.util.function.LongFunction<String> time = ms -> ms <= 0 ? "-" : String.format(Locale.ROOT, "%d:%02d", ms / 60_000, ms / 1000 % 60);
		return new ClickGuiScreen.PanelPage("Run stats", List.of(
				new ClickGuiScreen.PanelSection("Floor", () -> {
					List<String> floors = new ArrayList<>();
					for (String f : FLOOR_ORDER) if (records.runs.stream().anyMatch(r -> f.equals(r.floor))) floors.add(f);
					if (floors.isEmpty()) return List.of(new Opt.Info("No runs recorded yet", () -> ""));
					if (statsFloor == null || !floors.contains(statsFloor)) statsFloor = records.runs.getLast().floor;
					List<Opt> o = new ArrayList<>();
					// Chips in rows of five so every floor fits.
					for (int i = 0; i < floors.size(); i += 5) {
						o.add(new Opt.Chips("", floors.subList(i, Math.min(floors.size(), i + 5)), () -> statsFloor, v -> statsFloor = v));
					}
					return o;
				}),
				new ClickGuiScreen.PanelSection("Summary", () -> {
					var runs = records.runs.stream().filter(r -> r.floor != null && r.floor.equals(statsFloor)).toList();
					if (runs.isEmpty()) return List.of();
					var scored = runs.stream().filter(r -> r.finalScore != null).toList();
					var cleared = runs.stream().map(r -> r.splits.getOrDefault("CLEAR", 0L)).filter(t -> t > 0).toList();
					var withSecrets = runs.stream().filter(r -> r.secrets != null).toList();
					long sPlus = scored.stream().filter(r -> r.finalScore >= 300).count();
					List<Opt> o = new ArrayList<>();
					o.add(new Opt.Info("Runs", () -> String.valueOf(runs.size())));
					if (!scored.isEmpty()) {
						o.add(new Opt.Info("S+ rate", () -> Math.round(100.0 * sPlus / scored.size()) + "% (" + sPlus + "/" + scored.size() + ")"));
						o.add(new Opt.Info("Average score", () -> String.valueOf(Math.round(scored.stream().mapToInt(r -> r.finalScore).average().orElse(0)))));
						o.add(new Opt.Info("Best score", () -> String.valueOf(scored.stream().mapToInt(r -> r.finalScore).max().orElse(0))));
					}
					if (!cleared.isEmpty()) {
						o.add(new Opt.Info("Best time", () -> time.apply(cleared.stream().mapToLong(Long::longValue).min().orElse(0))));
						o.add(new Opt.Info("Average time", () -> time.apply(Math.round(cleared.stream().mapToLong(Long::longValue).average().orElse(0)))));
					}
					if (!withSecrets.isEmpty()) {
						o.add(new Opt.Info("Average secrets", () -> String.format(Locale.ROOT, "%.1f", withSecrets.stream().mapToInt(r -> r.secrets).average().orElse(0))));
					}
					o.add(new Opt.Info("Average deaths", () -> String.format(Locale.ROOT, "%.1f", runs.stream().mapToInt(r -> r.deaths).average().orElse(0))));
					return o;
				}),
				new ClickGuiScreen.PanelSection("Recent runs", () -> {
					var runs = records.runs.stream().filter(r -> r.floor != null && r.floor.equals(statsFloor)).toList();
					List<Opt> o = new ArrayList<>();
					for (int i = runs.size() - 1; i >= 0 && o.size() < 8; i--) {
						var r = runs.get(i);
						long ago = System.currentTimeMillis() - r.endedAt;
						String when = ago < 60_000 ? "just now" : ago < 3_600_000 ? ago / 60_000 + "m ago" : ago < 86_400_000 ? ago / 3_600_000 + "h ago" : ago / 86_400_000 + "d ago";
						String score = r.finalScore != null ? r.finalScore + (r.finalScore >= 300 ? " S+" : r.finalScore >= 270 ? " S" : "") : "?";
						o.add(new Opt.Info(when, () -> time.apply(r.splits.getOrDefault("CLEAR", 0L)) + " · " + score
								+ (r.deaths > 0 ? " · " + r.deaths + " death" + (r.deaths > 1 ? "s" : "") : "")));
					}
					return o;
				})));
	}

	// ---- Carries page ---------------------------------------------------------------------------

	private static final String DUNGEON = "Dungeon", SLAYER = "Slayer";
	private static String newKind = DUNGEON;
	private static SlayerType newSlayer = SlayerType.ZOMBIE;
	private static String newTier = "IV";
	/** Form feedback under the Add button, and its colour. */
	private static String carryStatus = "";
	private static int carryStatusColor = 0xFFF87171;

	private static final Map<SlayerType, String> SLAYER_SHORT = new java.util.EnumMap<>(Map.of(
			SlayerType.ZOMBIE, "Rev", SlayerType.SPIDER, "Tara", SlayerType.WOLF, "Sven",
			SlayerType.ENDERMAN, "Eman", SlayerType.BLAZE, "Blaze"));

	private static int maxTier(SlayerType t) {
		return switch (t) {
			case WOLF, ENDERMAN, BLAZE -> 4;
			default -> 5;
		};
	}

	static ClickGuiScreen.PanelPage carryPage(ScdMod mod) {
		ScdConfig c = mod.config();
		CarryService carries = mod.feature(CarryService.class);
		var coins = (java.util.function.LongFunction<String>) com.scd.logic.Numbers::compactCoins;
		return new ClickGuiScreen.PanelPage("Carries", List.of(
				new ClickGuiScreen.PanelSection("Active carries", () -> {
					List<Opt> o = new ArrayList<>();
					var active = carries.active();
					if (active.isEmpty()) o.add(new Opt.Info("No active carries", () -> ""));
					for (var carry : active) {
						o.add(new Opt.Info(carry.customer + " · " + carry.target(), () -> coins.apply(carry.earned()) + " / " + coins.apply(carry.totalPrice())));
						o.add(new Opt.Progress(carry.unitsDone + " of " + carry.unitsOwed + " " + carry.unit(),
								() -> carry.unitsOwed == 0 ? 0 : carry.unitsDone / (double) carry.unitsOwed,
								() -> carry.remaining() == 0 ? "done" : carry.remaining() + " left"));
						o.add(new Opt.Buttons("", List.of("+1", "-1", "Finish", "Remove"), List.of(
								() -> carries.adjust(carry, 1), () -> carries.adjust(carry, -1), () -> carries.finish(carry), () -> carries.remove(carry))));
					}
					return o;
				}),
				new ClickGuiScreen.PanelSection("New carry", () -> {
					List<Opt> o = new ArrayList<>();
					o.add(new Opt.Text("Clients", () -> newCustomer, v -> newCustomer = v.trim(),
							typed -> com.scd.client.hypixel.Players.suggest(typed, 20)));
					o.add(new Opt.Dropdown("Add player from lobby", () -> com.scd.client.hypixel.Players.suggest("", 200), v -> newCustomer = v));
					o.add(new Opt.Chips("", List.of(DUNGEON, SLAYER), () -> newKind, v -> newKind = v));
					if (newKind.equals(DUNGEON)) {
						var floors = com.scd.logic.dungeon.Floor.CARRYABLE;
						o.add(new Opt.Chips("", floors.subList(0, 7), () -> newWhat, v -> newWhat = v));
						o.add(new Opt.Chips("", floors.subList(7, 14), () -> newWhat, v -> newWhat = v));
					} else {
						List<String> bosses = new ArrayList<>(SLAYER_SHORT.values());
						o.add(new Opt.Chips("", bosses, () -> SLAYER_SHORT.get(newSlayer), v -> {
							for (var e : SLAYER_SHORT.entrySet()) if (e.getValue().equals(v)) newSlayer = e.getKey();
							if (com.scd.logic.slayer.SlayerTier.ALL.indexOf(newTier) >= maxTier(newSlayer)) newTier = com.scd.logic.slayer.SlayerTier.ALL.get(maxTier(newSlayer) - 1);
						}));
						o.add(new Opt.Chips("Tier", com.scd.logic.slayer.SlayerTier.ALL.subList(0, maxTier(newSlayer)), () -> newTier, v -> newTier = v));
					}
					String unit = newKind.equals(DUNGEON) ? "Runs" : "Kills";
					o.add(new Opt.Buttons(unit + "  " + newCount, List.of("-5", "-1", "+1", "+5"), List.of(
							() -> newCount = Math.max(1, newCount - 5), () -> newCount = Math.max(1, newCount - 1),
							() -> newCount = Math.min(999, newCount + 1), () -> newCount = Math.min(999, newCount + 5))));
					o.add(new Opt.Text("Price each", () -> newPrice, v -> newPrice = v.trim()));
					o.add(new Opt.Info("Total", () -> {
						var p = com.scd.logic.Numbers.parseCompactLong(newPrice);
						return p.isPresent() && p.getAsLong() > 0 ? newCount + " × " + coins.apply(p.getAsLong()) + " = " + coins.apply(p.getAsLong() * newCount) : "-";
					}));
					o.add(new Opt.Buttons("", List.of("Add carry"), List.of(() -> addCarry(carries))));
					if (!carryStatus.isEmpty()) o.add(new Opt.Note(() -> carryStatus, carryStatusColor));
					return o;
				}),
				new ClickGuiScreen.PanelSection("History", () -> {
					List<Opt> o = new ArrayList<>();
					o.add(new Opt.Info("Earned", () -> coins.apply(carries.earnedTotal())));
					o.add(new Opt.Info("Still owed", () -> coins.apply(carries.outstandingTotal())));
					int shown = 0;
					for (var carry : carries.all()) {
						if (carry.isActive()) continue;
						if (shown++ >= 6) break;
						o.add(new Opt.Info(carry.customer + " · " + carry.target() + " ×" + carry.unitsOwed, () -> coins.apply(carry.earned())));
					}
					return o;
				}),
				new ClickGuiScreen.PanelSection("Party messages", () -> List.of(
						new Opt.Toggle("Progress in party chat", () -> c.carries.partyProgress, v -> c.carries.partyProgress = v),
						new Opt.Text("Progress msg", () -> c.carries.progressTemplate, v -> c.carries.progressTemplate = v),
						new Opt.Text("Finish msg", () -> c.carries.finishTemplate, v -> c.carries.finishTemplate = v),
						new Opt.Info("Tags", () -> "{player} {done} {owed} {unit}")))));
	}

	private static void addCarry(CarryService carries) {
		var price = com.scd.logic.Numbers.parseCompactLong(newPrice);
		carryStatusColor = 0xFFF87171;
		if (newCustomer.isEmpty()) {
			carryStatus = "Enter the client's name";
			return;
		}
		if (price.isEmpty() || price.getAsLong() <= 0) {
			carryStatus = "Enter a price, like 1.5m or 800k";
			return;
		}
		String what;
		if (newKind.equals(DUNGEON)) {
			what = newWhat;
			carries.addDungeon(newCustomer, newWhat, price.getAsLong(), newCount);
		} else {
			what = newSlayer.displayName() + " " + newTier;
			carries.addSlayer(newCustomer, newSlayer, newTier, price.getAsLong(), newCount);
		}
		carryStatus = "Added " + newCustomer + " · " + what + " ×" + newCount;
		carryStatusColor = 0xFF4ADE80;
		newCustomer = "";
		newPrice = "";
		newCount = 1;
	}
}
