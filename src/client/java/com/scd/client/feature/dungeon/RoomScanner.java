package com.scd.client.feature.dungeon;

import com.scd.client.net.Backend;
import com.scd.client.net.BackendClient;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Opt-in contributor for the backend's crowd-sourced room database. Catacombs rooms sit on a
 * 32-block grid; while walking around, non-air blocks near the player are fingerprinted relative
 * to the current cell, and a cell's fingerprint is uploaded once the player leaves it (if it has at
 * least 50 blocks). Only block ids and cell-relative positions are sent - nothing about the player.
 * Leaving the dungeon mid-cell discards the partial scan.
 */
final class RoomScanner {
	private static final int GRID = 32;
	private static final int RADIUS_XZ = 6;
	private static final int RADIUS_Y = 10;
	private static final int INTERVAL_TICKS = 10;
	private static final int MIN_BLOCKS = 50;
	private static final int Y_BIAS = 512;

	private Long cell;
	private String floor;
	private final Map<Long, String> blocks = new HashMap<>();
	private int ticks;

	void tick(BackendClient backend, DungeonState state, boolean enabled) {
		var mc = Minecraft.getInstance();
		if (!enabled || !state.inDungeon() || mc.level == null || mc.player == null) {
			reset();
			return;
		}
		BlockPos pos = mc.player.blockPosition();
		int cx = Math.floorDiv(pos.getX(), GRID), cz = Math.floorDiv(pos.getZ(), GRID);
		long key = ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
		if (cell == null) {
			cell = key;
			floor = state.floor();
		} else if (key != cell) {
			submit(backend);
			blocks.clear();
			cell = key;
			floor = state.floor();
		}
		if (++ticks < INTERVAL_TICKS) return;
		ticks = 0;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int dx = -RADIUS_XZ; dx <= RADIUS_XZ; dx++) {
			for (int dz = -RADIUS_XZ; dz <= RADIUS_XZ; dz++) {
				int wx = pos.getX() + dx, wz = pos.getZ() + dz;
				int rx = wx - cx * GRID, rz = wz - cz * GRID;
				if (rx < 0 || rx >= GRID || rz < 0 || rz >= GRID) continue;
				for (int dy = -RADIUS_Y; dy <= RADIUS_Y; dy++) {
					int wy = pos.getY() + dy;
					BlockState bs = mc.level.getBlockState(cursor.set(wx, wy, wz));
					if (bs.isAir()) continue;
					blocks.putIfAbsent(encode(rx, wy, rz), BuiltInRegistries.BLOCK.getKey(bs.getBlock()).toString());
				}
			}
		}
	}

	private void submit(BackendClient backend) {
		if (blocks.size() < MIN_BLOCKS || floor == null) return;
		List<Backend.RoomBlock> out = new ArrayList<>(blocks.size());
		for (var e : blocks.entrySet()) {
			long k = e.getKey();
			out.add(new Backend.RoomBlock((int) ((k >> 20) & 0x3F), (int) ((k >> 6) & 0x3FFF) - Y_BIAS, (int) (k & 0x3F), e.getValue()));
		}
		backend.reportDungeonRoom(floor, out);
	}

	private void reset() {
		cell = null;
		floor = null;
		blocks.clear();
		ticks = 0;
	}

	private static long encode(int rx, int y, int rz) {
		return ((long) (rx & 0x3F) << 20) | ((long) ((y + Y_BIAS) & 0x3FFF) << 6) | (rz & 0x3F);
	}

	String describe() {
		return cell == null ? "idle" : "cell " + cell + " floor " + floor + " blocks " + blocks.size() + " (uploads at " + MIN_BLOCKS + "+)";
	}
}
