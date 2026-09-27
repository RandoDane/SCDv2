package com.scd.client.feature.dungeon;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.scd.client.ScdMod;
import com.scd.client.core.Events;
import com.scd.client.core.ScdLog;
import com.scd.client.feature.world.WorldGizmos;
import com.scd.logic.dungeon.room.DungeonGrid;
import com.scd.logic.dungeon.TicTacToe;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * More show-only puzzle solvers:
 * <ul>
 *     <li><b>Creeper Beams</b> - lit lantern pairs linked (pair data: Odin, BSD-3; same room frame as SCD).</li>
 *     <li><b>Ice Fill</b> - the path for each of the three floors (floor data: Odin, BSD-3).</li>
 *     <li><b>Ice Path</b> - the silverfish's shortest slide sequence to the exit, found by simulating
 *     slides over the ice around it until it leaves through the gap (no fixed coordinates needed).</li>
 *     <li><b>Teleport Maze</b> - pads are found by scanning; after each teleport, the pads your new
 *     facing points through are kept as candidates (the maze turns you toward the right one).</li>
 *     <li><b>Tic Tac Toe</b> - the board's nine squares are the map frames (played) plus the buttons
 *     (empty) on the wall; best move for O.</li>
 *     <li><b>Boulder</b> - the boulder layout is read on entry and looked up in the solution table
 *     (Odin, BSD-3); the next boulder to click is boxed and ticked off when clicked.</li>
 * </ul>
 */
final class PuzzleSolvers2 {
	private final ScdMod mod;
	private final DungeonFeature dungeon;
	private final List<int[]> beamPairs = new ArrayList<>();
	private final List<List<List<int[]>>> iceIds = new ArrayList<>(), iceEasy = new ArrayList<>();
	/** Lit lantern pairs; {@code pair} picks the colour and stays with the pair as others go out. */
	private record Beam(Vec3 a, Vec3 b, int pair) {
	}

	/** Colour slot per lantern pair (by its two positions), for the whole visit, whichever solver found it. */
	private final Map<String, Integer> beamSlots = new HashMap<>();
	/** Once SCD's own pairing worked in this room it is used for the rest of the visit (no flipping to the data). */
	private boolean ownBeamsWorked;

	private int beamSlot(BlockPos a, BlockPos b) {
		String key = Math.min(a.asLong(), b.asLong()) + ":" + Math.max(a.asLong(), b.asLong());
		return beamSlots.computeIfAbsent(key, k -> beamSlots.size());
	}
	private boolean beamsLogged, iceLogged;
	/** Boulder learning: a click waiting for its "after" snapshot. */
	private String boulderBefore;
	private BlockPos boulderClick;
	private long boulderAfterTick;

	private final List<Beam> beams = new ArrayList<>();
	private final List<Vec3> icePath = new ArrayList<>();
	private final List<Vec3> slidePath = new ArrayList<>();
	private List<BlockPos> tpPads = List.of();
	private Set<BlockPos> tpCandidates = new HashSet<>();
	private final Set<BlockPos> tpVisited = new HashSet<>();
	private Vec3 lastPos;
	private BlockPos tttMove;
	private final Map<String, List<int[]>> boulderSolutions = new HashMap<>();
	/** Remaining boulder clicks: {box, click block}. */
	private final List<BlockPos[]> boulderMoves = new ArrayList<>();
	private boolean boulderRead;
	private MappedRoom solvedFor;

	PuzzleSolvers2(ScdMod mod, DungeonFeature dungeon) {
		this.mod = mod;
		this.dungeon = dungeon;
		load();
		loadBoulder();
		// Clicking the boulder a box points at ticks that step off.
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() && !boulderMoves.isEmpty()) {
				BlockPos clicked = hit.getBlockPos();
				boulderMoves.removeIf(m -> m[1].equals(clicked));
			}
			// Learning how boulders move: the grid before and a second after each click.
			MappedRoom here = dungeon.rooms().current();
			if (level.isClientSide() && here != null && "Boulder".equals(here.name()) && here.anchor() != null) {
				boulderBefore = boulderKey(here, (net.minecraft.client.multiplayer.ClientLevel) level);
				boulderClick = here.toRelative(hit.getBlockPos());
				boulderAfterTick = mod.tasks.currentTick() + 20;
			}
			return net.minecraft.world.InteractionResult.PASS;
		});
		mod.bus.subscribe(DungeonEvents.RoomEntered.class, e -> {
			if (e.room() != solvedFor) resetRoom();
		});
		mod.bus.subscribe(Events.Tick.class, e -> {
			if (mod.config().dungeon.puzzleSolvers && dungeon.state().inDungeon()) ScdLog.guard("puzzles", this::tick);
		});
		WorldGizmos.onWorldExtract(pt -> {
			if (mod.config().dungeon.puzzleSolvers && dungeon.state().inDungeon()) draw();
		});
	}

	private void load() {
		try (var in = PuzzleSolvers2.class.getResourceAsStream("/assets/scd/dungeon/creeper-beams.json")) {
			if (in != null) {
				for (JsonElement el : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray()) {
					JsonArray a = el.getAsJsonArray();
					int[] pair = new int[6];
					for (int i = 0; i < 6; i++) pair[i] = a.get(i).getAsInt();
					beamPairs.add(pair);
				}
			}
		} catch (Exception e) {
			ScdLog.warn("Creeper Beams data failed to load", e);
		}
		try (var in = PuzzleSolvers2.class.getResourceAsStream("/assets/scd/dungeon/ice-fill.json")) {
			if (in != null) {
				JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
				readFloors(o.getAsJsonArray("identifier"), iceIds);
				readFloors(o.getAsJsonArray("easy"), iceEasy);
			}
		} catch (Exception e) {
			ScdLog.warn("Ice Fill data failed to load", e);
		}
	}

	private void loadBoulder() {
		try (var in = PuzzleSolvers2.class.getResourceAsStream("/assets/scd/dungeon/boulder.json")) {
			if (in == null) return;
			for (var e : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().entrySet()) {
				List<int[]> moves = new ArrayList<>();
				for (JsonElement m : e.getValue().getAsJsonArray()) {
					JsonArray a = m.getAsJsonArray();
					moves.add(new int[]{a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt(), a.get(3).getAsInt()});
				}
				boulderSolutions.put(e.getKey(), moves);
			}
		} catch (Exception e) {
			ScdLog.warn("Boulder data failed to load", e);
		}
	}

	private static void readFloors(JsonArray floors, List<List<List<int[]>>> out) {
		for (JsonElement floor : floors) {
			List<List<int[]>> patterns = new ArrayList<>();
			for (JsonElement pattern : floor.getAsJsonArray()) {
				List<int[]> points = new ArrayList<>();
				for (JsonElement p : pattern.getAsJsonArray()) {
					JsonObject o = p.getAsJsonObject();
					points.add(new int[]{o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt()});
				}
				patterns.add(points);
			}
			out.add(patterns);
		}
	}

	private void resetRoom() {
		beams.clear();
		beamSlots.clear();
		ownBeamsWorked = false;
		beamsLogged = iceLogged = false;
		boulderBefore = null;
		icePath.clear();
		slidePath.clear();
		tpPads = List.of();
		tpCandidates = new HashSet<>();
		tpVisited.clear();
		tttMove = null;
		boulderMoves.clear();
		boulderRead = false;
		solvedFor = null;
	}

	private void tick() {
		MappedRoom room = dungeon.rooms().current();
		if (room == null || room.name() == null || room.anchor() == null) return;
		var mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return;
		long t = mod.tasks.currentTick();
		if (mod.config().dungeon.puzzleOn(room.name())) switch (room.name()) {
			case "Creeper Beams" -> {
				if (t % 10 == 0) beams(room, mc.level);
			}
			case "Ice Fill" -> {
				if (icePath.isEmpty() && t % 20 == 0 && !ownIceFill(room, mc.level)) iceFill(room, mc.level);
			}
			case "Ice Path" -> {
				if (t % 5 == 0) icePath(room, mc.level);
			}
			case "Teleport Maze" -> teleportMaze(room, mc.level, mc.player.position(), mc.player.getYRot());
			case "Tic Tac Toe" -> {
				if (t % 10 == 0) ticTacToe(room, mc.level);
			}
			case "Boulder" -> {
				if (!boulderRead && t % 10 == 0) boulder(room, mc.level);
				if (boulderBefore != null && t >= boulderAfterTick) {
					ScdLog.info("[puzzles] boulder click rel " + boulderClick.getX() + "," + boulderClick.getY() + "," + boulderClick.getZ()
							+ " before " + boulderBefore + " after " + boulderKey(room, mc.level));
					boulderBefore = null;
				}
			}
			default -> {
			}
		}
		solvedFor = room;
	}

	// ---- Creeper Beams ------------------------------------------------------------------------

	private void beams(MappedRoom room, Level level) {
		List<Beam> own = ownBeams(room, level);
		if (own != null) {
			ownBeamsWorked = true;
			beams.clear();
			beams.addAll(own);
			return;
		}
		if (ownBeamsWorked) return; // a bad scan: keep the last good pairs
		beams.clear();
		for (int i = 0; i < beamPairs.size(); i++) {
			int[] p = beamPairs.get(i);
			BlockPos a = room.toWorld(new BlockPos(p[0], p[1], p[2])), b = room.toWorld(new BlockPos(p[3], p[4], p[5]));
			if (level.getBlockState(a).getBlock() == Blocks.SEA_LANTERN && level.getBlockState(b).getBlock() == Blocks.SEA_LANTERN) {
				beams.add(new Beam(Vec3.atCenterOf(a), Vec3.atCenterOf(b), beamSlot(a, b)));
			}
		}
	}

	/**
	 * SCD's own Creeper Beams: every lit sea lantern around the creeper, paired with the lantern on
	 * the far side whose straight line runs through the creeper (closest fit first). Null when the
	 * creeper or the lanterns can't be found, so the bundled pair list takes over.
	 */
	private List<Beam> ownBeams(MappedRoom room, Level level) {
		net.minecraft.world.entity.monster.Creeper creeper = null;
		for (Entity e : ((net.minecraft.client.multiplayer.ClientLevel) level).entitiesForRendering()) {
			if (e instanceof net.minecraft.world.entity.monster.Creeper c && dungeon.rooms().roomAt(c.blockPosition()) == room) creeper = c;
		}
		if (creeper == null) return null;
		Vec3 centre = creeper.getBoundingBox().getCenter();
		BlockPos base = creeper.blockPosition();
		List<BlockPos> lanterns = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(base.offset(-16, -4, -16), base.offset(16, 14, 16))) {
			if (level.getBlockState(p).getBlock() == Blocks.SEA_LANTERN) lanterns.add(p.immutable());
		}
		record Candidate(BlockPos a, BlockPos b, double miss) {
		}
		List<Candidate> candidates = new ArrayList<>();
		for (int i = 0; i < lanterns.size(); i++) {
			for (int j = i + 1; j < lanterns.size(); j++) {
				Vec3 a = Vec3.atCenterOf(lanterns.get(i)), b = Vec3.atCenterOf(lanterns.get(j));
				Vec3 ab = b.subtract(a);
				double t = centre.subtract(a).dot(ab) / ab.lengthSqr();
				if (t < 0.15 || t > 0.85) continue; // the creeper must sit between the two
				double miss = a.add(ab.scale(t)).distanceTo(centre);
				if (miss < 1.2) candidates.add(new Candidate(lanterns.get(i), lanterns.get(j), miss));
			}
		}
		// Pairs from the last scan that still fit come first, so a lantern never swaps partner.
		Set<String> previous = new HashSet<>();
		for (Beam b : beams) previous.add(BlockPos.containing(b.a()).asLong() + ":" + BlockPos.containing(b.b()).asLong());
		candidates.sort(java.util.Comparator.<Candidate>comparingInt(c -> previous.contains(c.a().asLong() + ":" + c.b().asLong())
				|| previous.contains(c.b().asLong() + ":" + c.a().asLong()) ? 0 : 1).thenComparingDouble(Candidate::miss));
		Set<BlockPos> used = new HashSet<>();
		List<Beam> out = new ArrayList<>();
		for (Candidate c : candidates) {
			if (used.contains(c.a()) || used.contains(c.b())) continue;
			used.add(c.a());
			used.add(c.b());
			out.add(new Beam(Vec3.atCenterOf(c.a()), Vec3.atCenterOf(c.b()), beamSlot(c.a(), c.b())));
		}
		if (!beamsLogged) {
			beamsLogged = true;
			ScdLog.info("[puzzles] beams: " + lanterns.size() + " lit lanterns, " + out.size() + " pairs (own)");
		}
		if (lanterns.size() >= 2 && out.isEmpty()) return null;
		return out;
	}

	// ---- Ice Fill -----------------------------------------------------------------------------

	private void iceFill(MappedRoom room, Level level) {
		for (int floor = 0; floor < iceIds.size() && floor < iceEasy.size(); floor++) {
			var ids = iceIds.get(floor);
			for (int i = 0; i < ids.size(); i++) {
				int[] airAt = ids.get(i).get(0), solidAt = ids.get(i).get(1);
				if (level.getBlockState(room.toWorld(new BlockPos(airAt[0], airAt[1], airAt[2]))).isAir()
						&& !level.getBlockState(room.toWorld(new BlockPos(solidAt[0], solidAt[1], solidAt[2]))).isAir()) {
					for (int[] p : iceEasy.get(floor).get(i)) icePath.add(Vec3.atBottomCenterOf(room.toWorld(new BlockPos(p[0], p[1], p[2]))).add(0, 0.1, 0));
					break;
				}
			}
		}
	}

	/**
	 * SCD's own Ice Fill: every ice floor in the room (grouped by height), its two ends found where
	 * the ice meets a walkable platform, and a path over every tile between them. False when any
	 * floor can't be read or solved, so the bundled paths take over.
	 */
	private boolean ownIceFill(MappedRoom room, Level level) {
		int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
		for (int[] tile : room.tiles()) {
			minX = Math.min(minX, DungeonGrid.centre(tile[0]) - 15);
			maxX = Math.max(maxX, DungeonGrid.centre(tile[0]) + 15);
			minZ = Math.min(minZ, DungeonGrid.centre(tile[1]) - 15);
			maxZ = Math.max(maxZ, DungeonGrid.centre(tile[1]) + 15);
		}
		java.util.TreeMap<Integer, Set<Long>> floors = new java.util.TreeMap<>();
		for (int y = 60; y <= 100; y++) {
			for (int x = minX; x <= maxX; x++) {
				for (int z = minZ; z <= maxZ; z++) {
					var b = level.getBlockState(new BlockPos(x, y, z)).getBlock();
					if (b == Blocks.ICE || b == Blocks.PACKED_ICE) floors.computeIfAbsent(y, k -> new HashSet<>()).add(com.scd.logic.dungeon.IceFillSolver.key(new int[]{x, z}));
				}
			}
		}
		floors.values().removeIf(f -> f.size() < 4);
		if (floors.isEmpty()) return false;
		List<Vec3> path = new ArrayList<>();
		for (var floor : floors.entrySet()) {
			int y = floor.getKey();
			Set<Long> tiles = floor.getValue();
			// Ends: ice tiles next to a solid, walkable non-ice block at the same height.
			List<int[]> ends = new ArrayList<>();
			for (long k : tiles) {
				int x = (int) (k >> 32), z = (int) k;
				for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
					BlockPos n = new BlockPos(x + d[0], y, z + d[1]);
					if (tiles.contains(com.scd.logic.dungeon.IceFillSolver.key(new int[]{n.getX(), n.getZ()}))) continue;
					var state = level.getBlockState(n);
					if (!state.isAir() && state.getBlock() != Blocks.ICE && state.getBlock() != Blocks.PACKED_ICE && level.getBlockState(n.above()).isAir()) {
						ends.add(new int[]{x, z});
						break;
					}
				}
			}
			List<int[]> solved = null;
			for (int i = 0; i < ends.size() && solved == null; i++) {
				for (int j = i + 1; j < ends.size() && solved == null; j++) solved = com.scd.logic.dungeon.IceFillSolver.solve(tiles, ends.get(i), ends.get(j));
			}
			if (!iceLogged) ScdLog.info("[puzzles] ice fill floor y" + y + ": " + tiles.size() + " tiles, " + ends.size() + " ends, " + (solved != null ? "solved (own)" : "unsolved"));
			if (solved == null) {
				iceLogged = true;
				return false;
			}
			for (int[] p : solved) path.add(new Vec3(p[0] + 0.5, y + 1.1, p[1] + 0.5));
		}
		iceLogged = true;
		icePath.addAll(path);
		return true;
	}

	// ---- Ice Path (silverfish) ----------------------------------------------------------------

	private void icePath(MappedRoom room, net.minecraft.client.multiplayer.ClientLevel level) {
		Silverfish fish = null;
		for (Entity e : level.entitiesForRendering()) {
			if (e instanceof Silverfish s && dungeon.rooms().roomAt(s.blockPosition()) == room) {
				fish = s;
				break;
			}
		}
		slidePath.clear();
		if (fish == null) return;
		int y = fish.getBlockY();
		int cx = DungeonGrid.centre(room.tiles().getFirst()[0]), cz = DungeonGrid.centre(room.tiles().getFirst()[1]);
		int half = 11; // board (17) + walls + slack; leaving this window = out through the exit gap
		BlockPos start = fish.blockPosition();
		// BFS over slide end points.
		Map<BlockPos, BlockPos> parent = new HashMap<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		queue.add(start);
		parent.put(start, start);
		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		BlockPos exit = null;
		search:
		while (!queue.isEmpty()) {
			BlockPos cur = queue.poll();
			for (int[] d : dirs) {
				int x = cur.getX(), z = cur.getZ();
				boolean out = false;
				while (true) {
					int nx = x + d[0], nz = z + d[1];
					if (Math.abs(nx - cx) > half || Math.abs(nz - cz) > half) {
						out = true;
						break;
					}
					if (!level.getBlockState(new BlockPos(nx, y, nz)).isAir()) break;
					x = nx;
					z = nz;
				}
				BlockPos end = new BlockPos(x, y, z);
				if (out) {
					parent.putIfAbsent(end, cur);
					exit = end;
					break search;
				}
				if (!parent.containsKey(end)) {
					parent.put(end, cur);
					queue.add(end);
				}
			}
		}
		if (exit == null) return;
		List<Vec3> rev = new ArrayList<>();
		for (BlockPos p = exit; ; p = parent.get(p)) {
			rev.add(Vec3.atBottomCenterOf(p).add(0, 0.2, 0));
			if (p.equals(start) || parent.get(p) == null || parent.get(p).equals(p)) break;
		}
		for (int i = rev.size() - 1; i >= 0; i--) slidePath.add(rev.get(i));
	}

	// ---- Teleport Maze ------------------------------------------------------------------------

	private void teleportMaze(MappedRoom room, Level level, Vec3 player, float yaw) {
		if (tpPads.isEmpty()) {
			List<BlockPos> pads = new ArrayList<>();
			for (int[] t : room.tiles()) {
				int cx = DungeonGrid.centre(t[0]), cz = DungeonGrid.centre(t[1]);
				for (BlockPos p : BlockPos.betweenClosed(cx - 15, 69, cz - 15, cx + 15, 69, cz + 15)) {
					if (level.getBlockState(p).getBlock() == Blocks.END_PORTAL_FRAME) pads.add(p.immutable());
				}
			}
			tpPads = pads;
			tpCandidates = new HashSet<>(pads);
			lastPos = player;
			return;
		}
		if (lastPos != null && lastPos.distanceToSqr(player) > 9) {
			// Teleported: the pad you landed next to is used; you now face the correct exit.
			for (BlockPos p : tpPads) if (Vec3.atCenterOf(p).distanceToSqr(player) < 4) tpVisited.add(p);
			double rad = Math.toRadians(yaw);
			double dx = -Math.sin(rad), dz = Math.cos(rad);
			Set<BlockPos> hit = new HashSet<>();
			for (BlockPos p : tpPads) {
				if (tpVisited.contains(p)) continue;
				if (rayHits(player.x, player.z, dx, dz, p.getX() - 0.75, p.getZ() - 0.75, p.getX() + 1.75, p.getZ() + 1.75)) hit.add(p);
			}
			tpCandidates.retainAll(hit);
			if (tpCandidates.isEmpty()) tpCandidates = hit;
		}
		lastPos = player;
	}

	/** 2D ray (origin + t*dir, t in 0..32) against an axis-aligned rectangle. */
	private static boolean rayHits(double ox, double oz, double dx, double dz, double minX, double minZ, double maxX, double maxZ) {
		double t0 = 0, t1 = 32;
		double[] o = {ox, oz}, d = {dx, dz}, lo = {minX, minZ}, hi = {maxX, maxZ};
		for (int i = 0; i < 2; i++) {
			if (Math.abs(d[i]) < 1e-9) {
				if (o[i] < lo[i] || o[i] > hi[i]) return false;
			} else {
				double a = (lo[i] - o[i]) / d[i], b = (hi[i] - o[i]) / d[i];
				t0 = Math.max(t0, Math.min(a, b));
				t1 = Math.min(t1, Math.max(a, b));
				if (t0 > t1) return false;
			}
		}
		return true;
	}

	// ---- Tic Tac Toe --------------------------------------------------------------------------

	private void ticTacToe(MappedRoom room, net.minecraft.client.multiplayer.ClientLevel level) {
		tttMove = null;
		List<ItemFrame> frames = new ArrayList<>();
		for (Entity e : level.entitiesForRendering()) {
			if (e instanceof ItemFrame f && dungeon.rooms().roomAt(f.blockPosition()) == room && f.getFramedMapId(f.getItem()) != null) frames.add(f);
		}
		if (frames.isEmpty() || frames.size() >= 9 || frames.size() % 2 == 0) return; // your turn: odd count
		// The board hangs on one wall: every frame shares one horizontal coordinate.
		boolean wallOnX = frames.stream().map(f -> f.blockPosition().getX()).distinct().count() == 1;
		int plane = wallOnX ? frames.getFirst().blockPosition().getX() : frames.getFirst().blockPosition().getZ();
		// The nine squares are the frames (played) plus the buttons (still empty) on that wall.
		Set<BlockPos> squares = new HashSet<>();
		for (ItemFrame f : frames) squares.add(f.blockPosition());
		int minY = frames.stream().mapToInt(f -> f.blockPosition().getY()).min().orElse(0);
		int minH = frames.stream().mapToInt(f -> h(f.blockPosition(), wallOnX)).min().orElse(0);
		for (int y = minY - 2; y <= minY + 4; y++) {
			for (int hh = minH - 2; hh <= minH + 4; hh++) {
				BlockPos p = wallOnX ? new BlockPos(plane, y, hh) : new BlockPos(hh, y, plane);
				if (level.getBlockState(p).getBlock() instanceof net.minecraft.world.level.block.ButtonBlock) squares.add(p);
			}
		}
		List<Integer> rows = squares.stream().map(BlockPos::getY).distinct().sorted().toList();
		List<Integer> cols = squares.stream().map(p -> h(p, wallOnX)).distinct().sorted().toList();
		if (rows.size() != 3 || cols.size() != 3 || rows.get(2) - rows.get(0) != 2 || cols.get(2) - cols.get(0) != 2) {
			ScdLog.debug("ttt: board not found (" + squares.size() + " squares, rows " + rows + " cols " + cols + ")");
			return;
		}
		int rowTop = rows.get(2), first = cols.get(0);
		char[][] board = new char[3][3];
		for (ItemFrame f : frames) {
			MapItemSavedData data = level.getMapData(f.getFramedMapId(f.getItem()));
			if (data == null) continue;
			int row = rowTop - f.blockPosition().getY(), col = h(f.blockPosition(), wallOnX) - first;
			int middle = data.colors[64 * 128 + 64] & 0xFF;
			board[row][col] = middle == 114 ? 'X' : middle == 33 ? 'O' : 0;
		}
		int[] move = TicTacToe.bestMove(board);
		if (move == null) return;
		int hh = first + move[1];
		tttMove = wallOnX ? new BlockPos(plane, rowTop - move[0], hh) : new BlockPos(hh, rowTop - move[0], plane);
	}

	private static int h(BlockPos p, boolean wallOnX) {
		return wallOnX ? p.getZ() : p.getX();
	}

	// ---- Boulder ------------------------------------------------------------------------------

	/** The 7x6 boulder grid as 0/1 (row by row from z 24, x 24 down); null while not loaded. */
	private static String boulderKey(MappedRoom room, net.minecraft.client.multiplayer.ClientLevel level) {
		StringBuilder key = new StringBuilder();
		for (int z = 24; z >= 9; z -= 3) {
			for (int x = 24; x >= 6; x -= 3) {
				BlockPos p = room.toWorld(new BlockPos(x, 66, z));
				if (p == null || !level.isLoaded(p)) return null;
				key.append(level.getBlockState(p).isAir() ? '0' : '1');
			}
		}
		return key.toString();
	}

	/** Reads the 7x6 boulder grid (room frame, Odin's layout) and looks the layout up. */
	private void boulder(MappedRoom room, net.minecraft.client.multiplayer.ClientLevel level) {
		StringBuilder key = new StringBuilder();
		for (int z = 24; z >= 9; z -= 3) {
			for (int x = 24; x >= 6; x -= 3) {
				BlockPos p = room.toWorld(new BlockPos(x, 66, z));
				if (p == null || !level.isLoaded(p)) return;
				key.append(level.getBlockState(p).isAir() ? '0' : '1');
			}
		}
		List<int[]> moves = boulderSolutions.get(key.toString());
		if (moves == null) {
			ScdLog.debug("boulder: unknown layout " + key);
			return; // try again shortly (a boulder may still be moving)
		}
		boulderRead = true;
		boulderMoves.clear();
		for (int[] m : moves) boulderMoves.add(new BlockPos[]{room.toWorld(new BlockPos(m[0], 65, m[1])), room.toWorld(new BlockPos(m[2], 65, m[3]))});
		ScdLog.info("[puzzles] boulder layout " + key + ": " + moves.size() + " moves");
	}

	// ---- drawing ------------------------------------------------------------------------------

	private void draw() {
		var cfg = mod.config().dungeon;
		int[] colors = {0xFFFACC15, 0xFF4ADE80, 0xFFE879F9, 0xFF22D3EE, 0xFFFB923C, 0xFFF87171, 0xFFFFFFFF, 0xFFA78BFA};
		if (cfg.puzzleOn("Creeper Beams")) for (Beam b : beams) {
			int c = colors[b.pair() % colors.length];
			WorldGizmos.block(BlockPos.containing(b.a()), c, true);
			WorldGizmos.block(BlockPos.containing(b.b()), c, true);
			WorldGizmos.line(b.a(), b.b(), c, true);
		}
		if (cfg.puzzleOn("Ice Fill")) for (int i = 0; i + 1 < icePath.size(); i++) WorldGizmos.line(icePath.get(i), icePath.get(i + 1), 0xFF22D3EE, true);
		if (cfg.puzzleOn("Ice Path")) for (int i = 0; i + 1 < slidePath.size(); i++) WorldGizmos.line(slidePath.get(i), slidePath.get(i + 1), 0xFFF87171, true);
		if (cfg.puzzleOn("Teleport Maze")) for (BlockPos p : tpPads) {
			if (tpVisited.contains(p)) WorldGizmos.block(p, 0x80888888, true);
			else if (tpCandidates.contains(p) && tpCandidates.size() < tpPads.size()) WorldGizmos.block(p, tpCandidates.size() == 1 ? 0xFF4ADE80 : 0xFFFACC15, true);
		}
		if (cfg.puzzleOn("Boulder")) for (int i = 0; i < boulderMoves.size(); i++) {
			WorldGizmos.block(boulderMoves.get(i)[0], i == 0 ? 0xFF4ADE80 : 0x80FACC15, true);
		}
		if (tttMove != null && cfg.puzzleOn("Tic Tac Toe")) WorldGizmos.block(tttMove, 0xFF4ADE80, true);
	}
}
