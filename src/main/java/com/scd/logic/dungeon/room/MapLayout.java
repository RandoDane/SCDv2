package com.scd.logic.dungeon.room;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads Hypixel's dungeon map (the 128x128 map item in hotbar slot 9) into the 6x6 tile grid.
 *
 * <p>Each tile is a {@code roomSize} square (16 px, or 18 on Entrance-F3) with 4 px gaps. A tile's
 * top-left pixel carries the room colour; its centre pixel carries the checkmark (or the room
 * colour again when there is none). Tiles of the same multi-tile room are joined by filled pixels
 * in the gap along their top/left edge - doors are only drawn mid-edge, so an edge pixel means
 * "same room", not "connected by a door". The grid origin is found from the entrance tile's run of
 * pixels, falling back to per-floor defaults.
 */
public final class MapLayout {
	public static final int MAP = 128;
	private static final int SPACING = 4;

	public record Tile(RoomKind kind, Checkmark checkmark) {
	}

	/** A room as the map shows it: its tiles (grid indices, x + z * 6), colour and checkmark. */
	public record MapRoom(List<Integer> tiles, RoomKind kind, Checkmark checkmark) {
	}

	/** Door types by their map colour: wither doors are black (119), blood red (18), fairy pink (82). */
	public enum DoorType {
		NORMAL, WITHER, BLOOD, FAIRY;

		static DoorType fromColor(byte c) {
			return switch (c) {
				case 119 -> WITHER;
				case 18 -> BLOOD;
				case 82 -> FAIRY;
				default -> NORMAL;
			};
		}
	}

	/**
	 * A door between tile (x, z) and its east (horizontal) or south neighbour. World centre:
	 * horizontal x = centre(x) + 16, z = centre(z); vertical x = centre(x), z = centre(z) + 16.
	 */
	public record Door(int x, int z, boolean horizontal, DoorType type) {
		public int worldX() {
			return DungeonGrid.centre(x) + (horizontal ? 16 : 0);
		}

		public int worldZ() {
			return DungeonGrid.centre(z) + (horizontal ? 0 : 16);
		}
	}

	public record Layout(int roomSize, int startX, int startZ, Tile[] tiles, List<MapRoom> rooms, List<Door> doors) {
		public Tile tile(int x, int z) {
			return tiles[DungeonGrid.index(x, z)];
		}

		public MapRoom roomAt(int index) {
			for (MapRoom r : rooms) if (r.tiles.contains(index)) return r;
			return null;
		}
	}

	private MapLayout() {
	}

	/**
	 * @param colors  map colour bytes, row-major ({@code colors[x + z * 128]})
	 * @param floor   floor key ("F6", "M3", "E"), for the fallback origin; may be null
	 * @return null if this isn't a dungeon map (yet)
	 */
	public static Layout read(byte[] colors, String floor) {
		if (colors == null || colors.length < MAP * MAP || colors[0] != 0) return null;
		int[] origin = findOrigin(colors);
		if (origin == null) origin = defaults(floor);
		if (origin == null) return null;
		int size = origin[0], sx = origin[1], sz = origin[2];
		int gap = size + SPACING;
		int join = size + SPACING / 2;

		Tile[] tiles = new Tile[36];
		byte[] roomColor = new byte[36];
		for (int i = 0; i < 36; i++) {
			int ox = sx + (i % 6) * gap, oz = sz + (i / 6) * gap;
			byte corner = px(colors, ox, oz);
			RoomKind kind = corner == 0 ? null : RoomKind.fromMapColor(corner);
			if (kind == null) continue;
			byte centre = px(colors, ox + size / 2, oz + size / 2);
			Checkmark mark = centre == corner ? Checkmark.NONE : Checkmark.fromMapColor(centre);
			if (kind == RoomKind.UNKNOWN && mark == null) mark = Checkmark.QUESTION;
			tiles[i] = new Tile(kind, mark == null ? Checkmark.NONE : mark);
			roomColor[i] = corner;
		}

		// Doors: a filled pixel mid-edge in the gap with an empty pixel 4 to the side (a joined
		// multi-tile room fills the whole gap instead).
		List<Door> doors = new ArrayList<>();
		int half = size / 2;
		for (int i = 0; i < 36; i++) {
			int tx = i % 6, tz = i / 6;
			int ox = sx + tx * gap, oz = sz + tz * gap;
			if (tx < 5) {
				byte door = px(colors, ox + join, oz + half);
				if (door != 0 && px(colors, ox + join, oz + half - 4) == 0) doors.add(new Door(tx, tz, true, DoorType.fromColor(door)));
			}
			if (tz < 5) {
				byte door = px(colors, ox + half, oz + join);
				if (door != 0 && px(colors, ox + half - 4, oz + join) == 0) doors.add(new Door(tx, tz, false, DoorType.fromColor(door)));
			}
		}

		List<MapRoom> rooms = new ArrayList<>();
		boolean[] seen = new boolean[36];
		for (int start = 0; start < 36; start++) {
			if (tiles[start] == null || seen[start]) continue;
			List<Integer> group = new ArrayList<>();
			ArrayDeque<Integer> queue = new ArrayDeque<>();
			queue.add(start);
			seen[start] = true;
			while (!queue.isEmpty()) {
				int cur = queue.poll();
				group.add(cur);
				int cx = cur % 6, cz = cur / 6;
				int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
				for (int[] d : dirs) {
					int nx = cx + d[0], nz = cz + d[1];
					if (!DungeonGrid.inGrid(nx, nz)) continue;
					int ni = DungeonGrid.index(nx, nz);
					if (seen[ni] || tiles[ni] == null || roomColor[ni] != roomColor[cur]) continue;
					// The joining pixel lies in the gap after the lower-index tile of the pair.
					int ax = Math.min(cx, nx), az = Math.min(cz, nz);
					int jx = sx + ax * gap + (d[0] != 0 ? join : 0);
					int jz = sz + az * gap + (d[1] != 0 ? join : 0);
					if (px(colors, jx, jz) == 0) continue;
					seen[ni] = true;
					queue.add(ni);
				}
			}
			group.sort(null);
			Checkmark mark = Checkmark.NONE;
			for (int i : group) {
				Checkmark m = tiles[i].checkmark;
				if (m != Checkmark.NONE) mark = m;
			}
			rooms.add(new MapRoom(List.copyOf(group), tiles[start].kind, mark));
		}
		return new Layout(size, sx, sz, tiles, List.copyOf(rooms), List.copyOf(doors));
	}

	/** [roomSize, startX, startZ] from the entrance tile's pixel run; null if not visible. */
	static int[] findOrigin(byte[] colors) {
		byte entrance = RoomKind.ENTRANCE.mapColor;
		for (int i = 0; i < MAP * MAP; i++) {
			if (colors[i] != entrance) continue;
			int end = i;
			while (end < colors.length && colors[end] == entrance && end / MAP == i / MAP) end++;
			int len = end - i;
			if (len == 16 || len == 18) {
				int gap = len + SPACING;
				int sx = (i % MAP) % gap, sz = (i / MAP) % gap;
				if (sx == 0) sx = 22;
				if (sz == 0) sz = 22;
				return new int[]{len, sx, sz};
			}
			i = end - 1;
		}
		return null;
	}

	static int[] defaults(String floor) {
		if (floor == null) return null;
		int n = floor.equals("E") ? 0 : floor.length() == 2 ? floor.charAt(1) - '0' : -1;
		if (n < 0 || n > 7) return null;
		int size = n <= 3 ? 18 : 16;
		int sx = n <= 1 ? 22 : n <= 3 ? 11 : 5;
		int sz = n == 0 ? 22 : n == 4 ? 16 : n <= 3 ? 11 : 5;
		return new int[]{size, sx, sz};
	}

	private static byte px(byte[] colors, int x, int z) {
		return x >= 0 && x < MAP && z >= 0 && z < MAP ? colors[z * MAP + x] : 0;
	}
}
