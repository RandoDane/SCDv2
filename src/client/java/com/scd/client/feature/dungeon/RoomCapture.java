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
 * {@link RoomStudio}). A room is captured once, as soon as it is identified, anchored and fully
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
		mod.bus.subscribe(Events.Tick.class, e -> {
			if (!mod.config().dungeon.captureRooms || !dungeon.state().inDungeon()) {
				job = null;
				return;
			}
			ScdLog.guard("room capture", this::tick);
		});
		mod.bus.subscribe(Events.WorldChanged.class, e -> job = null);
	}

	static String fileName(String room) {
		return room.replaceAll("[^A-Za-z0-9 _'-]", "_");
	}

	int count() {
		return captured.size();
	}

	boolean has(String room) {
		return captured.contains(fileName(room));
	}

	void recapture(MappedRoom room) {
		forced = room;
	}

	private void tick() {
		var level = Minecraft.getInstance().level;
		if (level == null) return;
		if (job == null) {
			if (mod.tasks.currentTick() % 20 != 0) return;
			for (MappedRoom r : dungeon.rooms().rooms()) {
				if (r.name() == null || r.anchor() == null || !r.complete()) continue;
				if (has(r.name()) && r != forced) continue;
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
			if (j.room == forced) forced = null;
			save(j);
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
		return true;
	}

	private void save(Job j) {
		String name = j.room.name();
		CompoundTag tag = new CompoundTag();
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
		captured.add(fileName(name));
		Path file = DIR.resolve(fileName(name) + ".nbt");
		Thread.ofVirtual().start(() -> {
			try {
				Files.createDirectories(DIR);
				NbtIo.writeCompressed(tag, file);
				ScdLog.info("[rooms] captured " + name + " (" + j.order.size() + " block types)");
			} catch (Exception e) {
				ScdLog.warn("Could not save captured room " + name, e);
			}
		});
	}
}
