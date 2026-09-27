package com.scd.client.feature.dungeon;

import com.scd.client.ScdMod;
import com.scd.client.core.Events;
import com.scd.client.core.ScdLog;
import com.scd.client.storage.ScdPaths;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.state.BlockState;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Saves a copy of every dungeon room you play through, for rebuilding them in singleplayer (see
 * {@link RoomStudio}), and shares it through the SCD server (backend/roomCaptures.js): rooms the
 * server already has aren't captured again, new ones are uploaded, and the progress everyone sees
 * is the shared one. A room is captured once, as soon as it is identified, anchored and fully
 * loaded (usually before anyone has opened or broken anything), a few thousand blocks per tick so
 * it never stutters. Files: config/scd/dungeon/captured/&lt;room&gt;.nbt with the blocks, the room's
 * name and its anchor/rotation, so positions in the copy map to the same room coordinates.
 */
final class RoomCapture {
	static final Path DIR = ScdPaths.file("dungeon/captured");
	private static final int BLOCKS_PER_TICK = 60_000;

	private final ScdMod mod;
	private final DungeonFeature dungeon;
	private final Set<String> captured = new HashSet<>();
	/** Block-layout signatures already saved per puzzle room, to keep one copy per variation. */
	private final Map<String, Set<Integer>> variants = new java.util.concurrent.ConcurrentHashMap<>();
	/** What the server has: room names, and puzzle variation signatures per room. */
	private final Set<String> serverNames = java.util.concurrent.ConcurrentHashMap.newKeySet();
	private final Map<String, Set<Integer>> serverSigs = new java.util.concurrent.ConcurrentHashMap<>();
	private volatile List<com.scd.client.net.BackendClient.CapturedRoom> serverRooms = List.of();
	/** Local copies: file id -> {name, signature, puzzle}. */
	private final Map<String, Object[]> local = new java.util.concurrent.ConcurrentHashMap<>();
	private long lastSync = Long.MIN_VALUE;
	private boolean backfilled;

	/** Puzzle rooms already copied this run (they're re-copied each run to catch new variations). */
	private final Set<MappedRoom> copiedThisRun = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
	private Job job;
	/** Room to capture again even though a copy exists ({@code /scd rooms recapture}). */
	private MappedRoom forced;

	private static final class Job {
		MappedRoom room;
		int minX, minY, minZ, sx, sy, sz;
		int cursor;
		int[] blocks;
		final Map<BlockState, Integer> palette = new HashMap<>();
		final List<BlockState> order = new ArrayList<>();
	}

	RoomCapture(ScdMod mod, DungeonFeature dungeon) {
		this.mod = mod;
		this.dungeon = dungeon;
		try {
			if (Files.isDirectory(DIR)) try (var files = Files.list(DIR)) {
				files.forEach(f -> captured.add(f.getFileName().toString().replaceFirst("\\.nbt$", "")));
			}
		} catch (Exception e) {
			ScdLog.warn("Could not list captured rooms", e);
		}
		Thread.ofVirtual().start(this::loadVariants);
		mod.bus.subscribe(Events.Tick.class, e -> {
			if (!mod.config().dungeon.captureRooms || !dungeon.state().inDungeon()) {
				job = null;
				return;
			}
			ScdLog.guard("room capture", this::tick);
		});
		mod.bus.subscribe(Events.WorldChanged.class, e -> {
			job = null;
			copiedThisRun.clear();
			if (mod.tasks.currentTick() - lastSync > 1200) sync();
		});
	}

	private void loadVariants() {
		try (var files = Files.list(DIR)) {
			for (Path f : files.toList()) {
				try {
					CompoundTag t = NbtIo.readCompressed(f, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
					String id = f.getFileName().toString().replaceFirst("\\.nbt$", "");
					String room = t.getStringOr("name", "?");
					int sig = t.getIntOr("signature", 0);
					variants.computeIfAbsent(room, k -> java.util.concurrent.ConcurrentHashMap.newKeySet()).add(sig);
					local.put(id, new Object[]{room, sig, "PUZZLE".equals(t.getStringOr("kind", ""))});
				} catch (Exception ignored) {
					// An unreadable copy just gets captured again.
				}
			}
		} catch (Exception ignored) {
			// No captures yet.
		}
	}

	/** Saved variations of a puzzle room so far. */
	int variantCount(String room) {
		return variants.getOrDefault(room, Set.of()).size();
	}

	static String fileName(String room) {
		return room.replaceAll("[^A-Za-z0-9 _'-]", "_");
	}

	int count() {
		return captured.size();
	}

	/** Captured by you or by anyone on the server. */
	boolean has(String room) {
		return captured.contains(fileName(room)) || serverNames.contains(room);
	}

	/** Puzzle variations you or anyone has. */
	int sharedVariants(String room) {
		Set<Integer> all = new HashSet<>(variants.getOrDefault(room, Set.of()));
		all.addAll(serverSigs.getOrDefault(room, Set.of()));
		return all.size();
	}

	/** Refreshes what the server has; then shares local copies it doesn't (once per session). */
	java.util.concurrent.CompletableFuture<Void> sync() {
		lastSync = mod.tasks.currentTick();
		return mod.backend.capturedRooms().thenAccept(list -> {
			serverRooms = List.copyOf(list);
			for (var r : list) {
				serverNames.add(r.name());
				serverSigs.computeIfAbsent(r.name(), k -> java.util.concurrent.ConcurrentHashMap.newKeySet()).add(r.signature());
			}
			if (!backfilled) {
				backfilled = true;
				for (var e : local.entrySet()) {
					String room = (String) e.getValue()[0];
					int sig = (int) e.getValue()[1];
					boolean puzzle = (boolean) e.getValue()[2];
					if (puzzle ? !serverSigs.getOrDefault(room, Set.of()).contains(sig) : !serverNames.contains(room)) {
						upload(room, sig, puzzle, DIR.resolve(e.getKey() + ".nbt"));
					}
				}
			}
		}).exceptionally(err -> null);
	}

	private void upload(String room, int sig, boolean puzzle, Path file) {
		try {
			byte[] bytes = Files.readAllBytes(file);
			mod.backend.uploadRoom(room, sig, puzzle, bytes).thenAccept(stored -> {
				serverNames.add(room);
				serverSigs.computeIfAbsent(room, k -> java.util.concurrent.ConcurrentHashMap.newKeySet()).add(sig);
				if (stored) ScdLog.info("[rooms] shared " + room + " with the server");
			}).exceptionally(err -> {
				ScdLog.debug("room upload failed: " + err.getMessage());
				return null;
			});
		} catch (Exception e) {
			ScdLog.debug("room upload failed: " + e.getMessage());
		}
	}

	/** Downloads every room on the server that you don't have a copy of (for /scd rooms build). */
	java.util.concurrent.CompletableFuture<Integer> fetchMissing() {
		return mod.backend.capturedRooms().thenCompose(list -> {
			List<java.util.concurrent.CompletableFuture<Boolean>> jobs = new ArrayList<>();
			for (var r : list) {
				boolean mine = local.values().stream().anyMatch(m -> m[0].equals(r.name()) && (!r.puzzle() || (int) m[1] == r.signature()));
				if (mine) continue;
				jobs.add(mod.backend.downloadRoom(r.id()).thenApply(bytes -> {
					try {
						Files.createDirectories(DIR);
						String base = fileName(r.name()), id = base;
						for (int n = 2; captured.contains(id); n++) id = base + " (" + n + ")";
						Files.write(DIR.resolve(id + ".nbt"), bytes);
						captured.add(id);
						local.put(id, new Object[]{r.name(), r.signature(), r.puzzle()});
						return true;
					} catch (Exception e) {
						return false;
					}
				}).exceptionally(err -> false));
			}
			return java.util.concurrent.CompletableFuture.allOf(jobs.toArray(new java.util.concurrent.CompletableFuture[0]))
					.thenApply(v -> (int) jobs.stream().filter(j -> j.join()).count());
		});
	}

	void recapture(MappedRoom room) {
		forced = room;
	}

	private void tick() {
		var level = Minecraft.getInstance().level;
		if (level == null) return;
		if (job == null) {
			if (mod.tasks.currentTick() - lastSync > 6000) sync();
			if (mod.tasks.currentTick() % 20 != 0) return;
			for (MappedRoom r : dungeon.rooms().rooms()) {
				if (r.name() == null || r.anchor() == null || !r.complete()) continue;
				boolean puzzle = r.kind() == com.scd.logic.dungeon.room.RoomKind.PUZZLE;
				if (r != forced && (puzzle ? copiedThisRun.contains(r) : has(r.name()))) continue;
				if (start(r, level)) break;
			}
			return;
		}
		// Copy a slice of the room per tick.
		Job j = job;
		int total = j.sx * j.sy * j.sz, end = Math.min(total, j.cursor + BLOCKS_PER_TICK);
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int i = j.cursor; i < end; i++) {
			int x = i % j.sx, z = (i / j.sx) % j.sz, y = i / (j.sx * j.sz);
			BlockState s = level.getBlockState(p.set(j.minX + x, j.minY + y, j.minZ + z));
			if (s.isAir()) continue;
			j.blocks[i] = j.palette.computeIfAbsent(s, k -> {
				j.order.add(k);
				return j.order.size();
			});
		}
		j.cursor = end;
		if (end >= total) {
			job = null;
			boolean wasForced = j.room == forced;
			if (wasForced) forced = null;
			save(j, wasForced);
		}
	}

	/** Starts copying a room once every chunk it covers is loaded. */
	private boolean start(MappedRoom r, net.minecraft.client.multiplayer.ClientLevel level) {
		int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
		for (int[] t : r.tiles()) {
			minX = Math.min(minX, com.scd.logic.dungeon.room.DungeonGrid.centre(t[0]) - 15);
			maxX = Math.max(maxX, com.scd.logic.dungeon.room.DungeonGrid.centre(t[0]) + 15);
			minZ = Math.min(minZ, com.scd.logic.dungeon.room.DungeonGrid.centre(t[1]) - 15);
			maxZ = Math.max(maxZ, com.scd.logic.dungeon.room.DungeonGrid.centre(t[1]) + 15);
		}
		for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
			for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) if (!level.hasChunk(cx, cz)) return false;
		}
		Job j = new Job();
		j.room = r;
		j.minX = minX;
		j.minZ = minZ;
		j.minY = 0;
		j.sx = maxX - minX + 1;
		j.sz = maxZ - minZ + 1;
		j.sy = Math.min(level.getMaxY(), Math.max(r.highestBlock, 100) + 2) + 1;
		j.blocks = new int[j.sx * j.sy * j.sz];
		job = j;
		copiedThisRun.add(r);
		return true;
	}

	/** The block layout as one number: same blocks in the same places = same variation. */
	private static int signature(Job j) {
		int h = 1;
		for (int b : j.blocks) h = 31 * h + (b == 0 ? 0 : j.order.get(b - 1).hashCode());
		return h;
	}

	private void save(Job j, boolean forcedCopy) {
		String name = j.room.name();
		int sig = signature(j);
		boolean puzzle = j.room.kind() == com.scd.logic.dungeon.room.RoomKind.PUZZLE;
		Set<Integer> known = variants.computeIfAbsent(name, k -> java.util.concurrent.ConcurrentHashMap.newKeySet());
		if (puzzle && !forcedCopy && (known.contains(sig) || serverSigs.getOrDefault(name, Set.of()).contains(sig))) return; // variation already saved (here or on the server)
		// First copy is "<room>", later variations "<room> (2)", "(3)"...
		String base = fileName(name), id = base;
		if (puzzle && !forcedCopy) for (int n = 2; captured.contains(id); n++) id = base + " (" + n + ")";
		known.add(sig);
		CompoundTag tag = new CompoundTag();
		tag.putInt("signature", sig);
		tag.putString("name", name);
		if (j.room.kind() != null) tag.putString("kind", j.room.kind().name());
		if (j.room.info() != null) tag.putString("shape", j.room.info().shape().key);
		tag.putString("rotation", j.room.anchor().rotation().name());
		tag.putInt("anchorX", j.room.anchor().x());
		tag.putInt("anchorZ", j.room.anchor().z());
		tag.putIntArray("min", new int[]{j.minX, j.minY, j.minZ});
		tag.putIntArray("size", new int[]{j.sx, j.sy, j.sz});
		ListTag palette = new ListTag();
		for (BlockState s : j.order) palette.add(NbtUtils.writeBlockState(s));
		tag.put("palette", palette);
		tag.putIntArray("blocks", j.blocks);
		captured.add(id);
		Path file = DIR.resolve(id + ".nbt");
		String label = id;
		Thread.ofVirtual().start(() -> {
			try {
				Files.createDirectories(DIR);
				NbtIo.writeCompressed(tag, file);
				local.put(label, new Object[]{name, sig, puzzle});
				ScdLog.info("[rooms] captured " + label + " (" + j.order.size() + " block types)");
				upload(name, sig, puzzle, file);
			} catch (Exception e) {
				ScdLog.warn("Could not save captured room " + name, e);
			}
		});
	}
}
