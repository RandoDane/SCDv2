package com.scd.logic.dungeon.room;

/** Room category. {@code mapColor} is the colour Hypixel's dungeon map paints the room with. */
public enum RoomKind {
	ENTRANCE(30),
	FAIRY(82),
	NORMAL(63),
	/** Rare normal rooms: same map colour as NORMAL, only known after identification. */
	RARE(63),
	BLOOD(18),
	CHAMPION(74),
	PUZZLE(66),
	TRAP(62),
	/** Seen on the map but not opened yet (grey). */
	UNKNOWN(85);

	public final byte mapColor;

	RoomKind(int mapColor) {
		this.mapColor = (byte) mapColor;
	}

	/** Map colour -> kind (NORMAL for 63); null for anything that isn't a room colour. */
	public static RoomKind fromMapColor(byte color) {
		for (RoomKind k : values()) if (k != RARE && k.mapColor == color) return k;
		return null;
	}

	public static RoomKind fromKey(String key) {
		try {
			return valueOf(key.toUpperCase(java.util.Locale.ROOT));
		} catch (IllegalArgumentException | NullPointerException e) {
			return null;
		}
	}
}
