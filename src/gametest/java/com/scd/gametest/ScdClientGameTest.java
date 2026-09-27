package com.scd.gametest;

import com.scd.client.ScdMod;
import com.scd.client.feature.accessory.AccessoryFeature;
import com.scd.client.feature.accessory.AccessoryScreen;
import com.scd.client.feature.carry.Carry;
import com.scd.client.feature.carry.CarryFormScreen;
import com.scd.client.feature.carry.CarryScreen;
import com.scd.client.feature.carry.CarryService;
import com.scd.client.feature.dungeon.DungeonFeature;
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
			if (System.getenv("SCD_KEY") != null) valuationScenario(ctx, server, mod);
			if (debugBackend != null) {
				ctx.runOnClient(mc -> mc.player.connection.sendCommand("scd record mark end of scenarios"));
				ctx.runOnClient(mc -> mc.player.connection.sendCommand("scd record stop"));
				ctx.waitTicks(40);
			}
		}
	}

	private static void screenshots(ClientGameTestContext ctx, ScdMod mod) {
		shot(ctx, "01-main", () -> new MainScreen(null, mod));
		shot(ctx, "02-slayer", () -> new SlayerScreen(null, mod, mod.feature(SlayerFeature.class)));
		shot(ctx, "03-carries", () -> new CarryScreen(null, mod.feature(CarryService.class)));
		shot(ctx, "04-carry-form", () -> new CarryFormScreen(null, mod.feature(CarryService.class)));
		shot(ctx, "05-dungeons", () -> new DungeonScreen(null, mod, mod.feature(DungeonFeature.class)));
		shot(ctx, "06-market", () -> new MarketScreen(null, mod));
		shot(ctx, "07-general", () -> new GeneralScreen(null, mod));
		shot(ctx, "08-accessories", () -> new AccessoryScreen(null, mod, mod.feature(AccessoryFeature.class)));
		shot(ctx, "09-hud-editor", () -> new HudEditorScreen(null, mod.huds, mod.configManager));
		if (System.getenv("SCD_THEME_SHOTS") != null) {
			for (String theme : List.of("Dusk", "Tidepool", "Ember", "Sakura", "Signal")) {
				ctx.runOnClient(mc -> com.scd.client.ui.Ui.setTheme(com.scd.client.ui.Theme.byName(theme)));
				shot(ctx, "theme-" + theme, () -> new SlayerScreen(null, mod, mod.feature(SlayerFeature.class)));
			}
			ctx.runOnClient(mc -> com.scd.client.ui.Ui.setTheme(com.scd.client.ui.Theme.byName("Midnight")));
		}
		ctx.setScreen(() -> null);
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
		ctx.takeScreenshot("12-dungeon-score-hud");

		String border = "▬".repeat(64);
		for (String line : List.of(border, "The Catacombs - Floor VI", "Team Score: 273 (S)", "☠ Defeated Sadan in 04m 46s",
				"+150,000 Catacombs Experience", border)) {
			server.runCommand("tellraw @a \"" + line + "\"");
		}
		ctx.waitTicks(60);
		check(ctx.computeOnClient(mc -> mod.feature(DungeonFeature.class).lastReport()) != null, "completion report not parsed");
		check(ctx.computeOnClient(mc -> carries.find(carry.id).unitsDone) == 1, "dungeon carry not credited");
		ctx.takeScreenshot("13-after-dungeon-completion");
		ctx.runOnClient(mc -> carries.remove(carry));
	}
}
