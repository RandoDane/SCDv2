package com.scd.logic.dungeon.room;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RoomEngineTest {
	@Test
	void emptyColumnMatchesOdinsIgnoredCore() {
		assertEquals(RoomCore.EMPTY, RoomCore.compute(y -> null).core());
		assertTrue(RoomCore.compute(y -> "minecraft:gold_block").empty(), "gold above the roof is treated like air");
	}

	@Test
	void coreHashFollowsOdinsStringLayout() {
		// Roof at 100: stone, then an air gap, planks (skipped), two bedrock and air below 69 -> padding.
		RoomCore.Result r = RoomCore.compute(y -> {
			if (y == 100) return "minecraft:stone";
			if (y == 99) return "minecraft:oak_planks";
			if (y == 60 || y == 59) return "minecraft:bedrock";
			if (y > 100 || y < 59) return null;
			return "minecraft:air";
		});
		StringBuilder sb = new StringBuilder("0".repeat(40)).append("Block{minecraft:stone}");
		for (int y = 98; y >= 61; y--) sb.append("Block{minecraft:air}");
		sb.append("Block{minecraft:bedrock}Block{minecraft:bedrock}").append("0".repeat(58 - 11));
		assertEquals(sb.toString().hashCode(), r.core());
		assertEquals(100, r.highestBlock());
	}

	@Test
	void rotationFramesAreInverse() {
		for (RoomRotation rot : RoomRotation.values()) {
			int[] rel = rot.toRelative(7, -3);
			int[] back = rot.toWorld(rel[0], rel[1]);
			assertArrayEquals(new int[]{7, -3}, back, rot.name());
		}
		// The tile centre, seen from each rotation's clay, is the same room-relative spot.
		for (RoomRotation rot : RoomRotation.values()) {
			assertArrayEquals(new int[]{15, 15}, rot.toRelative(-rot.dx, -rot.dz), rot.name());
		}
	}

	@Test
	void sameRoomPointMapsToSameRelativeCoordsInEveryRotation() {
		// A point 3 blocks "into" the room from its clay corner, in each orientation of a 1x1 at tile (2,2).
		for (RoomRotation rot : RoomRotation.values()) {
			var a = RoomPlacement.oneByOne(2, 2, rot);
			int[] off = rot.toWorld(3, 5);
			int[] rel = rot.toRelative(off[0], off[1]);
			assertArrayEquals(new int[]{3, 5}, rel);
			int cx = DungeonGrid.centre(2);
			assertEquals(15, Math.abs(a.x() - cx));
		}
	}

	@Test
	void geometryRotations() {
		assertEquals(RoomRotation.SOUTH, RoomPlacement.fromGeometry(List.of(new int[]{1, 1}, new int[]{2, 1}), RoomShape.ONE_BY_TWO).rotation());
		var vertical = RoomPlacement.fromGeometry(List.of(new int[]{1, 1}, new int[]{1, 2}, new int[]{1, 3}), RoomShape.ONE_BY_THREE);
		assertEquals(RoomRotation.WEST, vertical.rotation());
		assertEquals(DungeonGrid.centre(1) + 15, vertical.x());
		assertEquals(DungeonGrid.centre(1) - 15, vertical.z());
		// L missing NE -> SOUTH with clay at NW.
		var south = RoomPlacement.fromGeometry(List.of(new int[]{0, 0}, new int[]{0, 1}, new int[]{1, 1}), RoomShape.L);
		assertEquals(RoomRotation.SOUTH, south.rotation());
		assertEquals(DungeonGrid.centre(0) - 15, south.x());
		assertEquals(RoomRotation.WEST, RoomPlacement.fromGeometry(List.of(new int[]{0, 0}, new int[]{1, 0}, new int[]{0, 1}), RoomShape.L).rotation());
		assertEquals(RoomRotation.NORTH, RoomPlacement.fromGeometry(List.of(new int[]{0, 0}, new int[]{1, 0}, new int[]{1, 1}), RoomShape.L).rotation());
		var east = RoomPlacement.fromGeometry(List.of(new int[]{1, 0}, new int[]{0, 1}, new int[]{1, 1}), RoomShape.L);
		assertEquals(RoomRotation.EAST, east.rotation());
		assertEquals(DungeonGrid.centre(0) - 15, east.x());
		assertEquals(DungeonGrid.centre(1) + 15, east.z());
		assertFalse(RoomPlacement.corners(List.of(new int[]{1, 0}, new int[]{0, 1}, new int[]{1, 1})).containsKey(RoomRotation.SOUTH));
		assertNull(RoomPlacement.fromGeometry(List.of(new int[]{0, 0}), RoomShape.ONE_BY_ONE));
	}

	@Test
	void gridMatchesChunkColumns() {
		for (int t = 0; t < 6; t++) {
			assertEquals(DungeonGrid.centre(t), DungeonGrid.chunkOf(t) * 16 + 7);
			assertEquals(t, DungeonGrid.tileOf(DungeonGrid.centre(t)));
			assertEquals(t, DungeonGrid.tileOf(DungeonGrid.centre(t) - 15));
			assertEquals(t, DungeonGrid.tileOf(DungeonGrid.centre(t) + 15));
		}
	}

	@Test
	void databaseLoadsOdinFormat() {
		RoomDatabase db = new RoomDatabase();
		int n = db.load(new StringReader("[{\"name\":\"Altar\",\"type\":\"NORMAL\",\"cores\":[1,2],\"crypts\":3,\"maxSecrets\":6,\"shape\":\"L\"},"
				+ "{\"name\":\"Bad\",\"type\":\"WHAT\",\"cores\":[3],\"shape\":\"1x1\"}]"));
		assertEquals(1, n);
		RoomInfo altar = db.byCore(2);
		assertEquals("Altar", altar.name());
		assertEquals(RoomShape.L, altar.shape());
		assertEquals(6, altar.secrets());
		assertNull(db.byCore(3));
	}

	@Test
	void mapLayoutFindsRoomsShapesAndCheckmarks() {
		// F7-style grid: 16px rooms, origin (5,5), gap 20.
		byte[] map = new byte[128 * 128];
		int size = 16, gap = 20, sx = 5, sz = 5;
		paintTile(map, sx, sz, gap, size, 0, 0, RoomKind.ENTRANCE.mapColor);
		paintTile(map, sx, sz, gap, size, 1, 0, RoomKind.NORMAL.mapColor);
		paintTile(map, sx, sz, gap, size, 2, 0, RoomKind.NORMAL.mapColor);
		// join (1,0)-(2,0) into one 1x2 room: fill the 4px gap along the row
		for (int x = sx + gap + size; x < sx + 2 * gap; x++) for (int z = sz; z < sz + size; z++) map[z * 128 + x] = RoomKind.NORMAL.mapColor;
		paintTile(map, sx, sz, gap, size, 4, 4, RoomKind.PUZZLE.mapColor);
		map[(sz + 4 * gap + 8) * 128 + sx + 4 * gap + 8] = 30; // green check on the puzzle
		map[(sz + 8) * 128 + sx + gap + 8] = 34; // white check on the 1x2
		// A door between entrance and the 1x2: only mid-edge pixels, must not merge rooms
		for (int z = sz + 6; z < sz + 10; z++) for (int x = sx + size; x < sx + gap; x++) map[z * 128 + x] = RoomKind.NORMAL.mapColor;

		var layout = MapLayout.read(map, "F7");
		assertNotNull(layout);
		assertEquals(16, layout.roomSize());
		assertEquals(5, layout.startX());
		assertEquals(3, layout.rooms().size());
		var twoTile = layout.roomAt(1);
		assertEquals(List.of(1, 2), twoTile.tiles());
		assertEquals(Checkmark.WHITE, twoTile.checkmark());
		assertEquals(Checkmark.GREEN, layout.roomAt(DungeonGrid.index(4, 4)).checkmark());
		assertEquals(RoomKind.ENTRANCE, layout.roomAt(0).kind());
		assertNull(MapLayout.read(new byte[10], "F7"));
	}

	private static void paintTile(byte[] map, int sx, int sz, int gap, int size, int tx, int tz, byte color) {
		for (int x = 0; x < size; x++)
			for (int z = 0; z < size; z++) map[(sz + tz * gap + z) * 128 + sx + tx * gap + x] = color;
	}
}
