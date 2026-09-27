package com.scd.client.feature.dungeon;

import com.scd.client.core.ScdLog;
import com.scd.client.storage.ScdPaths;
import com.scd.logic.dungeon.room.Checkmark;
import com.scd.logic.dungeon.room.DungeonGrid;
import com.scd.logic.dungeon.room.MapLayout;
import com.scd.logic.dungeon.room.RoomCore;
import com.scd.logic.dungeon.room.RoomDatabase;
import com.scd.logic.dungeon.room.RoomInfo;
import com.scd.logic.dungeon.room.RoomKind;
import com.scd.logic.dungeon.room.RoomPlacement;
import com.scd.logic.dungeon.room.RoomRotation;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the room layout of the current Catacombs run.
 *
 * <ol>
 *     <li><b>World scan</b>: the centre column of every loaded tile is hashed ({@link RoomCore}) and
 *     looked up in the room database. Tiles resolving to the same room are one room.</li>
 *     <li><b>Map</b>: the dungeon map item gives room colours, multi-tile groups for rooms that
 *     aren't loaded yet, and checkmarks.</li>
 *     <li><b>Anchor</b>: once a room's footprint is complete its blue-terracotta clay is looked
 *     for at the candidate corners; multi-tile rooms fall back to their footprint.</li>
 * </ol>
 * Everything is polled (every {@link #SCAN_INTERVAL} ticks, ~5k block reads) instead of hooking
 * chunk events: chunks that arrive before the sidebar says "Catacombs" are simply picked up later.
 */
public final class RoomEngine {
	private static final int SCAN_INTERVAL = 10;
	private static final Path USER_ROOMS = ScdPaths.file("dungeon/rooms.json");

	private RoomDatabase db = new RoomDatabase();
	private final Map<Block, String> blockIds = new IdentityHashMap<>();
	private final MappedRoom[] byTile = new MappedRoom[36];
	private final Integer[] cores = new Integer[36];
	private final List<MappedRoom> rooms = new ArrayList<>();
	private final Map<RoomInfo, MappedRoom> byInfo = new HashMap<>();
	private final Set<Integer> reportedUnknown = new HashSet<>();
	private MapLayout.Layout layout;
	/** Teammate markers on the dungeon map, in map pixels {x, z}. */
	private List<int[]> teammateMarks = List.of();
	private MappedRoom current;
	private Level level;
	private int ticks;
	private String floor;

	public RoomEngine() {
		reloadDatabase();
	}

	public void reloadDatabase() {
		RoomDatabase fresh = new RoomDatabase();
		try (var in = RoomEngine.class.getResourceAsStream("/assets/scd/dungeon/rooms.json")) {
			if (in != null) fresh.load(new InputStreamReader(in, StandardCharsets.UTF_8));
		} catch (Exception e) {
			ScdLog.warn("Bundled room database failed to load", e);
		}
		if (Files.isRegularFile(USER_ROOMS)) {
			try (Reader r = Files.newBufferedReader(USER_ROOMS)) {
				int n = fresh.load(r);
				ScdLog.info("Loaded " + n + " custom rooms from " + USER_ROOMS.getFileName());
			} catch (Exception e) {
				ScdLog.warn("Custom room file " + USER_ROOMS + " is invalid", e);
			}
		}
		db = fresh;
		reset();
	}

	public RoomDatabase database() {
		return db;
	}

	public void reset() {
		java.util.Arrays.fill(byTile, null);
		java.util.Arrays.fill(cores, null);
		rooms.clear();
		byInfo.clear();
		layout = null;
		teammateMarks = List.of();
		current = null;
		ticks = 0;
	}

	/** @return true when the current room changed this tick */
	boolean tick(boolean inDungeon, boolean inBoss, String floor) {
		Minecraft mc = Minecraft.getInstance();
		if (!inDungeon || mc.level == null || mc.player == null) {
			if (!rooms.isEmpty() || current != null) reset();
			level = mc.level;
			return false;
		}
		if (mc.level != level) {
			reset();
			level = mc.level;
		}
		this.floor = floor;
		if (++ticks >= SCAN_INTERVAL) {
			ticks = 0;
			scanWorld(mc.level);
			readMap(mc);
			for (MappedRoom r : rooms) if (r.anchor == null) anchor(mc.level, r);
		}
		MappedRoom now = null;
		if (!inBoss) {
			int tx = DungeonGrid.tileOf(mc.player.getBlockX()), tz = DungeonGrid.tileOf(mc.player.getBlockZ());
			if (DungeonGrid.inGrid(tx, tz)) now = byTile[DungeonGrid.index(tx, tz)];
		}
		if (now == current) return false;
		current = now;
		if (now != null) now.visited = true;
		return true;
	}

	private void scanWorld(Level lvl) {
		for (int i = 0; i < 36; i++) {
			MappedRoom r = byTile[i];
			if (r != null && r.info != null) continue;
			int tx = i % 6, tz = i / 6;
			var access = lvl.getChunkSource().getChunk(DungeonGrid.chunkOf(tx), DungeonGrid.chunkOf(tz), ChunkStatus.FULL, false);
			if (!(access instanceof LevelChunk chunk)) continue;
			RoomCore.Result res = core(chunk, 7, 7);
			// Note: some real rooms are solid right up to the scan top (y140), so that is not filtered.
			if (res.empty()) continue;
			cores[i] = res.core();
			RoomInfo info = db.byCore(res.core());
			if (info == null) {
				if (reportedUnknown.add(res.core())) {
					ScdLog.info("[rooms] unknown core " + res.core() + " at tile " + tx + "," + tz + " floor " + floor + " roof y" + res.highestBlock());
				}
				if (r == null) {
					r = new MappedRoom();
					r.tiles.add(new int[]{tx, tz});
					byTile[i] = r;
					rooms.add(r);
				}
				if (r.core == null) r.core = res.core();
				r.highestBlock = res.highestBlock();
				continue;
			}
			MappedRoom room = byInfo.get(info);
			if (room == null) {
				// A map-only placeholder for this tile becomes the identified room.
				room = r != null ? r : new MappedRoom();
				if (r == null) rooms.add(room);
				room.info = info;
				room.core = res.core();
				byInfo.put(info, room);
			} else if (r != null && r != room) {
				// Merge a placeholder into the identified room.
				for (int[] t : r.tiles) if (!room.hasTile(t[0], t[1])) {
					room.tiles.add(t);
					byTile[DungeonGrid.index(t[0], t[1])] = room;
				}
				if (r.checkmark != Checkmark.UNDISCOVERED) room.checkmark = r.checkmark;
				rooms.remove(r);
				if (current == r) current = room;
			}
			room.highestBlock = res.highestBlock();
			if (!room.hasTile(tx, tz)) room.tiles.add(new int[]{tx, tz});
			byTile[i] = room;
		}
	}

	private void readMap(Minecraft mc) {
		ItemStack stack = mc.player.getInventory().getItem(8);
		if (stack.isEmpty() || stack.get(DataComponents.MAP_ID) == null) return;
		MapItemSavedData data = MapItem.getSavedData(stack, mc.level);
		if (data == null) return;
		MapLayout.Layout read = MapLayout.read(data.colors, floor);
		if (read == null) return;
		layout = read;
		// Teammate markers (Hypixel draws you as the frame-type marker; others as player markers).
		List<int[]> marks = new ArrayList<>();
		for (var d : data.getDecorations()) {
			if (d.type().value() == net.minecraft.world.level.saveddata.maps.MapDecorationTypes.FRAME.value()) continue;
			marks.add(new int[]{(d.x() + 128) / 2, (d.y() + 128) / 2});
		}
		teammateMarks = marks;
		for (MapLayout.MapRoom mr : read.rooms()) {
			// Find (or create) the room owning these tiles.
			MappedRoom room = null;
			for (int i : mr.tiles()) if (byTile[i] != null) {
				room = byTile[i];
				break;
			}
			if (room == null) {
				room = new MappedRoom();
				rooms.add(room);
			}
			if (room.info == null) room.kind = mr.kind() == RoomKind.UNKNOWN && room.kind != null ? room.kind : mr.kind();
			for (int i : mr.tiles()) {
				MappedRoom other = byTile[i];
				if (other != null && other != room) {
					// Two identified parts can't be merged; an unknown fragment can.
					if (other.info != null) continue;
					rooms.remove(other);
				}
				byTile[i] = room;
				if (!room.hasTile(i % 6, i / 6)) room.tiles.add(new int[]{i % 6, i / 6});
			}
			if (mr.checkmark() != Checkmark.UNDISCOVERED) room.checkmark = mr.checkmark();
		}
	}

	private void anchor(Level lvl, MappedRoom room) {
		if (room.highestBlock == 0) return;
		// Unknown rooms can still be anchored when they are a single tile and the clay is there.
		boolean oneByOne = room.info != null ? room.info.shape().tiles == 1 : room.tiles.size() == 1;
		if (room.info != null && !room.complete()) return;
		if (room.info == null && !oneByOne) return;
		Block clay = Blocks.DYED_TERRACOTTA.pick(DyeColor.BLUE);
		if ("Fairy".equals(room.name())) {
			// Fairy rooms have no usable clay: Odin pins them to SOUTH.
			int[] t = room.tiles.getFirst();
			room.anchor = RoomPlacement.oneByOne(t[0], t[1], RoomRotation.SOUTH);
			room.anchorSource = "fixed";
			return;
		}
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		// Don't decide while part of the room is unloaded (it would read as air).
		// Tile corners reach into neighbouring chunks, so check every probe spot.
		for (int[] t : room.tiles) {
			for (RoomRotation rot : PROBE_ORDER) {
				if (!lvl.hasChunkAt(p.set(DungeonGrid.centre(t[0]) + rot.dx, room.highestBlock, DungeonGrid.centre(t[1]) + rot.dz))) return;
			}
		}
		// Candidates: blue terracotta at any tile corner, at roof height. Live data: 1x1 rooms have one;
		// multi-tile rooms have a pair at a tile junction, whose corner labels are always clockwise
		// neighbours (e.g. SOUTH then WEST). Taking the first of the pair picks the same physical block
		// in every orientation of the room, so recorded routes line up after rotation.
		List<RoomPlacement.Anchor> found = new ArrayList<>();
		for (int[] t : room.tiles) {
			for (RoomRotation rot : PROBE_ORDER) {
				int x = DungeonGrid.centre(t[0]) + rot.dx, z = DungeonGrid.centre(t[1]) + rot.dz;
				if (lvl.getBlockState(p.set(x, room.highestBlock, z)).getBlock() == clay) found.add(new RoomPlacement.Anchor(rot, x, z));
			}
		}
		RoomPlacement.Anchor pick = RoomPlacement.pickClay(found);
		if (pick != null) {
			room.anchor = pick;
			room.anchorSource = found.size() > 1 ? "block pair" : "block";
			return;
		}
		if (room.info != null && !oneByOne) {
			RoomPlacement.Anchor geo = RoomPlacement.fromGeometry(room.tiles, room.info.shape());
			if (geo != null) {
				room.anchor = geo;
				room.anchorSource = "geometry";
				ScdLog.info("[rooms] " + room.label() + ": no clay at any tile corner (roof y" + room.highestBlock + "), footprint " + geo.rotation()
						+ "; blue terracotta nearby: " + findClay(lvl, room, clay));
			}
		}
	}

	private static final RoomRotation[] PROBE_ORDER = {RoomRotation.NORTH, RoomRotation.SOUTH, RoomRotation.WEST, RoomRotation.EAST};

	/** Diagnostic: blue terracotta anywhere in the room from 30 below to 5 above the roof (first 8). */
	private String findClay(Level lvl, MappedRoom room, Block clay) {
		List<String> found = new ArrayList<>();
		int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		for (int[] t : room.tiles) {
			minX = Math.min(minX, DungeonGrid.centre(t[0]) - 15);
			maxX = Math.max(maxX, DungeonGrid.centre(t[0]) + 15);
			minZ = Math.min(minZ, DungeonGrid.centre(t[1]) - 15);
			maxZ = Math.max(maxZ, DungeonGrid.centre(t[1]) + 15);
		}
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int y = room.highestBlock + 5; y >= room.highestBlock - 30 && found.size() < 8; y--) {
			for (int x = minX; x <= maxX && found.size() < 8; x++) {
				for (int z = minZ; z <= maxZ && found.size() < 8; z++) {
					if (lvl.getBlockState(p.set(x, y, z)).getBlock() == clay) found.add(x + "," + y + "," + z);
				}
			}
		}
		return found.isEmpty() ? "none in the room" : String.join("; ", found);
	}

	/** Odin's column hash, reading chunk sections directly (unloaded sections are air). */
	RoomCore.Result core(LevelChunk chunk, int x, int z) {
		LevelChunkSection[] sections = chunk.getSections();
		return RoomCore.compute(y -> {
			int si = chunk.getSectionIndex(y);
			if (si < 0 || si >= sections.length) return null;
			LevelChunkSection s = sections[si];
			if (s.hasOnlyAir()) return null;
			Block b = s.getBlockState(x, y & 15, z).getBlock();
			return blockIds.computeIfAbsent(b, k -> BuiltInRegistries.BLOCK.getKey(k).toString());
		});
	}

	/** Core of the tile the player stands in, computed now (for naming unknown rooms). */
	Integer coreAt(int tileX, int tileZ) {
		return DungeonGrid.inGrid(tileX, tileZ) ? cores[DungeonGrid.index(tileX, tileZ)] : null;
	}

	public MappedRoom current() {
		return current;
	}

	public List<MappedRoom> rooms() {
		return List.copyOf(rooms);
	}

	public MapLayout.Layout layout() {
		return layout;
	}

	public List<int[]> teammateMarks() {
		return teammateMarks;
	}

	/** Grid tile index (x + z * 6) under a map pixel, or -1. */
	public int tileAtMapPixel(int px, int pz) {
		if (layout == null) return -1;
		int gap = layout.roomSize() + 4;
		int tx = Math.floorDiv(px - layout.startX(), gap), tz = Math.floorDiv(pz - layout.startZ(), gap);
		return DungeonGrid.inGrid(tx, tz) ? DungeonGrid.index(tx, tz) : -1;
	}

	/** The room covering grid tile {@code index}, or null. */
	public MappedRoom roomAtTile(int index) {
		return index >= 0 && index < 36 ? byTile[index] : null;
	}

	public MappedRoom roomAt(BlockPos pos) {
		int tx = DungeonGrid.tileOf(pos.getX()), tz = DungeonGrid.tileOf(pos.getZ());
		return DungeonGrid.inGrid(tx, tz) ? byTile[DungeonGrid.index(tx, tz)] : null;
	}

	public String describe() {
		long known = rooms.stream().filter(r -> r.info != null).count();
		long anchored = rooms.stream().filter(r -> r.anchor != null).count();
		return rooms.isEmpty() ? "idle (" + db.size() + " rooms known)"
				: rooms.size() + " rooms, " + known + " identified, " + anchored + " anchored" + (layout != null ? ", map ok" : ", no map");
	}

	/**
	 * Saves the room the player stands in to the user's room file under {@code name}, with the cores
	 * of all its scanned tiles, then reloads so it is identified from now on.
	 */
	String nameCurrentRoom(String name, int secrets) throws java.io.IOException {
		MappedRoom room = current;
		if (room == null) return "Stand inside the room first.";
		List<Integer> roomCores = new ArrayList<>();
		for (int[] t : room.tiles) {
			Integer c = cores[DungeonGrid.index(t[0], t[1])];
			if (c != null && !roomCores.contains(c)) roomCores.add(c);
		}
		if (roomCores.isEmpty()) return "No core scanned for this room yet - wait a second and retry.";
		int minX = 9, minZ = 9, maxX = -1, maxZ = -1;
		for (int[] t : room.tiles) {
			minX = Math.min(minX, t[0]);
			minZ = Math.min(minZ, t[1]);
			maxX = Math.max(maxX, t[0]);
			maxZ = Math.max(maxZ, t[1]);
		}
		var shape = room.info != null ? room.info.shape() : com.scd.logic.dungeon.room.RoomShape.fromTiles(room.tiles.size(), maxX - minX + 1, maxZ - minZ + 1);
		RoomKind kind = room.kind() != null && room.kind() != RoomKind.UNKNOWN ? room.kind() : RoomKind.NORMAL;

		com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
		if (Files.isRegularFile(USER_ROOMS)) {
			try (Reader r = Files.newBufferedReader(USER_ROOMS)) {
				arr = com.google.gson.JsonParser.parseReader(r).getAsJsonArray();
			}
		}
		com.google.gson.JsonObject entry = null;
		for (var el : arr) if (name.equals(el.getAsJsonObject().get("name").getAsString())) entry = el.getAsJsonObject();
		if (entry == null) {
			entry = new com.google.gson.JsonObject();
			entry.addProperty("name", name);
			entry.add("cores", new com.google.gson.JsonArray());
			arr.add(entry);
		}
		entry.addProperty("type", kind.name());
		entry.addProperty("shape", shape.key);
		if (secrets >= 0) entry.addProperty("maxSecrets", secrets);
		var coreArr = entry.getAsJsonArray("cores");
		for (int c : roomCores) if (!coreArr.contains(new com.google.gson.JsonPrimitive(c))) coreArr.add(c);
		Files.createDirectories(USER_ROOMS.getParent());
		Files.writeString(USER_ROOMS, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(arr));
		reloadDatabase();
		return "Saved '" + name + "' (" + kind.name().toLowerCase(java.util.Locale.ROOT) + ", " + shape.key + ", cores " + roomCores + ") to " + USER_ROOMS.getFileName();
	}
}
