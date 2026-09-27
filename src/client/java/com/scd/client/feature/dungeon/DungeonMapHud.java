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
		for (MapLayout.Door d : layout.doors()) {
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
			if (room != null && room.info() != null && room.info().secrets() > 0) {
				int found = secrets.found(room);
				label = (secrets.inferred(room) ? "~" : "") + found + "/" + room.info().secrets();
				textColor = found >= room.info().secrets() ? 0xFF4ADE80 : 0xFFFFFFFF;
			}
			Checkmark mark = mr.checkmark();
			if (mark == Checkmark.GREEN || mark == Checkmark.WHITE || mark == Checkmark.RED) {
				int mc = mark == Checkmark.GREEN ? 0xFF4ADE80 : mark == Checkmark.RED ? 0xFFEF4444 : 0xFFFFFFFF;
				g.fill(cx - 2, cz - 7, cx + 2, cz - 3, mc);
			}
			if (label != null) Ui.centered(g, label, cx, cz - 1, textColor);
		}
		// Teammates (map markers) and you.
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

	private static void previewGrid(GuiGraphicsExtractor g, int x, int y) {
		RoomKind[] kinds = {RoomKind.ENTRANCE, RoomKind.NORMAL, RoomKind.NORMAL, RoomKind.PUZZLE, RoomKind.NORMAL, RoomKind.TRAP, RoomKind.NORMAL,
				RoomKind.FAIRY, RoomKind.NORMAL, RoomKind.CHAMPION, RoomKind.NORMAL, RoomKind.BLOOD};
		for (int i = 0; i < kinds.length; i++) {
			int rx = x + (i % 6) * CELL, rz = y + (i / 6) * CELL;
			g.fill(rx, rz, rx + ROOM, rz + ROOM, color(kinds[i]));
			if (kinds[i] == RoomKind.NORMAL) Ui.centered(g, (i % 3) + "/3", rx + ROOM / 2, rz + ROOM / 2 - 1, 0xFFFFFFFF);
		}
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
