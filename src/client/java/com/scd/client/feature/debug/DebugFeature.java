package com.scd.client.feature.debug;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.scd.client.Feature;
import com.scd.client.ScdMod;
import com.scd.client.core.Chat;
import com.scd.client.core.Events;
import com.scd.client.core.ScdLog;
import com.scd.client.feature.bazaar.BazaarFeature;
import com.scd.client.feature.carry.CarryService;
import com.scd.client.feature.slayer.BossLocator;
import com.scd.client.feature.slayer.SlayerFeature;
import com.scd.client.feature.slayer.SlayerType;
import com.scd.client.feature.world.GlowRegistry;
import com.scd.client.hypixel.ChatRouter;
import com.scd.client.hypixel.Items;
import com.scd.logic.Text;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Troubleshooting commands under /scd debug, only registered for use while developer mode is on
 * (/scd dev on). Every report goes to chat and to logs/latest.log, so it can be copied either way.
 */
public final class DebugFeature implements Feature {
	private ScdMod mod;
	private volatile boolean glowTest;
	private int menuDumpTicks = -1;

	@Override
	public void init(ScdMod mod) {
		this.mod = mod;
		ScdLog.setDebug(mod.config().general.developerMode);
		GlowRegistry.register(e -> glowTest && e == Minecraft.getInstance().crosshairPickEntity ? 0xFFFF00FF : null);
		mod.bus.subscribe(Events.ScreenOpened.class, e -> {
			if (menuDumpTicks == 0) menuDumpTicks = 40;
		});
		mod.bus.subscribe(Events.ScreenTick.class, e -> {
			if (menuDumpTicks > 0 && --menuDumpTicks == 0) {
				menuDumpTicks = -1;
				dumpMenu();
			}
		});
	}

	private boolean dev() {
		return mod.config().general.developerMode;
	}

	private static void report(String title, List<String> lines) {
		Chat.info(Component.literal(title).withStyle(ChatFormatting.YELLOW));
		ScdLog.info("=== " + title + " ===");
		for (String l : lines) {
			Chat.raw(Component.literal(" " + l).withStyle(ChatFormatting.GRAY));
			ScdLog.info(l);
		}
	}

	@Override
	public void registerCommands(LiteralArgumentBuilder<FabricClientCommandSource> root) {
		root.then(ClientCommands.literal("dev")
				.then(ClientCommands.literal("on").executes(ctx -> setDev(true)))
				.then(ClientCommands.literal("off").executes(ctx -> setDev(false))));

		root.then(ClientCommands.literal("debug").requires(src -> dev())
				.executes(ctx -> {
					overview();
					return 1;
				})
				.then(ClientCommands.literal("sidebar").executes(ctx -> {
					List<String> lines = new ArrayList<>();
					lines.add("title=\"" + mod.game.sidebarTitle() + "\" skyblock=" + mod.game.isOnSkyblock() + " area=" + mod.game.area());
					for (String l : mod.game.sidebar()) lines.add("\"" + l + "\"  " + Text.describeCodepoints(l));
					report("Sidebar (" + mod.game.sidebar().size() + ")", lines);
					return 1;
				}))
				.then(ClientCommands.literal("tablist").executes(ctx -> {
					report("Tab list (" + mod.game.tabList().size() + ")", mod.game.tabList().stream().map(s -> "\"" + s + "\"").toList());
					return 1;
				}))
				.then(ClientCommands.literal("nearby").executes(ctx -> {
					nearby(false);
					return 1;
				}))
				.then(ClientCommands.literal("armorstands").executes(ctx -> {
					nearby(true);
					return 1;
				}))
				.then(ClientCommands.literal("item").executes(ctx -> {
					var hover = mod.feature(BazaarFeature.class).hover();
					String nbt = hover.lastRawNbt();
					if (nbt == null) {
						Chat.info("Hover an item first, then run this again.");
						return 0;
					}
					List<String> chunks = new ArrayList<>();
					chunks.add("name: " + hover.lastName());
					for (int i = 0; i < nbt.length(); i += 200) chunks.add(nbt.substring(i, Math.min(nbt.length(), i + 200)));
					report("Last hovered item", chunks);
					return 1;
				}))
				.then(ClientCommands.literal("menu").executes(ctx -> {
					menuDumpTicks = 0;
					Chat.info("Armed - open a menu; its slots are dumped 2s later.");
					return 1;
				}))
				.then(ClientCommands.literal("capture").executes(ctx -> {
					ChatRouter.captureToLog = !ChatRouter.captureToLog;
					Chat.info("Raw chat capture " + (ChatRouter.captureToLog ? "ON - every incoming line goes to latest.log." : "OFF."));
					return 1;
				}))
				.then(ClientCommands.literal("glow").executes(ctx -> {
					glowTest = !glowTest;
					Chat.info("Glow test " + (glowTest ? "ON - whatever you look at glows magenta." : "OFF."));
					return 1;
				}))
				.then(ClientCommands.literal("slayer").executes(ctx -> {
					report("Slayer tracker", mod.feature(SlayerFeature.class).tracker().describe());
					return 1;
				}))
				.then(ClientCommands.literal("carry").executes(ctx -> {
					CarryService carries = mod.feature(CarryService.class);
					List<String> lines = new ArrayList<>();
					for (var c : carries.active()) lines.add("#" + c.id + " " + c.customer + " " + c.target() + ": " + carries.describe(c));
					report("Active carries", lines.isEmpty() ? List.of("none") : lines);
					return 1;
				})));
	}

	private int setDev(boolean on) {
		mod.config().general.developerMode = on;
		mod.configManager.save();
		ScdLog.setDebug(on);
		Chat.info("Developer mode " + (on ? "ON - /scd debug is available (reopen chat to see it)." : "OFF."));
		return 1;
	}

	private void overview() {
		var loader = FabricLoader.getInstance();
		String mc = loader.getModContainer("minecraft").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
		String fl = loader.getModContainer("fabricloader").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
		List<String> lines = new ArrayList<>();
		lines.add("SCD " + mod.version + " · MC " + mc + " · Loader " + fl + " · Java " + System.getProperty("java.version"));
		lines.add("active=" + mod.active() + " skyblock=" + mod.game.isOnSkyblock() + " area=" + mod.game.area());
		lines.add("Backend " + mod.backend.baseUrl() + ": " + mod.backend.status().message());
		lines.add("Market (scd.wtf): " + (mod.market.hasKey() ? "key set" : "NO KEY") + ", " + mod.market.status().message()
				+ ", " + mod.feature(BazaarFeature.class).prices().size() + " bazaar products cached");
		lines.add(loader.getAllMods().size() + " mods loaded (full list in log)");
		report("SCD debug", lines);
		ScdLog.info("Mods: " + loader.getAllMods().stream().map(c -> c.getMetadata().getId() + "@" + c.getMetadata().getVersion().getFriendlyString())
				.sorted().collect(Collectors.joining(", ")));
	}

	private void nearby(boolean armorStandsOnly) {
		var mcI = Minecraft.getInstance();
		if (mcI.level == null || mcI.player == null) return;
		record Row(double d, String text) {
		}
		List<Row> rows = new ArrayList<>();
		for (Entity e : mcI.level.entitiesForRendering()) {
			double d = e.distanceTo(mcI.player);
			if (d > (armorStandsOnly ? 16 : 48)) continue;
			if (armorStandsOnly) {
				if (!(e instanceof ArmorStand s)) continue;
				rows.add(new Row(d, String.format(Locale.ROOT, "%.1fm \"%s\" invisible=%s marker=%s small=%s", d, BossLocator.name(s), s.isInvisible(), s.isMarker(), s.isSmall())));
			} else {
				if (!(e instanceof LivingEntity)) continue;
				String name = BossLocator.name(e);
				SlayerType t = name != null ? SlayerType.fromBossText(name) : null;
				rows.add(new Row(d, String.format(Locale.ROOT, "%.1fm %s name=%s%s", d, EntityType.getKey(e.getType()),
						name != null ? "\"" + name + "\"" : "-", t != null ? "  <- " + t.displayName() + " boss" : "")));
			}
		}
		rows.sort(Comparator.comparingDouble(Row::d));
		report((armorStandsOnly ? "Armor stands" : "Nearby entities") + " (" + rows.size() + ")", rows.stream().map(Row::text).toList());
	}

	private void dumpMenu() {
		var screen = Minecraft.getInstance().gui.screen();
		if (!(screen instanceof AbstractContainerScreen<?> c)) {
			Chat.info("That screen isn't a container - nothing to dump.");
			return;
		}
		List<String> lines = new ArrayList<>();
		for (var slot : c.getMenu().slots) {
			if (!slot.hasItem()) continue;
			lines.add("[" + slot.index + "] " + Items.name(slot.getItem()) + " | " + String.join(" | ", Items.lore(slot.getItem())));
		}
		ScdLog.info("=== Menu dump \"" + Text.clean(screen.getTitle().getString()) + "\" ===");
		lines.forEach(ScdLog::info);
		Chat.info("Dumped " + lines.size() + " slots of \"" + Text.clean(screen.getTitle().getString()) + "\" to latest.log.");
	}
}
