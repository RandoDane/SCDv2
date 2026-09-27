package com.scd.client.feature.dungeon;

import com.scd.client.config.HudLayout;
import com.scd.client.config.ScdConfig;
import com.scd.client.hud.HudBox;
import com.scd.client.hud.HudColor;
import com.scd.client.hud.HudElement;
import com.scd.client.ui.Ui;
import com.scd.logic.dungeon.room.Checkmark;
import com.scd.logic.dungeon.room.DungeonGrid;
import com.scd.logic.dungeon.room.MapLayout;
import com.scd.logic.dungeon.room.RoomKind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.function.Supplier;

/**
 * SCD's own dungeon map: rooms coloured by type, multi-tile rooms joined, special doors, check
 * marks, secrets found/total per room ("~" when inferred from teammates) and player dots.
 */
final class DungeonMapHud extends HudElement {
	private static final int ROOM = 20, GAP = 4, CELL = ROOM + GAP, SIZE = 6 * CELL - GAP;

	private final Supplier<ScdConfig> config;
	private final DungeonFeature dungeon;
	private final SecretTracker secrets;

	DungeonMapHud(Supplier<ScdConfig> config, DungeonFeature dungeon, SecretTracker secrets) {
		super("dungeon_map", "Dungeon map", HudLayout.at(HudLayout.AnchorX.RIGHT, HudLayout.AnchorY.MIDDLE, 8, 0));
		this.config = config;
		this.dungeon = dungeon;
		this.secrets = secrets;
	}

	@Override
	public boolean enabled() {
		return config.get().dungeon.mapHud;
	}

	@Override
	public HudBox build(boolean preview) {
		var layout = dungeon.rooms().layout();
		if (!preview && (layout == null || !dungeon.state().inDungeon() || dungeon.scoreInBoss())) return null;
		HudBox box = new HudBox().minWidth(SIZE);
		box.custom(SIZE, SIZE, (g, x, y, w, h) -> {
			if (layout != null) draw(g, x, y, layout);
			else previewGrid(g, x, y);
		});
		int unassigned = preview ? 0 : secrets.unassigned();
		if (unassigned > 0) box.text("+" + unassigned + " secret" + (unassigned > 1 ? "s" : "") + " not placed", HudColor.LABEL);
		return box;
	}

	private void draw(GuiGraphicsExtractor g, int x, int y, MapLayout.Layout layout) {
		// Rooms and the joins between tiles of the same room.
		for (int i = 0; i < 36; i++) {
			var tile = layout.tiles()[i];
			if (tile == null) continue;
			int tx = i % 6, tz = i / 6;
			int color = color(tile.kind());
			int rx = x + tx * CELL, rz = y + tz * CELL;
			g.fill(rx, rz, rx + ROOM, rz + ROOM, color);
			var room = layout.roomAt(i);
			if (room != null && tx < 5 && room.tiles().contains(i + 1)) g.fill(rx + ROOM, rz, rx + CELL, rz + ROOM, color);
			if (room != null && tz < 5 && room.tiles().contains(i + 6)) g.fill(rx, rz + ROOM, rx + ROOM, rz + CELL, color);
			if (room != null && tx < 5 && tz < 5 && room.tiles().containsAll(java.util.List.of(i + 1, i + 6, i + 7))) {
				g.fill(rx + ROOM, rz + ROOM, rx + CELL, rz + CELL, color);
			}
		}
		var c = config.get().dungeon;
		if (c.mapDoors) for (MapLayout.Door d : layout.doors()) {
			int cx = x + d.x() * CELL + ROOM / 2, cz = y + d.z() * CELL + ROOM / 2;
			int dc = switch (d.type()) {
				case WITHER -> 0xFF111111;
				case BLOOD -> 0xFFDC2626;
				case FAIRY -> 0xFFEC4899;
				default -> 0xFF8B6B4A;
			};
			if (d.horizontal()) g.fill(cx + ROOM / 2, cz - 2, cx + ROOM / 2 + GAP, cz + 2, dc);
			else g.fill(cx - 2, cz + ROOM / 2, cx + 2, cz + ROOM / 2 + GAP, dc);
		}
		// Per-room check marks and secret counts, centred on the room's first tile.
		for (MapLayout.MapRoom mr : layout.rooms()) {
			int first = mr.tiles().getFirst();
			int cx = x + (first % 6) * CELL + ROOM / 2, cz = y + (first / 6) * CELL + ROOM / 2;
			MappedRoom room = dungeon.rooms().roomAtTile(first);
			String label = null;
			int textColor = 0xFFFFFFFF;
			if (c.mapSecrets && room != null && room.info() != null && room.info().secrets() > 0) {
				int found = secrets.found(room);
				label = (secrets.inferred(room) ? "~" : "") + found + "/" + room.info().secrets();
				textColor = found >= room.info().secrets() ? 0xFF4ADE80 : 0xFFFFFFFF;
			}
			Checkmark mark = mr.checkmark();
			if (c.mapChecks && (mark == Checkmark.GREEN || mark == Checkmark.WHITE || mark == Checkmark.RED)) {
				int mc = mark == Checkmark.GREEN ? 0xFF4ADE80 : mark == Checkmark.RED ? 0xFFEF4444 : 0xFFFFFFFF;
				g.fill(cx - 2, cz - 7, cx + 2, cz - 3, mc);
			}
			if (label != null) Ui.centered(g, label, cx, cz - 1, textColor);
		}
		// Teammates (map markers) and you.
		if (!c.mapPlayers) return;
		int gap = layout.roomSize() + 4;
		for (int[] m : dungeon.rooms().teammateMarks()) {
			float fx = (m[0] - layout.startX()) / (float) gap, fz = (m[1] - layout.startZ()) / (float) gap;
			dot(g, x + Math.round(fx * CELL), y + Math.round(fz * CELL), 0xFF60A5FA);
		}
		var p = Minecraft.getInstance().player;
		if (p != null) {
			float fx = (float) ((p.getX() - DungeonGrid.ORIGIN) / DungeonGrid.TILE), fz = (float) ((p.getZ() - DungeonGrid.ORIGIN) / DungeonGrid.TILE);
			dot(g, x + Math.round(fx * CELL), y + Math.round(fz * CELL), 0xFF4ADE80);
		}
	}

	private static void dot(GuiGraphicsExtractor g, int x, int y, int color) {
		g.fill(x - 2, y - 2, x + 2, y + 2, 0xFF000000);
		g.fill(x - 1, y - 1, x + 1, y + 1, color);
	}

	/**
	 * An example floor for the HUD editor, drawn like a real run: multi-tile rooms, every room type,
	 * doors, check marks, secret counts, unexplored rooms and player dots.
	 */
	private static final String[] PREVIEW = {
			"EaabPc",
			"dffbgc",
			"dffhgc",
			"Tijjjj",
			"kiYlmn",
			"kFoomB"};
	/** Doors as tile pairs (index = z * 6 + x) with a type: n normal, w wither, b blood. */
	private static final String[] PREVIEW_DOORS = {"0-1n", "0-6n", "2-3n", "3-4n", "9-10n", "10-11n", "6-7n", "14-15w", "12-18n",
			"18-19n", "19-20w", "20-26n", "25-24n", "30-31n", "31-32n", "21-27n", "27-28n", "28-29n", "29-35b", "17-23n"};

	private void previewGrid(GuiGraphicsExtractor g, int x, int y) {
		for (int i = 0; i < 36; i++) {
			char id = PREVIEW[i / 6].charAt(i % 6);
			int tx = i % 6, tz = i / 6, color = color(previewKind(id));
			int rx = x + tx * CELL, rz = y + tz * CELL;
			g.fill(rx, rz, rx + ROOM, rz + ROOM, color);
			boolean right = tx < 5 && PREVIEW[tz].charAt(tx + 1) == id, down = tz < 5 && PREVIEW[tz + 1].charAt(tx) == id;
			if (right) g.fill(rx + ROOM, rz, rx + CELL, rz + ROOM, color);
			if (down) g.fill(rx, rz + ROOM, rx + ROOM, rz + CELL, color);
			if (right && down && PREVIEW[tz + 1].charAt(tx + 1) == id) g.fill(rx + ROOM, rz + ROOM, rx + CELL, rz + CELL, color);
		}
		var c = config.get().dungeon;
		if (c.mapDoors) for (String d : PREVIEW_DOORS) {
			String[] ab = d.substring(0, d.length() - 1).split("-");
			int t1 = Math.min(Integer.parseInt(ab[0]), Integer.parseInt(ab[1])), t2 = Math.max(Integer.parseInt(ab[0]), Integer.parseInt(ab[1]));
			int dc = switch (d.charAt(d.length() - 1)) {
				case 'w' -> 0xFF111111;
				case 'b' -> 0xFFDC2626;
				default -> 0xFF8B6B4A;
			};
			int cx = x + (t1 % 6) * CELL + ROOM / 2, cz = y + (t1 / 6) * CELL + ROOM / 2;
			if (t2 - t1 == 1) g.fill(cx + ROOM / 2, cz - 2, cx + ROOM / 2 + GAP, cz + 2, dc);
			else g.fill(cx - 2, cz + ROOM / 2, cx + 2, cz + ROOM / 2 + GAP, dc);
		}
		// Check marks and secrets on each room's first tile: id -> check (g/w/-), found, total.
		String[][] rooms = {{"a", "g", "3", "3"}, {"b", "w", "1", "4"}, {"c", "w", "2", "5"}, {"d", "g", "2", "2"}, {"f", "w", "4", "7"},
				{"g", "-", "0", "3"}, {"h", "g", "1", "1"}, {"i", "w", "1", "2"}, {"j", "-", "0", "6"}, {"k", "-", "0", "2"},
				{"l", "-", "0", "1"}, {"P", "g", "0", "0"}, {"T", "w", "0", "0"}, {"E", "g", "0", "0"}};
		for (String[] r : rooms) {
			int first = -1;
			for (int i = 0; i < 36 && first < 0; i++) if (PREVIEW[i / 6].charAt(i % 6) == r[0].charAt(0)) first = i;
			int cx = x + (first % 6) * CELL + ROOM / 2, cz = y + (first / 6) * CELL + ROOM / 2;
			if (c.mapChecks && !r[1].equals("-")) g.fill(cx - 2, cz - 7, cx + 2, cz - 3, r[1].equals("g") ? 0xFF4ADE80 : 0xFFFFFFFF);
			int total = Integer.parseInt(r[3]), found = Integer.parseInt(r[2]);
			if (c.mapSecrets && total > 0) Ui.centered(g, found + "/" + total, cx, cz - 1, found >= total ? 0xFF4ADE80 : 0xFFFFFFFF);
		}
		if (!c.mapPlayers) return;
		dot(g, x + 3 * CELL + CELL / 2, y + 3 * CELL + ROOM / 2, 0xFF4ADE80);
		dot(g, x + 1 * CELL + 4, y + 1 * CELL + ROOM + 2, 0xFF60A5FA);
		dot(g, x + 5 * CELL + ROOM / 2, y + 1 * CELL + 6, 0xFF60A5FA);
	}

	private static RoomKind previewKind(char id) {
		return switch (id) {
			case 'E' -> RoomKind.ENTRANCE;
			case 'P' -> RoomKind.PUZZLE;
			case 'T' -> RoomKind.TRAP;
			case 'Y' -> RoomKind.CHAMPION;
			case 'F' -> RoomKind.FAIRY;
			case 'B' -> RoomKind.BLOOD;
			case 'm', 'n', 'o' -> RoomKind.UNKNOWN;
			default -> RoomKind.NORMAL;
		};
	}

	static int color(RoomKind kind) {
		return switch (kind) {
			case ENTRANCE -> 0xFF15803D;
			case FAIRY -> 0xFFDB2777;
			case BLOOD -> 0xFFB91C1C;
			case CHAMPION -> 0xFFCA8A04;
			case PUZZLE -> 0xFF7E22CE;
			case TRAP -> 0xFFEA580C;
			case UNKNOWN -> 0xFF4B5563;
			default -> 0xFF7C5A3A;
		};
	}
}
