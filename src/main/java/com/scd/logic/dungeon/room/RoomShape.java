package com.scd.logic.dungeon.room;

/** Room footprint in 32-block tiles. {@code key} is the spelling used in rooms.json. */
public enum RoomShape {
	ONE_BY_ONE("1x1", 1),
	ONE_BY_TWO("1x2", 2),
	ONE_BY_THREE("1x3", 3),
	ONE_BY_FOUR("1x4", 4),
	TWO_BY_TWO("2x2", 4),
	L("L", 3);

	public final String key;
	public final int tiles;

	RoomShape(String key, int tiles) {
		this.key = key;
		this.tiles = tiles;
	}

	public static RoomShape fromKey(String key) {
		for (RoomShape s : values()) if (s.key.equalsIgnoreCase(key)) return s;
		return null;
	}

	/** Shape implied by a set of tiles (as found on the map), given its bounding box. */
	public static RoomShape fromTiles(int count, int spanX, int spanZ) {
		return switch (count) {
			case 2 -> ONE_BY_TWO;
			case 3 -> spanX == 2 && spanZ == 2 ? L : ONE_BY_THREE;
			case 4 -> spanX == 2 && spanZ == 2 ? TWO_BY_TWO : ONE_BY_FOUR;
			default -> ONE_BY_ONE;
		};
	}
}
