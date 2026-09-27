package com.scd.gametest;

import com.scd.client.ScdMod;
import com.scd.client.feature.accessory.AccessoryFeature;
import com.scd.client.feature.accessory.AccessoryScreen;
import com.scd.client.feature.carry.Carry;
import com.scd.client.feature.carry.CarryFormScreen;
import com.scd.client.feature.carry.CarryScreen;
import com.scd.client.feature.carry.CarryService;
import com.scd.client.feature.dungeon.DungeonFeature;
import com.scd.client.feature.dungeon.MappedRoom;
import com.scd.client.feature.dungeon.DungeonScreen;
import com.scd.client.feature.slayer.SlayerFeature;
import com.scd.client.feature.slayer.SlayerScreen;
import com.scd.client.feature.slayer.SlayerType;
import com.scd.client.hud.HudEditorScreen;
import com.scd.client.hypixel.Players;
import com.scd.client.screen.GeneralScreen;
import com.scd.client.screen.MainScreen;
import com.scd.client.screen.MarketScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.util.List;

/**
 * End-to-end checks in a real 26.2 client: fakes Hypixel's sidebar (team-prefix lines under
 * invisible owners, exactly like SkyBlock), a named Slayer boss with a "Spawned by" tag, and the raw
 * chat lines SCD parses - then asserts on SCD's state and screenshots every screen.
 */
public final class ScdClientGameTest implements FabricClientGameTest {
	private static final String OWNERS = "0123456789abcdef";

	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (var world = ctx.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			ctx.waitTicks(40);
			ScdMod mod = ctx.computeOnClient(mc -> ScdMod.get());
			ctx.runOnClient(mc -> {
				com.scd.client.core.ScdLog.setDebug(true);
				com.scd.client.hypixel.ChatRouter.captureToLog = true;
			});
			server.runCommand("gamerule doDaylightCycle false");
			server.runCommand("time set noon");

			screenshots(ctx, mod);
			// Record the scenarios into a local debug backend when one is running (see backend/debugSessions.js).
			String debugBackend = System.getenv("SCD_TEST_BACKEND");
			if (debugBackend != null) {
				ctx.runOnClient(mc -> {
					mod.backend.setBaseUrl(debugBackend);
					mc.player.connection.sendCommand("scd record start");
				});
				ctx.waitTicks(5);
				String creds = ctx.computeOnClient(mc -> mod.feature(com.scd.client.feature.debug.SessionRecorder.class).credentials());
				check(creds != null, "recording did not start via /scd record start");
				System.out.println("SCD_TEST_SESSION " + creds);
			}
			slayerScenario(ctx, server, mod);
			dungeonScenario(ctx, server, mod);
			roomScenario(ctx, server, mod);
			lagScenario(ctx, server, mod);
			if (System.getenv("SCD_KEY") != null) valuationScenario(ctx, server, mod);
			if (debugBackend != null) {
				ctx.runOnClient(mc -> mc.player.connection.sendCommand("scd record mark end of scenarios"));
				ctx.runOnClient(mc -> mc.player.connection.sendCommand("scd record stop"));
				ctx.waitTicks(40);
			}
		}
	}

	private static void screenshots(ClientGameTestContext ctx, ScdMod mod) {
		ctx.runOnClient(mc -> {
			com.scd.client.ui.clickgui.ClickGuiScreen.expand("Dungeons/Score HUD");
			com.scd.client.ui.clickgui.ClickGuiScreen.expand("Slayer/Spawn alert");
			com.scd.client.ui.clickgui.ClickGuiScreen.expand("HUD/Dungeon map");
		});
		shot(ctx, "00-clickgui", () -> new com.scd.client.ui.clickgui.ClickGuiScreen(mod));
		textFieldTest(ctx, mod);
		shot(ctx, "00b-subpage", () -> new AccessoryScreen(new com.scd.client.ui.clickgui.ClickGuiScreen(mod), mod, mod.feature(AccessoryFeature.class)));
		shot(ctx, "01-main", () -> new MainScreen(null, mod));
		shot(ctx, "02-slayer", () -> new SlayerScreen(null, mod, mod.feature(SlayerFeature.class)));
		shot(ctx, "03-carries", () -> new CarryScreen(null, mod.feature(CarryService.class)));
		shot(ctx, "04-carry-form", () -> new CarryFormScreen(null, mod.feature(CarryService.class)));
		shot(ctx, "05-dungeons", () -> new DungeonScreen(null, mod, mod.feature(DungeonFeature.class)));
		shot(ctx, "06-market", () -> new MarketScreen(null, mod));
		shot(ctx, "07-general", () -> new GeneralScreen(null, mod));
		shot(ctx, "08-accessories", () -> new AccessoryScreen(null, mod, mod.feature(AccessoryFeature.class)));
		shot(ctx, "09b-appearance", () -> com.scd.client.screen.AppearanceScreen.forHud(null, mod, mod.huds.elements().getFirst()));
		shot(ctx, "09-hud-editor", () -> new HudEditorScreen(null, mod.huds, mod.configManager));
		if (System.getenv("SCD_FPS") != null) {
			ctx.setScreen(() -> null);
			ctx.waitTicks(100);
			int base = ctx.computeOnClient(mc -> mc.getFps());
			ctx.setScreen(() -> new MainScreen(null, mod));
			ctx.waitTicks(100);
			int overview = ctx.computeOnClient(mc -> mc.getFps());
			ctx.setScreen(() -> new SlayerScreen(null, mod, mod.feature(SlayerFeature.class)));
			ctx.waitTicks(100);
			int slayerPage = ctx.computeOnClient(mc -> mc.getFps());
			long slayerNs = com.scd.client.ui.ScdScreen.avgFrameNanos;
			ctx.setScreen(() -> new MainScreen(null, mod));
			ctx.waitTicks(60);
			long overviewNs = com.scd.client.ui.ScdScreen.avgFrameNanos;
			System.out.println("SCD_FPS base=" + base + " overview=" + overview + " slayer=" + slayerPage
					+ " | SCD draw cost per frame: overview=" + overviewNs / 1000 + "us slayer=" + slayerNs / 1000 + "us");
			ctx.setScreen(() -> null);
		}
		if (System.getenv("SCD_SCALE_SHOTS") != null) {
			for (int scale : new int[] {2, 3}) {
				ctx.runOnClient(mc -> {
					mc.options.guiScale().set(scale);
					mc.resizeGui();
				});
				shot(ctx, "scale" + scale + "-clickgui", () -> new com.scd.client.ui.clickgui.ClickGuiScreen(mod));
				shot(ctx, "scale" + scale + "-main", () -> new MainScreen(null, mod));
				shot(ctx, "scale" + scale + "-slayer", () -> new SlayerScreen(null, mod, mod.feature(SlayerFeature.class)));
			}
			ctx.runOnClient(mc -> {
				mc.options.guiScale().set(0);
				mc.resizeGui();
			});
			for (int size : new int[] {70, 100, 110}) {
				ctx.runOnClient(mc -> com.scd.client.ui.Ui.setMenuTextSize(size));
				shot(ctx, "text" + size + "-market", () -> new MarketScreen(null, mod));
			}
			ctx.runOnClient(mc -> com.scd.client.ui.Ui.setMenuTextSize(mod.config().general.menuTextSize));
		}
		if (System.getenv("SCD_THEME_SHOTS") != null) {
			for (String theme : List.of("Dusk", "Tidepool", "Ember", "Sakura", "Signal")) {
				ctx.runOnClient(mc -> com.scd.client.ui.Ui.setTheme(com.scd.client.ui.Theme.byName(theme)));
				shot(ctx, "theme-" + theme, () -> new SlayerScreen(null, mod, mod.feature(SlayerFeature.class)));
			}
			ctx.runOnClient(mc -> com.scd.client.ui.Ui.setTheme(com.scd.client.ui.Theme.byName("Midnight")));
		}
		ctx.setScreen(() -> null);
	}

	/** Open the Carries page, click its Customer field, type, and check both what's shown and what's saved. */
	private static void textFieldTest(ClientGameTestContext ctx, ScdMod mod) {
		ctx.runOnClient(mc -> com.scd.client.ui.clickgui.ClickGuiScreen.openCarries(mod));
		ctx.setScreen(() -> new com.scd.client.ui.clickgui.ClickGuiScreen(mod));
		ctx.waitTicks(5);
		double[] at = ctx.computeOnClient(mc -> {
			var w = mc.getWindow();
			int density = Math.max(1, Math.round(w.getHeight() / 540f));
			int vw = w.getWidth() / density;
			int pw = Math.max(120, vw / 5), cx = vw - pw + 6, cw = pw - 12;
			// "Active carries" card (header 16 + one row + 2), gap 6, "New carry" header 16, then Customer.
			int y = 28 + 31 + 6 + 16 + 6;
			return new double[]{(cx + cw * 3 / 4) * density, y * density};
		});
		ctx.getInput().setCursorPos(at[0], at[1]);
		ctx.getInput().pressMouse(0);
		ctx.waitTicks(2);
		ctx.getInput().typeChars("Bob");
		ctx.waitTicks(3);
		ctx.takeScreenshot("00c-text-field");
		ctx.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
		ctx.waitTicks(2);
		String saved = ctx.computeOnClient(mc -> {
			try {
				var f = Class.forName("com.scd.client.ui.clickgui.ClickGuiContent").getDeclaredField("newCustomer");
				f.setAccessible(true);
				return (String) f.get(null);
			} catch (ReflectiveOperationException e) {
				return "<" + e + ">";
			}
		});
		if (!"Bob".equals(saved)) throw new AssertionError("text field saved '" + saved + "' instead of 'Bob'");
		ctx.takeScreenshot("00d-carries-page");
		ctx.runOnClient(mc -> com.scd.client.ui.clickgui.ClickGuiScreen.closePage());
	}

	private static void shot(ClientGameTestContext ctx, String name, java.util.function.Supplier<net.minecraft.client.gui.screens.Screen> screen) {
		ctx.setScreen(screen);
		ctx.waitTicks(5);
		ctx.takeScreenshot(name);
	}

	/** Sidebar lines top to bottom, the way Hypixel builds them. */
	private static void sidebar(TestServerContext server, List<String> lines) {
		server.runOnServer((MinecraftServer s) -> {
			var sb = s.getScoreboard();
			Objective old = sb.getObjective("sb");
			if (old != null) sb.removeObjective(old);
			for (int i = 0; i < 16; i++) {
				var team = sb.getPlayerTeam("sbl" + i);
				if (team != null) sb.removePlayerTeam(team);
			}
			Objective obj = sb.addObjective("sb", ObjectiveCriteria.DUMMY, Component.literal("SKYBLOCK"),
					ObjectiveCriteria.RenderType.INTEGER, false, null);
			sb.setDisplayObjective(DisplaySlot.SIDEBAR, obj);
			for (int i = 0; i < lines.size(); i++) {
				String owner = "§" + OWNERS.charAt(i);
				var team = sb.addPlayerTeam("sbl" + i);
				team.setPlayerPrefix(Component.literal(lines.get(i)));
				sb.addPlayerToTeam(owner, team);
				sb.getOrCreatePlayerScore(ScoreHolder.forNameOnly(owner), obj).set(lines.size() - i);
			}
		});
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}

	private static void slayerScenario(ClientGameTestContext ctx, TestServerContext server, ScdMod mod) {
		SlayerFeature slayer = mod.feature(SlayerFeature.class);
		String self = ctx.computeOnClient(mc -> Players.selfName());

		sidebar(server, List.of("Dec 12th", "⏣ Graveyard", "", "Slayer Quest", "Revenant Horror IV", "(0/2,400) Combat XP"));
		ctx.waitTicks(10);
		// Outside the slayer's areas the quest is dormant: no HUD.
		sidebar(server, List.of("Dec 12th", "⏣ Village", "", "Slayer Quest", "Revenant Horror IV", "(0/2,400) Combat XP"));
		ctx.waitTicks(5);
		check(ctx.computeOnClient(mc -> mod.feature(SlayerFeature.class).tracker().quest() == null), "Revenant quest should be hidden in Village");
		check(ctx.computeOnClient(mc -> mod.feature(SlayerFeature.class).tracker().questAnywhere() != null), "quest itself should keep running outside its area");
		sidebar(server, List.of("Dec 12th", "⏣ Graveyard", "", "Slayer Quest", "Revenant Horror IV", "(0/2,400) Combat XP"));
		ctx.waitTicks(5);
		check(ctx.computeOnClient(mc -> slayer.tracker().quest()) != null, "quest not detected from sidebar");
		check(!ctx.computeOnClient(mc -> slayer.tracker().quest().bossSpawned()), "boss wrongly marked spawned");
		check("IV".equals(ctx.computeOnClient(mc -> slayer.tracker().quest().tier())), "tier not parsed");

		sidebar(server, List.of("Dec 12th", "⏣ Graveyard", "", "Slayer Quest", "Revenant Horror IV", "Slay the boss!"));
		server.runCommand("execute as @p at @s run summon pig ~3 ~ ~ {NoAI:1b,CustomNameVisible:1b,CustomName:\"☠ Revenant Horror 380,000/400,000❤\"}");
		server.runCommand("execute as @p at @s run summon armor_stand ~3 ~1.4 ~ {Invisible:1b,Marker:1b,NoGravity:1b,CustomNameVisible:1b,CustomName:\"Spawned by: " + self + "\"}");
		ctx.waitTicks(20);
		check(ctx.computeOnClient(mc -> slayer.tracker().boss()) != null, "boss entity not located via Spawned-by tag");
		Float frac = ctx.computeOnClient(mc -> slayer.tracker().hpFraction());
		check(frac != null && Math.abs(frac - 0.95f) < 0.01f, "HP fraction wrong: " + frac);
		ctx.takeScreenshot("10-slayer-fight-hud");

		int before = ctx.computeOnClient(mc -> slayer.records().kills(SlayerType.ZOMBIE, "IV"));
		sidebar(server, List.of("Dec 12th", "⏣ Graveyard", "", "Slayer Quest", "Revenant Horror IV"));
		server.runCommand("tellraw @a \"  SLAYER QUEST FAILED!\"");
		ctx.waitTicks(120); // past the 5s no-verdict fallback, so this really proves FAILED was honoured
		check(ctx.computeOnClient(mc -> slayer.records().kills(SlayerType.ZOMBIE, "IV")) == before, "failed quest was counted as a kill");

		sidebar(server, List.of("Dec 12th", "⏣ Graveyard", "", "Slayer Quest", "Revenant Horror IV", "Slay the boss!"));
		ctx.waitTicks(10);
		sidebar(server, List.of("Dec 12th", "⏣ Graveyard", "", "Slayer Quest", "Revenant Horror IV"));
		server.runCommand("tellraw @a \"  SLAYER QUEST COMPLETE!\"");
		ctx.waitTicks(10);
		check(ctx.computeOnClient(mc -> slayer.records().kills(SlayerType.ZOMBIE, "IV")) == before + 1, "completed kill not recorded");
		ctx.takeScreenshot("11-slayer-after-kill");
		// HUD at 80% size must stay crisp (density-matched font, not a resampled one).
		ctx.runOnClient(mc -> mod.huds.elements().forEach(el -> mod.huds.layout(el).scale = 0.8f));
		ctx.waitTicks(3);
		ctx.takeScreenshot("11b-slayer-hud-80");
		ctx.runOnClient(mc -> mod.huds.elements().forEach(el -> mod.huds.layout(el).scale = 1f));
		server.runCommand("kill @e[type=!player]");
	}

	/** Real market call: an item with stars + potato books must be valued above its clean price. */
	private static void valuationScenario(ClientGameTestContext ctx, TestServerContext server, ScdMod mod) {
		server.runCommand("give @p diamond_sword[custom_data={id:\"HYPERION\",upgrade_level:5,hot_potato_count:15,uuid:\"scd-test-0001\"}]");
		ctx.waitTicks(10);
		var bazaar = mod.feature(com.scd.client.feature.bazaar.BazaarFeature.class);
		var stack = ctx.computeOnClient(mc -> mc.player.getInventory().getItem(0).copy());
		check(com.scd.client.feature.bazaar.ItemValuation.hasAddons(stack), "stars/books not seen as add-ons");
		com.scd.client.net.Backend.ItemValue v = null;
		for (int i = 0; i < 40 && v == null; i++) {
			v = ctx.computeOnClient(mc -> bazaar.valuation().get(stack));
			ctx.waitTicks(5);
		}
		check(v != null && v.estimatedValue() != null, "no valuation returned for the test Hyperion");
		check(v.addonsValue() > 0, "add-ons not valued: " + v);
		System.out.println("SCD_TEST_VALUATION " + v);
	}

	private static void dungeonScenario(ClientGameTestContext ctx, TestServerContext server, ScdMod mod) {
		CarryService carries = mod.feature(CarryService.class);
		Carry carry = ctx.computeOnClient(mc -> carries.addDungeon("TestCustomer", "F6", 2_000_000, 3));

		sidebar(server, List.of("Dec 12th", "⏣ The Catacombs (F6)", "", "Time Elapsed: 04m 10s", "Cleared: 100% (273)"));
		ctx.waitTicks(10);
		check(ctx.computeOnClient(mc -> mod.feature(DungeonFeature.class).state().inDungeon()), "dungeon not detected");
		check("F6".equals(ctx.computeOnClient(mc -> mod.feature(DungeonFeature.class).state().floor())), "floor not parsed");
		// Splits and the mimic: Mort starts the clock, the blood door and Watcher split, a baby zombie's death is the mimic.
		server.runCommand("tellraw @a \"[NPC] Mort: Here, I found this map when I first entered the dungeon.\"");
		ctx.waitTicks(20);
		server.runCommand("tellraw @a \"The BLOOD DOOR has been opened!\"");
		server.runCommand("execute as @p at @s run summon zombie ~3 ~ ~ {IsBaby:1b,NoAI:1b}");
		ctx.waitTicks(10);
		server.runCommand("kill @e[type=zombie]");
		ctx.waitTicks(10);
		server.runCommand("tellraw @a \"[BOSS] The Watcher: You have proven yourself. You may pass.\"");
		server.runCommand("tellraw @a \"[BOSS] Sadan: So you made it all the way here... Now you wish to defy me? Sadan?!\"");
		ctx.waitTicks(10);
		server.runCommand("tellraw @a \"         What is the status of Bonzo?\"");
		server.runCommand("tellraw @a \"     ⓐ Apprentice Necromancer\"");
		server.runCommand("tellraw @a \"     ⓑ New Necromancer\"");
		server.runCommand("tellraw @a \" ☠ Steve was killed by Crypt Lurker and became a ghost.\"");
		server.runCommand("tellraw @a \"Your ⚚ Bonzo's Mask saved your life!\"");
		ctx.waitTicks(5);
		DungeonFeature df = mod.feature(DungeonFeature.class);
		check(ctx.computeOnClient(mc -> df.score() != null && df.score().mimic()), "mimic death (baby zombie, entity event 3) not detected");
		check(ctx.computeOnClient(mc -> df.score().inBoss()), "boss entry not detected");
		ctx.takeScreenshot("12-dungeon-score-hud");

		String border = "▬".repeat(64);
		for (String line : List.of(border, "The Catacombs - Floor VI", "Team Score: 273 (S)", "☠ Defeated Sadan in 04m 46s",
				"+150,000 Catacombs Experience", border)) {
			server.runCommand("tellraw @a \"" + line + "\"");
		}
		ctx.waitTicks(60);
		check(ctx.computeOnClient(mc -> mod.feature(DungeonFeature.class).lastReport()) != null, "completion report not parsed");
		check(ctx.computeOnClient(mc -> carries.find(carry.id).unitsDone) == 1, "dungeon carry not credited");
		var pbs = ctx.computeOnClient(mc -> df.records().bestSplits.get("F6"));
		check(pbs != null && pbs.containsKey("BLOOD_OPEN") && pbs.containsKey("BOSS") && pbs.get("CLEAR") == 286_000L, "splits/PBs not recorded: " + pbs);
		System.out.println("SCD_TEST_SPLITS " + pbs);
		ctx.takeScreenshot("13-after-dungeon-completion");
		ctx.runOnClient(mc -> carries.remove(carry));
	}

	/**
	 * Room engine end to end: build a room "core" column at tile (2,2) with its blue-terracotta clay
	 * on the WEST corner, stand on it inside a fake Catacombs sidebar, name the unknown room with
	 * /scd dungeon room name, and check it is then identified from its core and anchored WEST from the block.
	 */
	private static void roomScenario(ClientGameTestContext ctx, TestServerContext server, ScdMod mod) {
		DungeonFeature dungeon = mod.feature(DungeonFeature.class);
		ctx.runOnClient(mc -> {
			try {
				java.nio.file.Files.deleteIfExists(com.scd.client.storage.ScdPaths.file("dungeon/rooms.json"));
			} catch (java.io.IOException e) {
				throw new RuntimeException(e);
			}
			dungeon.rooms().reloadDatabase();
			mod.config().dungeon.roomDebug = true;
		});
		int c = com.scd.logic.dungeon.room.DungeonGrid.centre(2); // -121
		server.runCommand("forceload add " + (c - 16) + " " + (c - 16) + " " + (c + 16) + " " + (c + 16));
		server.runCommand("fill " + c + " 80 " + c + " " + c + " 89 " + c + " stone_bricks");
		server.runCommand("setblock " + c + " 90 " + c + " glass");
		server.runCommand("fill " + c + " 60 " + c + " " + c + " 61 " + c + " bedrock");
		server.runCommand("fill " + (c - 15) + " 90 " + (c - 15) + " " + (c + 15) + " 90 " + (c + 15) + " smooth_stone replace air");
		server.runCommand("setblock " + c + " 90 " + c + " glass");
		server.runCommand("setblock " + (c + 15) + " 90 " + (c - 15) + " blue_terracotta");
		server.runCommand("tp @p " + (c + 6) + " 91 " + (c - 6));
		sidebar(server, List.of("Dec 12th", "⏣ The Catacombs (F7)", "", "Time Elapsed: 01m 10s", "Cleared: 12% (80)"));
		ctx.waitTicks(40);
		check(ctx.computeOnClient(mc -> dungeon.rooms().current() != null), "no room at the player's tile: " + ctx.computeOnClient(mc -> dungeon.rooms().describe()));
		check(ctx.computeOnClient(mc -> dungeon.rooms().current().name() == null), "test room should start unknown");

		ctx.runOnClient(mc -> mc.player.connection.sendCommand("scd dungeon room name \"SCD Test Room\" 3"));
		ctx.waitTicks(40);
		MappedRoom room = ctx.computeOnClient(mc -> dungeon.rooms().current());
		check(room != null && "SCD Test Room".equals(room.name()), "named room not identified by its core: " + (room == null ? null : room.label()));
		check(room.info().secrets() == 3, "secret count not saved");
		check(room.anchor() != null && room.anchor().rotation() == com.scd.logic.dungeon.room.RoomRotation.WEST,
				"clay not found / wrong rotation: " + room.anchor());
		check(room.anchor().x() == c + 15 && room.anchor().z() == c - 15, "anchor not on the clay: " + room.anchor());
		var rel = ctx.computeOnClient(mc -> room.toRelative(mc.player.blockPosition()));
		var back = ctx.computeOnClient(mc -> room.toWorld(rel));
		check(back.equals(ctx.computeOnClient(mc -> mc.player.blockPosition())), "relative frame does not round-trip");
		System.out.println("SCD_TEST_ROOM " + room.label() + " " + room.anchor() + " player rel " + rel);
		ctx.takeScreenshot("14-room-hud");
		routeScenario(ctx, server, mod, c);

		ctx.runOnClient(mc -> {
			try {
				java.nio.file.Files.deleteIfExists(com.scd.client.storage.ScdPaths.file("dungeon/rooms.json"));
			} catch (java.io.IOException e) {
				throw new RuntimeException(e);
			}
			dungeon.rooms().reloadDatabase();
			mod.config().dungeon.roomDebug = false;
		});
		sidebar(server, List.of("Dec 12th", "⏣ Hub"));
	}

	/**
	 * Record a 4-step route (chest, item, bat, exit) in the test room, then play it back and check each
	 * secret advances it; round-trip it through a share code; optionally load a real SecretRoutes pack.
	 */
	private static void routeScenario(ClientGameTestContext ctx, TestServerContext server, ScdMod mod, int c) {
		var routes = mod.feature(com.scd.client.feature.dungeon.route.RouteFeature.class);
		var chest = new net.minecraft.core.BlockPos(c + 3, 91, c + 3);
		server.runCommand("setblock " + chest.getX() + " 91 " + chest.getZ() + " chest");
		server.runCommand("tp @p " + (c - 5) + " 91 " + (c - 5));
		ctx.waitTicks(10);
		ctx.runOnClient(mc -> mc.player.connection.sendCommand("scd route record"));
		ctx.waitTicks(5);
		check(ctx.computeOnClient(mc -> routes.recorderActive()), "recording did not start");
		server.runCommand("tp @p " + (c + 2) + " 91 " + (c + 2));
		ctx.waitTicks(10);
		clickBlock(ctx, chest);
		server.runCommand("tp @p " + (c - 6) + " 91 " + (c - 1) + " -135 20");
		ctx.waitTicks(10);
		ctx.takeScreenshot("14b-route-recording");
		server.runCommand("tp @p " + (c + 8) + " 91 " + (c - 2));
		ctx.waitTicks(10);
		server.runCommand("execute as @p at @s run summon item ~ ~ ~ {Item:{id:\"minecraft:bone\",count:1},PickupDelay:0s}");
		ctx.waitTicks(20);
		server.runCommand("execute as @p at @s run summon bat ~2 ~1 ~ {NoAI:1b}");
		ctx.waitTicks(5);
		server.runCommand("kill @e[type=bat]");
		ctx.waitTicks(10);
		server.runCommand("tp @p " + (c - 10) + " 91 " + (c + 10));
		ctx.waitTicks(10);
		ctx.runOnClient(mc -> mc.player.connection.sendCommand("scd route mark"));
		ctx.waitTicks(2);
		ctx.runOnClient(mc -> mc.player.connection.sendCommand("scd route record stop"));
		ctx.waitTicks(5);
		var steps = ctx.computeOnClient(mc -> routes.library().mine().rooms.get("SCD Test Room"));
		check(steps != null && steps.size() == 4, "expected 4 recorded steps, got " + (steps == null ? null : steps.size()));
		System.out.println("SCD_TEST_ROUTE types " + steps.stream().map(st -> st.secretType.key).toList()
				+ " warps " + steps.stream().mapToInt(st -> st.etherwarps.size()).sum());
		check(steps.get(0).secretType == com.scd.logic.dungeon.route.RouteStep.SecretType.INTERACT, "step 1 should be the chest");
		check(steps.get(1).secretType == com.scd.logic.dungeon.route.RouteStep.SecretType.ITEM, "step 2 should be the item");
		check(steps.get(2).secretType == com.scd.logic.dungeon.route.RouteStep.SecretType.BAT, "step 3 should be the bat");

		// Playback
		server.runCommand("tp @p " + (c - 5) + " 91 " + (c - 5));
		ctx.waitTicks(10);
		check(ctx.computeOnClient(mc -> routes.playbackIndex()) == 0, "playback should start at step 1");
		ctx.takeScreenshot("15-route-playback");
		server.runCommand("tp @p " + (c + 2) + " 91 " + (c + 2));
		ctx.waitTicks(5);
		clickBlock(ctx, chest);
		check(ctx.computeOnClient(mc -> routes.playbackIndex()) == 1, "chest click did not advance");
		server.runCommand("tp @p " + (c + 8) + " 91 " + (c - 2));
		ctx.waitTicks(5);
		server.runCommand("execute as @p at @s run summon item ~ ~ ~ {Item:{id:\"minecraft:bone\",count:1},PickupDelay:0s}");
		ctx.waitTicks(20);
		check(ctx.computeOnClient(mc -> routes.playbackIndex()) == 2, "item pickup did not advance");
		server.runCommand("execute as @p at @s run summon bat ~2 ~1 ~ {NoAI:1b}");
		ctx.waitTicks(5);
		server.runCommand("kill @e[type=bat]");
		ctx.waitTicks(10);
		check(ctx.computeOnClient(mc -> routes.playbackIndex()) == 3, "bat kill did not advance");
		server.runCommand("tp @p " + (c - 10) + " 91 " + (c + 10));
		ctx.waitTicks(10);
		check(ctx.computeOnClient(mc -> routes.playbackIndex()) == 4, "reaching the exit did not finish the route");

		// Share code round trip
		String code = ctx.computeOnClient(mc -> com.scd.logic.dungeon.route.ShareCode.encode("SCD Test Room", routes.library().mine().rooms.get("SCD Test Room")));
		ctx.runOnClient(mc -> {
			routes.library().mine().rooms.remove("SCD Test Room");
			mc.player.connection.sendCommand("scd route import " + code);
		});
		ctx.waitTicks(5);
		check(ctx.computeOnClient(mc -> routes.library().mine().rooms.get("SCD Test Room")).size() == 4, "share code import failed");

		// Several routes per room: the one starting nearest the entrance plays.
		enterRoom(ctx, server, c - 14, c + 10);
		ctx.runOnClient(mc -> mc.player.connection.sendCommand("scd route record"));
		ctx.waitTicks(3);
		server.runCommand("tp @p " + (c - 8) + " 91 " + (c + 8));
		ctx.waitTicks(5);
		ctx.runOnClient(mc -> mc.player.connection.sendCommand("scd route mark"));
		ctx.runOnClient(mc -> mc.player.connection.sendCommand("scd route record stop"));
		ctx.waitTicks(5);
		check(ctx.computeOnClient(mc -> routes.library().mine().routesFor("SCD Test Room").size()) == 2, "second recording did not add a route");
		enterRoom(ctx, server, c - 13, c + 9);
		check("SCD Test Room:2".equals(ctx.computeOnClient(mc -> routes.playingKey())), "wrong route for the second entrance: " + ctx.computeOnClient(mc -> routes.playingKey()));
		enterRoom(ctx, server, c - 5, c - 5);
		check("SCD Test Room".equals(ctx.computeOnClient(mc -> routes.playingKey())), "wrong route for the first entrance: " + ctx.computeOnClient(mc -> routes.playingKey()));
		System.out.println("SCD_TEST_ENTRANCES ok");

		String srPack = System.getenv("SCD_SR_ROUTES");
		if (srPack != null) {
			ctx.runOnClient(mc -> {
				try {
					java.nio.file.Files.copy(java.nio.file.Path.of(srPack), com.scd.client.feature.dungeon.route.RouteLibrary.folder().resolve("secretroutes.json"),
							java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				} catch (java.io.IOException e) {
					throw new RuntimeException(e);
				}
				routes.library().reload();
			});
			var pack = ctx.computeOnClient(mc -> routes.library().packs().stream().filter(p -> p.file().equals("secretroutes.json")).findFirst().orElse(null));
			check(pack != null && pack.pack().rooms.size() >= 90, "SecretRoutes pack not loaded");
			check(ctx.computeOnClient(mc -> !routes.library().routesFor("Altar", java.util.List.of()).isEmpty()), "SecretRoutes 'Altar-6' not mapped onto Altar");
			System.out.println("SCD_TEST_SR_PACK rooms " + pack.pack().rooms.size());
			ctx.runOnClient(mc -> {
				try {
					java.nio.file.Files.deleteIfExists(com.scd.client.feature.dungeon.route.RouteLibrary.folder().resolve("secretroutes.json"));
				} catch (java.io.IOException e) {
					throw new RuntimeException(e);
				}
			});
		}
		ctx.runOnClient(mc -> {
			routes.library().mine().removeRoom("SCD Test Room");
			try {
				routes.library().saveMine();
			} catch (java.io.IOException e) {
				throw new RuntimeException(e);
			}
			routes.library().reload();
		});
	}

	/** Leave the room grid, then walk back in at (x, z) so the room is entered from there. */
	private static void enterRoom(ClientGameTestContext ctx, TestServerContext server, int x, int z) {
		server.runCommand("tp @p 40 91 40");
		ctx.waitTicks(5);
		server.runCommand("tp @p " + x + " 91 " + z);
		ctx.waitTicks(15);
	}

	private static void clickBlock(ClientGameTestContext ctx, net.minecraft.core.BlockPos pos) {
		ctx.runOnClient(mc -> mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND,
				new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false)));
		ctx.waitTicks(5);
		ctx.setScreen(() -> null);
		ctx.waitTicks(2);
	}

	/** Lag scanner: 200 named armor stands must show up as a measured cost with the right count. */
	private static void lagScenario(ClientGameTestContext ctx, TestServerContext server, ScdMod mod) {
		var scanner = mod.feature(com.scd.client.feature.perf.LagScanner.class);
		server.runCommand("tp @p 0 -60 0 0 0");
		for (int i = 0; i < 200; i++) {
			server.runCommand("summon armor_stand " + (i % 20 - 10) + " -60 " + (i / 20 + 3) + " {NoGravity:1b,CustomNameVisible:1b,CustomName:\"stand " + i + "\"}");
		}
		ctx.runOnClient(mc -> {
			mod.config().perf.lagScanner = true;
			scanner.setEnabled(true);
		});
		ctx.waitTicks(70);
		var snap = ctx.computeOnClient(mc -> scanner.latest());
		check(snap != null, "lag scanner produced no snapshot");
		var stands = snap.top().stream().filter(l -> l.label().equals("Armor Stand")).findFirst().orElse(null);
		System.out.println("SCD_TEST_LAG fps=" + Math.round(snap.fps()) + " top=" + snap.top());
		check(stands != null && stands.count() >= 200, "armor stands not measured: " + snap.top());
		ctx.takeScreenshot("16-lag-scanner");
		ctx.runOnClient(mc -> mc.player.connection.sendCommand("scd lag report"));
		ctx.waitTicks(5);
		ctx.runOnClient(mc -> {
			mod.config().perf.lagScanner = false;
			scanner.setEnabled(false);
		});
		server.runCommand("kill @e[type=armor_stand]");
	}
}
