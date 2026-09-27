package com.scd.client.feature.dungeon.route;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.scd.client.Feature;
import com.scd.client.ScdMod;
import com.scd.client.config.ScdConfig;
import com.scd.client.core.Chat;
import com.scd.client.core.Events;
import com.scd.client.core.ScdLog;
import com.scd.client.feature.dungeon.DungeonEvents;
import com.scd.client.feature.dungeon.DungeonFeature;
import com.scd.client.feature.dungeon.MappedRoom;
import com.scd.logic.dungeon.route.RouteStep;
import com.scd.logic.dungeon.route.ShareCode;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Secret routes: plays routes for the room you're in, records new ones as you play, and moves them
 * around as share codes or SecretRoutes-compatible pack files.
 */
public final class RouteFeature implements Feature {
	private ScdMod mod;
	private DungeonFeature dungeon;
	private RouteLibrary library;
	private final RouteRunner runner = new RouteRunner();
	private final RouteRecorder recorder = new RouteRecorder();
	private KeyMapping nextKey, backKey;

	@Override
	public void init(ScdMod mod) {
		this.mod = mod;
		this.dungeon = mod.feature(DungeonFeature.class);
		this.library = new RouteLibrary();

		mod.bus.subscribe(DungeonEvents.RoomEntered.class, e -> load(e.room()));
		mod.bus.subscribe(Events.Tick.class, e -> tick());
		mod.bus.subscribe(Events.WorldChanged.class, e -> {
			runner.clear();
			if (recorder.active()) {
				recorder.cancel();
				Chat.info("Route recording discarded (left the world).");
			}
		});
		mod.bus.subscribe(Events.ItemPickedUp.class, e -> {
			runner.onItemPickup(e.pos());
			var p = Minecraft.getInstance().player;
			if (p != null) recorder.onItemPickup(e.pos(), p.position());
		});
		mod.bus.subscribe(Events.EntityDied.class, e -> {
			if (!(e.entity() instanceof Bat bat)) return;
			runner.onBatDeath(bat.position());
			var p = Minecraft.getInstance().player;
			if (p != null) recorder.onBatDeath(bat.position(), p.position());
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() && player == Minecraft.getInstance().player) {
				ScdLog.guard("route interact", () -> {
					runner.onInteract(hit.getBlockPos());
					boolean tnt = player.getItemInHand(hand).is(Items.TNT);
					recorder.onInteract(hit.getBlockPos(), level.getBlockState(hit.getBlockPos()).getBlock(), tnt, player.position());
				});
			}
			return InteractionResult.PASS;
		});
		ClientPlayerBlockBreakEvents.AFTER.register((level, player, pos, state) -> recorder.onBlockBroken(pos));
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (level.isClientSide() && player.getItemInHand(hand).is(Items.ENDER_PEARL)) {
				recorder.onPearl(player.position(), player.getYRot(), player.getXRot());
			}
			return InteractionResult.PASS;
		});

		var category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("scd", "routes"));
		nextKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.scd.route_next", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));
		backKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.scd.route_back", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));

		mod.huds.add(new RouteHud(mod::config, this));
		// Gizmos last one frame; a HUD layer runs every frame.
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("scd", "route_render"), (g, delta) -> {
			ScdConfig.Dungeon c = mod.config().dungeon;
			if (c.routes) ScdLog.guard("route render", () -> runner.render(c.routesThroughWalls, c.routesShowNext));
		});
	}

	private ScdConfig.Dungeon config() {
		return mod.config().dungeon;
	}

	private void load(MappedRoom room) {
		if (room == null || room.name() == null) {
			runner.clear();
			return;
		}
		if (room == runner.room()) return;
		runner.set(room, library.routesFor(room.name(), config().disabledRoutePacks));
	}

	private void tick() {
		var mc = Minecraft.getInstance();
		if (mc.player == null) return;
		MappedRoom current = dungeon.rooms().current();
		// Left the room grid (boss, next floor, hub): nothing to play.
		if (current == null && runner.room() != null) runner.clear();
		// Entered before the room was identified/anchored: pick the route up once it is.
		if (current != null && current != runner.room() && current.name() != null) load(current);
		runner.tick(mc.player.position());
		recorder.tick(mc.player.position());
		while (nextKey.consumeClick()) runner.next();
		while (backKey.consumeClick()) runner.previous();
	}

	RouteRunner runner() {
		return runner;
	}

	RouteRecorder recorder() {
		return recorder;
	}

	public boolean recorderActive() {
		return recorder.active();
	}

	/** Current playback step index (steps done), -1 when no route plays. */
	public int playbackIndex() {
		return runner.active() ? runner.index() : -1;
	}

	public RouteLibrary library() {
		return library;
	}

	// --- commands --------------------------------------------------------------------------

	@Override
	public void registerCommands(LiteralArgumentBuilder<FabricClientCommandSource> root) {
		root.then(ClientCommands.literal("route")
				.executes(ctx -> status())
				.then(ClientCommands.literal("next").executes(ctx -> step(1)))
				.then(ClientCommands.literal("back").executes(ctx -> step(-1)))
				.then(ClientCommands.literal("reset").executes(ctx -> step(0)))
				.then(ClientCommands.literal("alt").executes(ctx -> {
					runner.cycleRoute();
					return status();
				}))
				.then(ClientCommands.literal("record").executes(ctx -> startRecording())
						.then(ClientCommands.literal("stop").executes(ctx -> stopRecording()))
						.then(ClientCommands.literal("cancel").executes(ctx -> {
							recorder.cancel();
							Chat.info("Route recording cancelled.");
							return 1;
						})))
				.then(ClientCommands.literal("mark").executes(ctx -> {
					var p = Minecraft.getInstance().player;
					if (!recorder.active() || p == null) return fail("Not recording.");
					recorder.markExit(p.position());
					Chat.info("Marked waypoint " + recorder.steps().size() + ".");
					return 1;
				}))
				.then(ClientCommands.literal("undo").executes(ctx -> {
					if (!recorder.active()) return fail("Not recording.");
					Chat.info(recorder.undo() ? "Removed the last step." : "No finished step to remove.");
					return 1;
				}))
				.then(ClientCommands.literal("share").executes(ctx -> share()))
				.then(ClientCommands.literal("import")
						.then(ClientCommands.argument("code", StringArgumentType.greedyString())
								.executes(ctx -> importCode(StringArgumentType.getString(ctx, "code")))))
				.then(ClientCommands.literal("delete").executes(ctx -> deleteMine()))
				.then(ClientCommands.literal("reload").executes(ctx -> {
					library.reload();
					runner.clear();
					Chat.info("Route packs reloaded: " + library.packs().size() + " packs + " + library.mine().rooms.size() + " of your routes.");
					return 1;
				})));
	}

	private int fail(String msg) {
		Chat.error(msg);
		return 0;
	}

	private int status() {
		MappedRoom room = dungeon.rooms().current();
		if (recorder.active()) {
			Chat.info("Recording " + recorder.room().label() + ": " + recorder.steps().size() + " steps done, "
					+ recorder.pendingPoints() + " points in the current one.");
			return 1;
		}
		if (room == null) return fail("Not in a mapped dungeon room.");
		if (!runner.active()) {
			Chat.info(room.label() + ": no route" + (room.anchor() == null ? " (room not anchored yet)" : "") + ".");
			return 1;
		}
		RouteStep s = runner.current();
		Chat.info(room.label() + ": step " + Math.min(runner.index() + 1, runner.steps().size()) + "/" + runner.steps().size()
				+ (s != null ? " (" + s.secretType.key + ")" : " (done)")
				+ (runner.routeCount() > 1 ? " · route " + (runner.routeIndex() + 1) + "/" + runner.routeCount() : ""));
		return 1;
	}

	private int step(int dir) {
		if (dir > 0) runner.next();
		else if (dir < 0) runner.previous();
		else runner.reset();
		return status();
	}

	private int startRecording() {
		var p = Minecraft.getInstance().player;
		MappedRoom room = dungeon.rooms().current();
		if (p == null || room == null) return fail("Stand in a dungeon room first.");
		if (room.name() == null) return fail("This room isn't identified - name it first with /scd dungeon room name \"<name>\".");
		if (room.anchor() == null) return fail("This room isn't anchored yet (its blue terracotta wasn't found). Try again in a moment.");
		recorder.start(room, p.position());
		runner.clear();
		Chat.info(Component.literal("Recording a route for " + room.label() + ". Open secrets as usual; ").withStyle(ChatFormatting.GREEN)
				.append(Chat.button("mark", "/scd route mark", true, "Mark a waypoint/exit where you stand"))
				.append(Component.literal(" "))
				.append(Chat.button("undo", "/scd route undo", true, "Drop the last step"))
				.append(Component.literal(" "))
				.append(Chat.button("stop", "/scd route record stop", true, "Save the route")));
		return 1;
	}

	private int stopRecording() {
		if (!recorder.active()) return fail("Not recording.");
		MappedRoom room = recorder.room();
		List<RouteStep> steps = recorder.stop();
		if (steps.isEmpty()) return fail("Nothing recorded.");
		library.mine().rooms.put(room.name(), new ArrayList<>(steps));
		try {
			library.saveMine();
		} catch (Exception e) {
			return fail("Could not save: " + e.getMessage());
		}
		runner.set(room, library.routesFor(room.name(), config().disabledRoutePacks));
		Chat.success("Saved a " + steps.size() + "-step route for " + room.label() + " to " + RouteLibrary.MINE + ".");
		return 1;
	}

	private int share() {
		MappedRoom room = runner.room() != null ? runner.room() : dungeon.rooms().current();
		if (room == null || room.name() == null || runner.steps().isEmpty()) return fail("No route here to share.");
		String code = ShareCode.encode(room.name(), runner.steps());
		Minecraft.getInstance().keyboardHandler.setClipboard(code);
		Chat.info("Route code for " + room.label() + " copied to your clipboard (" + code.length() + " chars). Import with /scd route import <code>.");
		return 1;
	}

	private int importCode(String code) {
		ShareCode.Decoded d;
		try {
			d = ShareCode.decode(code);
		} catch (IllegalArgumentException e) {
			return fail(e.getMessage());
		}
		String room = library.canonical(d.room());
		library.mine().rooms.put(room, new ArrayList<>(d.steps()));
		try {
			library.saveMine();
		} catch (Exception e) {
			return fail("Could not save: " + e.getMessage());
		}
		runner.clear();
		Chat.success("Imported a " + d.steps().size() + "-step route for " + room + ".");
		return 1;
	}

	private int deleteMine() {
		MappedRoom room = dungeon.rooms().current();
		if (room == null || room.name() == null) return fail("Stand in the room whose route you want to delete.");
		if (library.mine().rooms.remove(room.name()) == null) return fail("You have no own route for " + room.label() + ".");
		try {
			library.saveMine();
		} catch (Exception e) {
			return fail("Could not save: " + e.getMessage());
		}
		runner.clear();
		Chat.info("Deleted your route for " + room.label() + ".");
		return 1;
	}
}
