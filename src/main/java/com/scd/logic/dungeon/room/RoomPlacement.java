package com.scd.logic.dungeon.room;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Where a room's blue-terracotta anchor ("clay") is, and so which way it faces.
 *
 * <p>The clay always sits on a corner of the room's bounding box, 15 blocks from the nearest tile
 * centre, and the corner encodes the rotation: north-west = SOUTH (the identity frame), north-east
 * = WEST, south-east = NORTH, south-west = EAST. For a 1x1 room the corner can only be found by
 * looking for the block; multi-tile rooms can also be solved from their footprint:
 * <ul>
 *     <li>1xN rooms: horizontal = SOUTH, vertical = WEST</li>
 *     <li>2x2 rooms: SOUTH</li>
 *     <li>L rooms: SOUTH is missing its north-east tile; every 90 degrees clockwise moves the gap
 *     one corner on (WEST: south-east missing, NORTH: south-west, EAST: north-west)</li>
 * </ul>
 * The client prefers finding the block at the candidate corners and falls back to the footprint.
 */
public final class RoomPlacement {
	public record Anchor(RoomRotation rotation, int x, int z) {
	}

	private RoomPlacement() {
	}

	/** Candidate clay positions (x, z) per rotation: the four corners of the tiles' bounding box. */
	public static Map<RoomRotation, int[]> corners(List<int[]> tiles) {
		Map<RoomRotation, int[]> out = cornersNoPrune(tiles);
		int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		for (int[] t : tiles) {
			minX = Math.min(minX, t[0]);
			minZ = Math.min(minZ, t[1]);
			maxX = Math.max(maxX, t[0]);
			maxZ = Math.max(maxZ, t[1]);
		}
		// An L room's missing corner can't hold its clay.
		if (tiles.size() == 3 && maxX - minX == 1 && maxZ - minZ == 1) out.remove(missingCornerRotation(tiles, minX, minZ));
		return out;
	}

	/** Rotation solved from the footprint alone; null for 1x1 rooms (needs the block) or bad input. */
	public static Anchor fromGeometry(List<int[]> tiles, RoomShape shape) {
		if (tiles.isEmpty() || shape == RoomShape.ONE_BY_ONE || tiles.size() != shape.tiles) return null;
		int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
		for (int[] t : tiles) {
			minX = Math.min(minX, t[0]);
			minZ = Math.min(minZ, t[1]);
			maxX = Math.max(maxX, t[0]);
		}
		RoomRotation rot;
		if (shape == RoomShape.L) {
			RoomRotation missing = missingCornerRotation(tiles, minX, minZ);
			if (missing == null) return null;
			// The gap sits one corner clockwise of the clay.
			rot = switch (missing) {
				case WEST -> RoomRotation.SOUTH;  // gap NE
				case NORTH -> RoomRotation.WEST;  // gap SE
				case EAST -> RoomRotation.NORTH;  // gap SW
				case SOUTH -> RoomRotation.EAST;  // gap NW
			};
		} else if (shape == RoomShape.TWO_BY_TWO) {
			rot = RoomRotation.SOUTH;
		} else {
			rot = minX == maxX ? RoomRotation.WEST : RoomRotation.SOUTH;
		}
		int[] c = cornersNoPrune(tiles).get(rot);
		return new Anchor(rot, c[0], c[1]);
	}

	/** Clockwise successor of a clay corner: NW (SOUTH) -> NE (WEST) -> SE (NORTH) -> SW (EAST). */
	public static RoomRotation clockwise(RoomRotation r) {
		return switch (r) {
			case SOUTH -> RoomRotation.WEST;
			case WEST -> RoomRotation.NORTH;
			case NORTH -> RoomRotation.EAST;
			case EAST -> RoomRotation.SOUTH;
		};
	}

	/**
	 * Chooses the clay among blue terracotta found at tile corners. One: that one. A pair: the one
	 * whose clockwise successor label is the other - invariant under rotating the room, so the same
	 * physical block is chosen in every orientation. Anything else: the first in probe order.
	 */
	public static Anchor pickClay(List<Anchor> found) {
		if (found.isEmpty()) return null;
		if (found.size() == 1) return found.getFirst();
		for (Anchor a : found) {
			boolean hasSuccessor = false, hasPredecessor = false;
			for (Anchor b : found) {
				if (b == a) continue;
				if (b.rotation() == clockwise(a.rotation())) hasSuccessor = true;
				if (clockwise(b.rotation()) == a.rotation()) hasPredecessor = true;
			}
			if (hasSuccessor && !hasPredecessor) return a;
		}
		return found.getFirst();
	}

	/** Anchor for a 1x1 room from the rotation whose probe found the clay. */
	public static Anchor oneByOne(int tileX, int tileZ, RoomRotation rot) {
		return new Anchor(rot, DungeonGrid.centre(tileX) + rot.dx, DungeonGrid.centre(tileZ) + rot.dz);
	}

	private static Map<RoomRotation, int[]> cornersNoPrune(List<int[]> tiles) {
		int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		for (int[] t : tiles) {
			minX = Math.min(minX, t[0]);
			minZ = Math.min(minZ, t[1]);
			maxX = Math.max(maxX, t[0]);
			maxZ = Math.max(maxZ, t[1]);
		}
		int w = DungeonGrid.centre(minX) - 15, e = DungeonGrid.centre(maxX) + 15;
		int n = DungeonGrid.centre(minZ) - 15, s = DungeonGrid.centre(maxZ) + 15;
		Map<RoomRotation, int[]> out = new EnumMap<>(RoomRotation.class);
		out.put(RoomRotation.SOUTH, new int[]{w, n});
		out.put(RoomRotation.WEST, new int[]{e, n});
		out.put(RoomRotation.NORTH, new int[]{e, s});
		out.put(RoomRotation.EAST, new int[]{w, s});
		return out;
	}

	/** The corner (named by the rotation whose clay would sit there) that an L room lacks. */
	private static RoomRotation missingCornerRotation(List<int[]> tiles, int minX, int minZ) {
		boolean nw = has(tiles, minX, minZ), ne = has(tiles, minX + 1, minZ);
		boolean se = has(tiles, minX + 1, minZ + 1), sw = has(tiles, minX, minZ + 1);
		if (!nw) return RoomRotation.SOUTH;
		if (!ne) return RoomRotation.WEST;
		if (!se) return RoomRotation.NORTH;
		if (!sw) return RoomRotation.EAST;
		return null;
	}

	private static boolean has(List<int[]> tiles, int x, int z) {
		for (int[] t : tiles) if (t[0] == x && t[1] == z) return true;
		return false;
	}
}
