package com.scd.client.feature.dungeon;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.scd.client.ScdMod;
import com.scd.client.core.Chat;
import com.scd.client.core.ScdLog;
import com.scd.client.feature.world.WorldGizmos;
import com.scd.client.storage.ScdPaths;
import com.scd.logic.dungeon.room.RoomRotation;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Singleplayer workshop for dungeon rooms: {@code /scd rooms build} lays out every captured room
 * (see {@link RoomCapture}) in a grid in the open singleplayer world, {@code /scd rooms tp <name>}
 * jumps to one, and {@code /scd secret add <type>} labels a secret there. Labels are stored in the
 * same room coordinates as everything else, so they show up in real runs too.
 */
final class RoomStudio {
	private static final Path PLACEMENTS = ScdPaths.file("dungeon/studio.json");
	private static final Path LABELS = ScdPaths.file("dungeon/secrets_labeled.json");
	private static final int SLOT = 160, PER_ROW = 10, BLOCKS_PER_TICK = 80_000;
	static final List<String> TYPES = List.of("chest", "item", "bat", "essence", "lever", "redstone", "other");

	/** Where a captured room sits in the singleplayer world: world = captured + (dx, 0, dz). */
	record Placement(String name, int dx, int dz, RoomRotation rotation, int anchorX, int anchorZ, int[] min, int[] size) {
		boolean contains(double x, double z) {
			return x >= min[0] + dx && x < min[0] + dx + size[0] && z >= min[2] + dz && z < min[2] + dz + size[2];
		}

		/** Singleplayer block -> room-relative. */
		BlockPos toRelative(BlockPos sp) {
			int[] r = rotation.toRelative(sp.getX() - dx - anchorX, sp.getZ() - dz - anchorZ);
			return new BlockPos(r[0], sp.getY(), r[1]);
		}

		BlockPos toWorld(BlockPos rel) {
			int[] w = rotation.toWorld(rel.getX(), rel.getZ());
			return new BlockPos(w[0] + anchorX + dx, rel.getY(), w[1] + anchorZ + dz);
		}
	}

	record Label(String type, BlockPos rel) {
	}

	private final ScdMod mod;
	private final DungeonFeature dungeon;
	private final Map<String, Placement> placements = new LinkedHashMap<>();
	private final Map<String, List<Label>> labels = new LinkedHashMap<>();

	/** One marker or node in the route being built, in the order it was made (room coordinates). */
	record Mark(String kind, BlockPos rel) {
	}

	/** Marker blocks: what placing each one means. */
	private static final Map<net.minecraft.world.item.Item, String> MARKERS = new LinkedHashMap<>();

	static {
		MARKERS.put(net.minecraft.world.item.Items.GOLD_BLOCK, "click secret");
		MARKERS.put(net.minecraft.world.item.Items.EMERALD_BLOCK, "item");
		MARKERS.put(net.minecraft.world.item.Items.DIRT, "bat");
		MARKERS.put(net.minecraft.world.item.Items.LAPIS_BLOCK, "etherwarp");
		MARKERS.put(net.minecraft.world.item.Items.GLASS, "ender pearl");
		MARKERS.put(net.minecraft.world.item.Items.IRON_BLOCK, "stonk");
		MARKERS.put(net.minecraft.world.item.Items.TNT, "superboom");
		MARKERS.put(net.minecraft.world.item.Items.DIAMOND_BLOCK, "click");
		MARKERS.put(net.minecraft.world.item.Items.OBSIDIAN, "exit");
	}

	private boolean nodeHooked;
	private Placement sessionRoom;
	private final List<Mark> session = new ArrayList<>();

	/** Rooms waiting to be placed, and the one in progress. */
	private final ArrayDeque<Path> buildQueue = new ArrayDeque<>();
	private CompoundTag building;
	private Placement buildingAt;
	private int buildCursor, built, toBuild;

	RoomStudio(ScdMod mod, DungeonFeature dungeon) {
		this.mod = mod;
		this.dungeon = dungeon;
		load();
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server == Minecraft.getInstance().getSingleplayerServer() && (building != null || !buildQueue.isEmpty())) {
				ScdLog.guard("room build", () -> buildTick(server));
			}
		});
		WorldGizmos.onWorldExtract(pt -> {
			if (mod.config().dungeon.labeledSecrets) drawLabels();
			drawSession();
		});
		// Placing a marker block in a built room adds it to the route being made.
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() && player == Minecraft.getInstance().player && Minecraft.getInstance().hasSingleplayerServer()) {
				String kind = MARKERS.get(player.getItemInHand(hand).getItem());
				if (kind != null) ScdLog.guard("studio marker", () -> onMarker(kind, level, hit));
			}
			return net.minecraft.world.InteractionResult.PASS;
		});
		// Breaking a marker takes it back out.
		net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents.AFTER.register((level, player, pos, state) -> {
			if (sessionRoom != null && session.removeIf(m -> !m.kind().equals("node") && sessionRoom.toWorld(m.rel()).equals(pos))) {
				Chat.info("Marker removed.");
			}
		});
		// N (the route node key) adds a path node here when not recording in a real dungeon. Hooked on
		// the first tick: the route feature is registered after this one.
		mod.bus.subscribe(com.scd.client.core.Events.Tick.class, e -> {
			if (!nodeHooked) {
				nodeHooked = true;
				mod.feature(com.scd.client.feature.dungeon.route.RouteFeature.class).setNodeFallback(this::addNode);
			}
		});
	}

	// ---- storage --------------------------------------------------------------------------------

	private void load() {
		try {
			if (Files.isRegularFile(PLACEMENTS)) {
				for (var el : JsonParser.parseString(Files.readString(PLACEMENTS)).getAsJsonArray()) {
					JsonObject o = el.getAsJsonObject();
					Placement p = new Placement(o.get("name").getAsString(), o.get("dx").getAsInt(), o.get("dz").getAsInt(),
							RoomRotation.valueOf(o.get("rotation").getAsString()), o.get("anchorX").getAsInt(), o.get("anchorZ").getAsInt(),
							ints(o.getAsJsonArray("min")), ints(o.getAsJsonArray("size")));
					placements.put(p.name(), p);
				}
			}
			if (Files.isRegularFile(LABELS)) {
				for (var e : JsonParser.parseString(Files.readString(LABELS)).getAsJsonObject().entrySet()) {
					List<Label> list = new ArrayList<>();
					for (var l : e.getValue().getAsJsonArray()) {
						JsonObject o = l.getAsJsonObject();
						int[] p = ints(o.getAsJsonArray("pos"));
						list.add(new Label(o.get("type").getAsString(), new BlockPos(p[0], p[1], p[2])));
					}
					labels.put(e.getKey(), list);
				}
			}
		} catch (Exception e) {
			ScdLog.warn("Room studio files are invalid", e);
		}
	}

	private static int[] ints(JsonArray a) {
		int[] out = new int[a.size()];
		for (int i = 0; i < out.length; i++) out[i] = a.get(i).getAsInt();
		return out;
	}

	private static JsonArray arr(int... v) {
		JsonArray a = new JsonArray();
		for (int i : v) a.add(i);
		return a;
	}

	private void savePlacements() {
		JsonArray out = new JsonArray();
		for (Placement p : placements.values()) {
			JsonObject o = new JsonObject();
			o.addProperty("name", p.name());
			o.addProperty("dx", p.dx());
			o.addProperty("dz", p.dz());
			o.addProperty("rotation", p.rotation().name());
			o.addProperty("anchorX", p.anchorX());
			o.addProperty("anchorZ", p.anchorZ());
			o.add("min", arr(p.min()));
			o.add("size", arr(p.size()));
			out.add(o);
		}
		write(PLACEMENTS, out);
	}

	private void saveLabels() {
		JsonObject out = new JsonObject();
		labels.forEach((room, list) -> {
			JsonArray a = new JsonArray();
			for (Label l : list) {
				JsonObject o = new JsonObject();
				o.addProperty("type", l.type());
				o.add("pos", arr(l.rel().getX(), l.rel().getY(), l.rel().getZ()));
				a.add(o);
			}
			out.add(room, a);
		});
		write(LABELS, out);
	}

	private static void write(Path file, com.google.gson.JsonElement json) {
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(json));
		} catch (Exception e) {
			ScdLog.warn("Could not save " + file.getFileName(), e);
		}
	}

	/** Every known secret of a room: your labels, then any other secret found in its routes (room coordinates). */
	List<Label> waypoints(String room) {
		List<Label> out = new ArrayList<>(labelsFor(room));
		var routes = mod.feature(com.scd.client.feature.dungeon.route.RouteFeature.class).library()
				.routesFor(room, mod.config().dungeon.disabledRoutePacks);
		for (var route : routes) {
			for (var step : route.steps()) {
				if (step.secret == null) continue;
				String type = switch (step.secretType) {
					case ITEM -> "item";
					case BAT -> "bat";
					case INTERACT -> "chest";
					default -> null;
				};
				if (type == null) continue;
				BlockPos rel = new BlockPos(step.secret[0], step.secret[1], step.secret[2]);
				if (out.stream().noneMatch(l -> l.rel().equals(rel))) out.add(new Label(type, rel));
			}
		}
		return out;
	}

	/** Secrets labelled for a room (room coordinates). */
	List<Label> labelsFor(String room) {
		return labels.getOrDefault(room, List.of());
	}

	// ---- building -------------------------------------------------------------------------------

	private int build() {
		var mc = Minecraft.getInstance();
		if (!mc.hasSingleplayerServer()) return fail("Open a singleplayer world first (a flat world works best).");
		if (building != null || !buildQueue.isEmpty()) return fail("Already building (" + built + "/" + toBuild + ").");
		List<Path> files = new ArrayList<>();
		try (var list = Files.list(RoomCapture.DIR)) {
			list.filter(f -> f.toString().endsWith(".nbt")).sorted().forEach(files::add);
		} catch (Exception e) {
			return fail("No captured rooms yet: play some dungeons first.");
		}
		if (files.isEmpty()) return fail("No captured rooms yet: play some dungeons first.");
		placements.clear();
		buildQueue.addAll(files);
		built = 0;
		toBuild = files.size();
		Chat.info("Building " + toBuild + " rooms in a grid (" + SLOT + " blocks apart). This takes a little while.");
		return 1;
	}

	/** Places up to {@link #BLOCKS_PER_TICK} blocks per server tick, room after room. */
	private void buildTick(net.minecraft.server.MinecraftServer server) {
		var level = server.overworld();
		if (building == null) {
			Path next = buildQueue.poll();
			if (next == null) return;
			try {
				building = NbtIo.readCompressed(next, NbtAccounter.unlimitedHeap());
			} catch (Exception e) {
				ScdLog.warn("Captured room " + next.getFileName() + " is unreadable", e);
				return;
			}
			int slot = placements.size();
			int[] min = building.getIntArray("min").orElseThrow(), size = building.getIntArray("size").orElseThrow();
			int slotX = (slot % PER_ROW) * SLOT, slotZ = (slot / PER_ROW) * SLOT;
			buildingAt = new Placement(building.getStringOr("name", "?"), slotX - min[0], slotZ - min[2],
					RoomRotation.valueOf(building.getStringOr("rotation", "NORTH")), building.getIntOr("anchorX", 0), building.getIntOr("anchorZ", 0), min, size);
			buildCursor = 0;
		}
		var blockLookup = server.registryAccess().lookupOrThrow(Registries.BLOCK);
		var paletteTag = building.getListOrEmpty("palette");
		List<BlockState> palette = new ArrayList<>();
		for (int i = 0; i < paletteTag.size(); i++) palette.add(NbtUtils.readBlockState(blockLookup, paletteTag.getCompoundOrEmpty(i)));
		int[] blocks = building.getIntArray("blocks").orElseThrow();
		int[] min = buildingAt.min(), size = buildingAt.size();
		int end = Math.min(blocks.length, buildCursor + BLOCKS_PER_TICK);
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int i = buildCursor; i < end; i++) {
			if (blocks[i] == 0) continue;
			int x = i % size[0], z = (i / size[0]) % size[2], y = i / (size[0] * size[2]);
			level.setBlock(p.set(min[0] + x + buildingAt.dx(), min[1] + y, min[2] + z + buildingAt.dz()), palette.get(blocks[i] - 1), 2 | 16);
		}
		buildCursor = end;
		if (end >= blocks.length) {
			placements.put(buildingAt.name(), buildingAt);
			building = null;
			built++;
			if (built == toBuild || built % 10 == 0) {
				int done = built;
				Minecraft.getInstance().execute(() -> Chat.info("Built " + done + "/" + toBuild + " rooms."));
			}
			if (built == toBuild) Minecraft.getInstance().execute(this::savePlacements);
		}
	}

	private int teleport(String name) {
		var mc = Minecraft.getInstance();
		var server = mc.getSingleplayerServer();
		if (server == null || mc.player == null) return fail("Only in the singleplayer world with the rooms.");
		Placement p = find(name);
		if (p == null) return fail("No built room called \"" + name + "\". /scd rooms list");
		int cx = p.min()[0] + p.dx() + p.size()[0] / 2, cz = p.min()[2] + p.dz() + p.size()[2] / 2;
		var uuid = mc.player.getUUID();
		server.execute(() -> {
			var level = server.overworld();
			// First spot above the floor with room to stand, scanning up from the bottom.
			int y = 70;
			for (int yy = 1; yy < 200; yy++) {
				BlockPos b = new BlockPos(cx, yy, cz);
				if (!level.getBlockState(b).isAir() && level.getBlockState(b.above()).isAir() && level.getBlockState(b.above(2)).isAir()) {
					y = yy + 1;
					break;
				}
			}
			var sp = server.getPlayerList().getPlayer(uuid);
			if (sp != null) sp.connection.teleport(cx + 0.5, y, cz + 0.5, sp.getYRot(), sp.getXRot());
		});
		Chat.info("Teleported to " + p.name() + ".");
		return 1;
	}

	private Placement find(String name) {
		Placement exact = placements.get(name);
		if (exact != null) return exact;
		for (Placement p : placements.values()) if (p.name().equalsIgnoreCase(name)) return p;
		for (Placement p : placements.values()) if (p.name().toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT))) return p;
		return null;
	}

	private Placement here() {
		var mc = Minecraft.getInstance();
		if (mc.player == null || !mc.hasSingleplayerServer()) return null;
		for (Placement p : placements.values()) if (p.contains(mc.player.getX(), mc.player.getZ())) return p;
		return null;
	}

	// ---- labelling ------------------------------------------------------------------------------

	private int addLabel(String type) {
		var mc = Minecraft.getInstance();
		Placement p = here();
		if (p == null || mc.player == null) return fail("Stand inside a built room in the singleplayer world.");
		BlockPos target;
		if (type.equals("item") || type.equals("bat")) {
			target = mc.player.blockPosition();
		} else {
			HitResult hit = mc.hitResult;
			if (!(hit instanceof BlockHitResult bh) || hit.getType() != HitResult.Type.BLOCK) return fail("Look at the " + type + " block.");
			target = bh.getBlockPos();
		}
		BlockPos rel = p.toRelative(target);
		List<Label> list = labels.computeIfAbsent(p.name(), k -> new ArrayList<>());
		list.removeIf(l -> l.rel().equals(rel));
		list.add(new Label(type, rel));
		saveLabels();
		var info = dungeon.rooms().database().byName(p.name());
		Chat.success("Labelled " + type + " in " + p.name() + " (" + list.size() + (info != null && info.secrets() > 0 ? "/" + info.secrets() : "") + ").");
		return 1;
	}

	private int undoLabel() {
		Placement p = here();
		if (p == null) return fail("Stand inside a built room in the singleplayer world.");
		List<Label> list = labels.get(p.name());
		if (list == null || list.isEmpty()) return fail("No labels in " + p.name() + ".");
		Label l = list.removeLast();
		saveLabels();
		Chat.info("Removed " + l.type() + " from " + p.name() + ".");
		return 1;
	}

	private static int color(String type) {
		return switch (type) {
			case "chest", "essence", "lever", "redstone", "click", "click secret" -> 0xFF5555FF;
			case "item", "bat" -> 0xFF55FF55;
			case "etherwarp" -> 0xFFAA00AA;
			case "ender pearl" -> 0xFF55FFFF;
			case "stonk" -> 0xFFFFFF55;
			case "superboom", "exit" -> 0xFFFF5555;
			default -> 0xFFFFFFFF;
		};
	}

	private static String title(String type) {
		return switch (type) {
			case "essence" -> "Wither essence";
			case "redstone" -> "Redstone key";
			case "click secret" -> "Click secret";
			case "ender pearl" -> "Ender pearl";
			default -> Character.toUpperCase(type.charAt(0)) + type.substring(1);
		};
	}

	/** Labels in the singleplayer copies nearby, and in the room you're in during a real run. */
	private void drawLabels() {
		var mc = Minecraft.getInstance();
		if (mc.player == null) return;
		if (mc.hasSingleplayerServer()) {
			for (Placement p : placements.values()) {
				if (!p.contains(mc.player.getX(), mc.player.getZ())) continue;
				for (Label l : labelsFor(p.name())) mark(p.toWorld(l.rel()), l.type());
			}
		} else if (dungeon.state().inDungeon()) {
			MappedRoom r = dungeon.rooms().current();
			if (r == null || r.name() == null || r.anchor() == null) return;
			for (Label l : waypoints(r.name())) mark(r.toWorld(l.rel()), l.type());
		}
	}

	private static void mark(BlockPos pos, String type) {
		if (type.equals("chest")) {
			// Route secrets only say "interact": name it after the block that's there.
			var level = Minecraft.getInstance().level;
			var b = level != null ? level.getBlockState(pos).getBlock() : null;
			if (b == net.minecraft.world.level.block.Blocks.LEVER) type = "lever";
			else if (b instanceof net.minecraft.world.level.block.AbstractSkullBlock) type = "essence";
			else if (b == net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK) type = "redstone";
		}
		WorldGizmos.block(pos, color(type), true);
		WorldGizmos.label(Vec3.atCenterOf(pos).add(0, 1.0, 0), title(type), color(type), true);
	}

	// ---- route making with marker blocks ---------------------------------------------------------

	private boolean inSession(Placement p) {
		if (p == null) return false;
		if (sessionRoom != null && !sessionRoom.name().equals(p.name()) && !session.isEmpty()) {
			Chat.error("You have an unsaved route in " + sessionRoom.name() + ": /scd studio save or /scd studio clear.");
			return false;
		}
		sessionRoom = p;
		return true;
	}

	/** N: a node on the block you stand in. Returns the node number, 0 when not in a built room. */
	private int addNode(Vec3 feet) {
		Placement p = here();
		if (!inSession(p)) return 0;
		session.add(new Mark("node", p.toRelative(BlockPos.containing(feet))));
		int n = 0;
		for (Mark m : session) if (m.kind().equals("node")) n++;
		return n;
	}

	private static boolean interactable(net.minecraft.world.level.block.Block b) {
		return b == net.minecraft.world.level.block.Blocks.CHEST || b == net.minecraft.world.level.block.Blocks.TRAPPED_CHEST
				|| b == net.minecraft.world.level.block.Blocks.LEVER || b instanceof net.minecraft.world.level.block.AbstractSkullBlock
				|| b == net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK || b instanceof net.minecraft.world.level.block.DoorBlock
				|| b instanceof net.minecraft.world.level.block.ButtonBlock;
	}

	private void onMarker(String kind, net.minecraft.world.level.Level level, BlockHitResult hit) {
		Placement p = here();
		if (!inSession(p)) return;
		BlockPos clicked = hit.getBlockPos();
		BlockPos placed = level.getBlockState(clicked).canBeReplaced() ? clicked : clicked.relative(hit.getDirection());
		BlockPos target = placed;
		String type = kind;
		switch (kind) {
			case "stonk" -> target = clicked; // the wall block the marker was put against
			case "click secret", "click" -> {
				// The chest / lever / skull the marker was put on or against.
				BlockPos found = interactable(level.getBlockState(clicked).getBlock()) ? clicked : null;
				for (var d : net.minecraft.core.Direction.values()) {
					if (found == null && interactable(level.getBlockState(placed.relative(d)).getBlock())) found = placed.relative(d);
				}
				target = found != null ? found : clicked;
				if (kind.equals("click secret")) {
					var b = level.getBlockState(target).getBlock();
					type = b == net.minecraft.world.level.block.Blocks.LEVER ? "lever"
							: b instanceof net.minecraft.world.level.block.AbstractSkullBlock ? "essence"
							: b == net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK ? "redstone" : "chest";
				}
			}
			default -> {
			}
		}
		session.add(new Mark(type, p.toRelative(target)));
		Minecraft.getInstance().gui.hud.setOverlayMessage(net.minecraft.network.chat.Component.literal(title(type) + " marked")
				.withStyle(net.minecraft.ChatFormatting.AQUA), false);
	}

	/** The route being made: nodes joined by a line, every marker labelled. */
	private void drawSession() {
		if (sessionRoom == null || session.isEmpty()) return;
		Vec3 last = null;
		for (Mark m : session) {
			BlockPos w = sessionRoom.toWorld(m.rel());
			if (m.kind().equals("node")) {
				Vec3 v = new Vec3(w.getX() + 0.5, w.getY() + 0.1, w.getZ() + 0.5);
				WorldGizmos.block(w.below(), 0x9060A5FA, true);
				if (last != null) WorldGizmos.line(last, v, 0xFF4ADE80, true);
				last = v;
			} else {
				WorldGizmos.label(Vec3.atCenterOf(w).add(0, 1.0, 0), title(m.kind()), color(m.kind()), true);
				if (m.kind().equals("etherwarp") || m.kind().equals("ender pearl") || m.kind().equals("item") || m.kind().equals("bat") || m.kind().equals("exit")) {
					Vec3 v = new Vec3(w.getX() + 0.5, w.getY() + 1.1, w.getZ() + 0.5);
					if (last != null) WorldGizmos.line(last, v, 0xFF4ADE80, true);
					last = v;
				}
			}
		}
	}

	/** Turns the markers and nodes, in order, into route steps: each secret (or exit) closes a step. */
	private List<com.scd.logic.dungeon.route.RouteStep> toSteps() {
		List<com.scd.logic.dungeon.route.RouteStep> steps = new ArrayList<>();
		var step = new com.scd.logic.dungeon.route.RouteStep();
		step.manual = true;
		int[] lastLoc = null;
		for (Mark m : session) {
			int[] r = {m.rel().getX(), m.rel().getY(), m.rel().getZ()};
			int[] above = {r[0], r[1] + 1, r[2]};
			switch (m.kind()) {
				case "node" -> {
					step.locations.add(r);
					lastLoc = r;
				}
				case "etherwarp" -> {
					step.etherwarps.add(r);
					step.locations.add(above);
					lastLoc = above;
				}
				case "ender pearl" -> {
					step.pearlLandings.add(r);
					step.locations.add(above);
					lastLoc = above;
				}
				case "stonk" -> step.mines.add(r);
				case "superboom" -> step.tnts.add(r);
				case "click" -> step.interacts.add(r);
				default -> {
					var type = switch (m.kind()) {
						case "item" -> com.scd.logic.dungeon.route.RouteStep.SecretType.ITEM;
						case "bat" -> com.scd.logic.dungeon.route.RouteStep.SecretType.BAT;
						case "exit" -> com.scd.logic.dungeon.route.RouteStep.SecretType.EXIT_ROUTE;
						default -> com.scd.logic.dungeon.route.RouteStep.SecretType.INTERACT;
					};
					step.secretType = type;
					step.secret = r;
					steps.add(step);
					step = new com.scd.logic.dungeon.route.RouteStep();
					step.manual = true;
					// Each step starts where the last one ended, so the line stays joined.
					if (lastLoc != null) step.locations.add(lastLoc);
				}
			}
		}
		if (step.locations.size() > 1 || !step.mines.isEmpty() || !step.etherwarps.isEmpty()) {
			step.secretType = com.scd.logic.dungeon.route.RouteStep.SecretType.EXIT_ROUTE;
			step.secret = step.locations.isEmpty() ? lastLoc : step.locations.getLast();
			if (step.secret != null) steps.add(step);
		}
		return steps;
	}

	private int saveSession() {
		if (sessionRoom == null || session.isEmpty()) return fail("Nothing marked yet: place marker blocks and press N for nodes.");
		var steps = toSteps();
		if (steps.isEmpty()) return fail("Mark at least one secret or exit.");
		var routes = mod.feature(com.scd.client.feature.dungeon.route.RouteFeature.class).library();
		routes.mine().add(sessionRoom.name(), new ArrayList<>(steps));
		try {
			routes.saveMine();
		} catch (Exception e) {
			return fail("Could not save: " + e.getMessage());
		}
		// The secrets double as labels for the room.
		List<Label> list = labels.computeIfAbsent(sessionRoom.name(), k -> new ArrayList<>());
		for (Mark m : session) {
			if (List.of("chest", "lever", "essence", "redstone", "item", "bat").contains(m.kind())) {
				list.removeIf(l -> l.rel().equals(m.rel()));
				list.add(new Label(m.kind(), m.rel()));
			}
		}
		saveLabels();
		Chat.success("Saved a " + steps.size() + "-step route for " + sessionRoom.name() + ".");
		session.clear();
		sessionRoom = null;
		return 1;
	}

	/** Gives the marker blocks (singleplayer), named after what they mark. */
	private int kit() {
		var mc = Minecraft.getInstance();
		var server = mc.getSingleplayerServer();
		if (server == null || mc.player == null) return fail("Only in singleplayer.");
		var uuid = mc.player.getUUID();
		server.execute(() -> {
			var sp = server.getPlayerList().getPlayer(uuid);
			if (sp == null) return;
			MARKERS.forEach((item, kind) -> {
				var stack = new net.minecraft.world.item.ItemStack(item, 64);
				stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal(title(kind))
						.withStyle(s -> s.withItalic(false)));
				sp.getInventory().add(stack);
			});
		});
		Chat.info("Markers: gold = click secret, emerald = item, dirt = bat, lapis = etherwarp, glass = ender pearl, iron = stonk, TNT = superboom, diamond = click, obsidian = exit. N = path node.");
		return 1;
	}

	LiteralArgumentBuilder<FabricClientCommandSource> studioCommand() {
		return ClientCommands.literal("studio")
				.executes(ctx -> kit())
				.then(ClientCommands.literal("kit").executes(ctx -> kit()))
				.then(ClientCommands.literal("save").executes(ctx -> saveSession()))
				.then(ClientCommands.literal("undo").executes(ctx -> {
					if (session.isEmpty()) return fail("Nothing to undo.");
					Mark m = session.removeLast();
					Chat.info("Removed " + m.kind() + ".");
					return 1;
				}))
				.then(ClientCommands.literal("clear").executes(ctx -> {
					session.clear();
					sessionRoom = null;
					Chat.info("Cleared.");
					return 1;
				}));
	}

	// ---- commands -------------------------------------------------------------------------------

	LiteralArgumentBuilder<FabricClientCommandSource> roomsCommand(RoomCapture capture) {
		return ClientCommands.literal("rooms")
				.executes(ctx -> {
					int total = dungeon.rooms().database().size();
					Chat.info("Captured " + capture.count() + " of " + total + " rooms. In singleplayer: /scd rooms build, /scd rooms tp <name>, /scd secret add <type>.");
					return 1;
				})
				.then(ClientCommands.literal("build").executes(ctx -> build()))
				.then(ClientCommands.literal("list").executes(ctx -> {
					if (placements.isEmpty()) return fail("Nothing built yet: /scd rooms build in a singleplayer world.");
					List<String> names = new ArrayList<>(placements.keySet());
					Chat.info(names.size() + " rooms: " + String.join(", ", names));
					return 1;
				}))
				.then(ClientCommands.literal("missing").executes(ctx -> {
					List<String> missing = new ArrayList<>();
					for (var info : dungeon.rooms().database().rooms()) if (!capture.has(info.name())) missing.add(info.name());
					missing.sort(null);
					Chat.info(missing.size() + " rooms not captured yet: " + String.join(", ", missing));
					return 1;
				}))
				.then(ClientCommands.literal("recapture").executes(ctx -> {
					MappedRoom r = dungeon.rooms().current();
					if (r == null || r.name() == null) return fail("Stand in an identified room in a dungeon.");
					capture.recapture(r);
					Chat.info("Capturing " + r.name() + " again.");
					return 1;
				}))
				.then(ClientCommands.literal("tp").then(ClientCommands.argument("name", StringArgumentType.greedyString())
						.suggests((ctx, b) -> {
							for (String n : placements.keySet()) if (n.toLowerCase(Locale.ROOT).startsWith(b.getRemainingLowerCase())) b.suggest(n);
							return b.buildFuture();
						})
						.executes(ctx -> teleport(StringArgumentType.getString(ctx, "name")))));
	}

	LiteralArgumentBuilder<FabricClientCommandSource> secretCommand() {
		var add = ClientCommands.literal("add");
		for (String t : TYPES) add.then(ClientCommands.literal(t).executes(ctx -> addLabel(t)));
		return ClientCommands.literal("secret")
				.then(add)
				.then(ClientCommands.literal("undo").executes(ctx -> undoLabel()))
				.then(ClientCommands.literal("list").executes(ctx -> {
					Placement p = here();
					String room = p != null ? p.name() : dungeon.rooms().current() != null ? dungeon.rooms().current().name() : null;
					if (room == null) return fail("Stand in a room.");
					List<String> parts = new ArrayList<>();
					for (Label l : labelsFor(room)) parts.add(l.type() + " " + l.rel().getX() + "," + l.rel().getY() + "," + l.rel().getZ());
					Chat.info(room + ": " + (parts.isEmpty() ? "no labels" : String.join(" · ", parts)));
					return 1;
				}));
	}

	private static int fail(String msg) {
		Chat.error(msg);
		return 0;
	}
}
