package com.scd.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.scd.client.core.Chat;
import com.scd.client.core.Events;
import com.scd.client.core.ScdLog;
import com.scd.client.feature.accessory.AccessoryFeature;
import com.scd.client.feature.bazaar.BazaarFeature;
import com.scd.client.feature.carry.CarryService;
import com.scd.client.feature.debug.DebugFeature;
import com.scd.client.feature.dungeon.DungeonFeature;
import com.scd.client.feature.mayor.MayorService;
import com.scd.client.feature.slayer.SlayerFeature;
import com.scd.client.hud.HudEditorScreen;
import com.scd.client.screen.MainScreen;
import com.scd.client.storage.StoreRegistry;
import com.scd.client.ui.ScdScreen;
import com.scd.client.ui.Theme;
import com.scd.client.ui.Ui;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Entrypoint: builds the shared services, initializes every feature in dependency order and
 * connects Fabric's events to SCD's own bus. This class only wires things together - behaviour
 * lives in the features.
 */
public final class ScdClient implements ClientModInitializer {
	private ScdMod mod;
	private KeyMapping menuKey;

	@Override
	public void onInitializeClient() {
		String version = FabricLoader.getInstance().getModContainer("scd").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("dev");
		mod = new ScdMod(version);
		Ui.setTheme(Theme.byName(mod.config().general.theme));
		mod.configManager.onChange(() -> mod.backend.setBaseUrl(mod.config().backend.serverUrl));

		// Order matters: later features look earlier ones up in init().
		for (Feature f : List.of(new MayorService(), new BazaarFeature(), new SlayerFeature(), new CarryService(),
				new DungeonFeature(), new AccessoryFeature(), new DebugFeature(), new com.scd.client.feature.debug.SessionRecorder())) {
			mod.add(f);
			ScdLog.guard("init " + f.getClass().getSimpleName(), () -> f.init(mod));
		}
		mod.huds.register();

		ClientTickEvents.END_CLIENT_TICK.register(mc -> ScdLog.guard("tick", this::tick));
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			mod.bus.post(new Events.ScreenOpened(screen));
			ScreenEvents.afterTick(screen).register(s -> mod.bus.post(new Events.ScreenTick(s)));
		});
		StoreRegistry.start();
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> StoreRegistry.flushAll());
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> dispatcher.register(commands()));

		menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.scd.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN,
				KeyMapping.Category.register(Identifier.fromNamespaceAndPath("scd", "main"))));

		ScdLog.info("SCD " + version + " ready: " + mod.features().size() + " features, " + mod.huds.elements().size() + " HUD elements, market key "
				+ (mod.market.hasKey() ? "set" : "missing"));
	}

	private void tick() {
		mod.game.tick(mod.tasks.currentTick());
		mod.tasks.tick();
		mod.bus.post(new Events.Tick(mod.tasks.currentTick()));
		while (menuKey.consumeClick()) ScdScreen.open(new MainScreen(null, mod));
	}

	private LiteralArgumentBuilder<FabricClientCommandSource> commands() {
		var root = ClientCommands.literal("scd")
				.executes(ctx -> {
					ScdScreen.open(new MainScreen(null, mod));
					return 1;
				})
				.then(ClientCommands.literal("hud").executes(ctx -> {
					ScdScreen.open(new HudEditorScreen(null, mod.huds, mod.configManager));
					return 1;
				}))
				.then(ClientCommands.literal("help").executes(ctx -> {
					help();
					return 1;
				}))
				.then(ClientCommands.literal("market").then(ClientCommands.literal("key")
						.then(ClientCommands.argument("key", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(ctx -> {
							mod.config().market.apiKey = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "key").trim();
							mod.configManager.save();
							Chat.success("scd.wtf key saved - prices refresh within a minute.");
							return 1;
						}))))
				.then(ClientCommands.literal("server")
						.then(ClientCommands.argument("url", com.mojang.brigadier.arguments.StringArgumentType.greedyString()).executes(ctx -> {
							String url = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "url");
							String err = mod.backend.setBaseUrl(url);
							if (err != null) {
								Chat.error(err);
								return 0;
							}
							mod.config().backend.serverUrl = mod.backend.baseUrl();
							mod.configManager.save();
							Chat.success("SCD backend set to " + mod.backend.baseUrl());
							return 1;
						})));
		for (Feature f : mod.features()) f.registerCommands(root);
		return root;
	}

	private static void help() {
		Chat.info(Component.literal("Commands").withStyle(ChatFormatting.YELLOW));
		String[][] lines = {
				{"/scd", "settings hub"}, {"/scd hud", "move/resize HUDs"}, {"/scd price <item>", "Bazaar + AH price"},
				{"/scd market key <key>", "set the scd.wtf API key"}, {"/scd server <url>", "SCD backend URL"},
				{"/scd slayer [drops|records|session reset]", "Slayer"},
				{"/scd carry [list|add|done|extend|adjust|remove]", "carries"},
				{"/scd dungeon [score]", "dungeons"}, {"/scd accessories", "accessory bag"},
				{"/scd record start|mark|stop", "debug recording for live test sessions"},
				{"/scd dev on|off", "developer mode (/scd debug ...)"}};
		for (String[] l : lines) {
			Chat.raw(Component.literal(" " + l[0]).withStyle(ChatFormatting.AQUA).append(Component.literal(" - " + l[1]).withStyle(ChatFormatting.GRAY)));
		}
	}
}
