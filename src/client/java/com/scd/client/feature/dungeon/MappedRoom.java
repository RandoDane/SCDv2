package com.scd.client.feature.dungeon;

import com.scd.logic.dungeon.room.Checkmark;
import com.scd.logic.dungeon.room.RoomInfo;
import com.scd.logic.dungeon.room.RoomKind;
import com.scd.logic.dungeon.room.RoomPlacement;
import com.scd.logic.dungeon.room.RoomShape;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * One room of the current run: which tiles it covers, what it is (if its core is known), and its
 * anchor. With an anchor, positions convert between world and room-relative coordinates, which is
 * what routes and waypoints are stored in.
 */
public final class MappedRoom {
	/** Tile coordinates {x, z} in the 6x6 grid. */
	final List<int[]> tiles = new ArrayList<>(4);
	RoomInfo info;
	RoomKind kind;
	/** Core of the first tile scanned (for reporting unknown rooms). */
	Integer core;
	int highestBlock;
	RoomPlacement.Anchor anchor;
	/** "block" when the clay was found in the world, "geometry" when solved from the footprint. */
	String anchorSource;
	Checkmark checkmark = Checkmark.UNDISCOVERED;
	boolean visited;

	public String name() {
		return info != null ? info.name() : null;
	}

	public RoomKind kind() {
		return info != null ? info.kind() : kind;
	}

	public RoomShape expectedShape() {
		return info != null ? info.shape() : null;
	}

	public RoomInfo info() {
		return info;
	}

	public RoomPlacement.Anchor anchor() {
		return anchor;
	}

	public Checkmark checkmark() {
		return checkmark;
	}

	public List<int[]> tiles() {
		return List.copyOf(tiles);
	}

	public boolean hasTile(int x, int z) {
		for (int[] t : tiles) if (t[0] == x && t[1] == z) return true;
		return false;
	}

	/** All tiles found (so the footprint, and with it a multi-tile anchor, is final). */
	boolean complete() {
		return info != null && tiles.size() == info.shape().tiles;
	}

	/** World block -> room-relative block (y unchanged); null without an anchor. */
	public BlockPos toRelative(BlockPos world) {
		if (anchor == null) return null;
		int[] r = anchor.rotation().toRelative(world.getX() - anchor.x(), world.getZ() - anchor.z());
		return new BlockPos(r[0], world.getY(), r[1]);
	}

	public BlockPos toWorld(BlockPos rel) {
		if (anchor == null) return null;
		int[] w = anchor.rotation().toWorld(rel.getX(), rel.getZ());
		return new BlockPos(w[0] + anchor.x(), rel.getY(), w[1] + anchor.z());
	}

	/** Sub-block positions rotate around block centres, so blocks and points stay consistent. */
	public Vec3 toRelative(Vec3 world) {
		if (anchor == null) return null;
		double[] r = anchor.rotation().toRelative(world.x - anchor.x() - 0.5, world.z - anchor.z() - 0.5);
		return new Vec3(r[0] + 0.5, world.y, r[1] + 0.5);
	}

	public Vec3 toWorld(Vec3 rel) {
		if (anchor == null) return null;
		double[] w = anchor.rotation().toWorld(rel.x - 0.5, rel.z - 0.5);
		return new Vec3(w[0] + anchor.x() + 0.5, rel.y, w[1] + anchor.z() + 0.5);
	}

	public String label() {
		String n = name();
		return n != null ? n : (kind() != null ? kind().name().toLowerCase(java.util.Locale.ROOT) : "room") + " (unknown)";
	}
}
