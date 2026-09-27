package com.scd.logic.dungeon.room;

/**
 * Which way a room faces, and the room-relative coordinate frame built on it.
 *
 * <p>Every Catacombs room has one blue terracotta block on its roof line. Its position relative to
 * the room is fixed, so its world position tells the rotation, and it is the origin of the relative
 * frame: {@code relative = toRelative(world - clay)}, with the room turned so that it always "faces
 * north". This is the frame Odin's rooms/waypoints use, so routes and waypoints recorded in one run
 * replay in every rotation of the same room.
 *
 * <p>For a 1x1 room the clay sits at (dx, dz) from the tile centre.
 */
public enum RoomRotation {
	NORTH(15, 15),
	SOUTH(-15, -15),
	WEST(15, -15),
	EAST(-15, 15);

	public final int dx;
	public final int dz;

	RoomRotation(int dx, int dz) {
		this.dx = dx;
		this.dz = dz;
	}

	/** World offset from the clay -> room-relative (x, z). */
	public int[] toRelative(int x, int z) {
		return switch (this) {
			case NORTH -> new int[]{-x, -z};
			case WEST -> new int[]{z, -x};
			case SOUTH -> new int[]{x, z};
			case EAST -> new int[]{-z, x};
		};
	}

	/** Room-relative (x, z) -> world offset from the clay. Inverse of {@link #toRelative}. */
	public int[] toWorld(int x, int z) {
		return switch (this) {
			case NORTH -> new int[]{-x, -z};
			case WEST -> new int[]{-z, x};
			case SOUTH -> new int[]{x, z};
			case EAST -> new int[]{z, -x};
		};
	}

	/** Same as {@link #toRelative} for sub-block positions (block centres are at +0.5). */
	public double[] toRelative(double x, double z) {
		return switch (this) {
			case NORTH -> new double[]{-x, -z};
			case WEST -> new double[]{z, -x};
			case SOUTH -> new double[]{x, z};
			case EAST -> new double[]{-z, x};
		};
	}

	public double[] toWorld(double x, double z) {
		return switch (this) {
			case NORTH -> new double[]{-x, -z};
			case WEST -> new double[]{-z, x};
			case SOUTH -> new double[]{x, z};
			case EAST -> new double[]{z, -x};
		};
	}
}
