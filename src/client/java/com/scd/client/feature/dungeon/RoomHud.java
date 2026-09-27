package com.scd.client.feature.dungeon;

import com.scd.client.config.HudLayout;
import com.scd.client.config.ScdConfig;
import com.scd.client.hud.HudBox;
import com.scd.client.hud.HudColor;
import com.scd.client.hud.HudElement;
import com.scd.logic.dungeon.room.Checkmark;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.Locale;
import java.util.function.Supplier;

/** The room the player is in: name, secrets, crypts; with debug on also its frame and your relative position. */
final class RoomHud extends HudElement {
	private final Supplier<ScdConfig> config;
	private final Supplier<MappedRoom> room;

	RoomHud(Supplier<ScdConfig> config, Supplier<MappedRoom> room) {
		super("dungeon_room", "Dungeon room", HudLayout.at(HudLayout.AnchorX.RIGHT, HudLayout.AnchorY.TOP, 8, 8));
		this.config = config;
		this.room = room;
	}

	@Override
	public boolean enabled() {
		return config.get().dungeon.roomHud;
	}

	@Override
	public HudBox build(boolean preview) {
		boolean debug = config.get().dungeon.roomDebug;
		HudBox box = new HudBox().minWidth(120);
		if (preview) {
			box.title("Altar").text("6 secrets · 3 crypts", HudColor.TEXT);
			if (debug) box.text("WEST (block) · rel 4 71 -9", HudColor.LABEL);
			return box;
		}
		MappedRoom r = room.get();
		if (r == null) return null;
		box.title(r.label());
		if (r.info() != null) {
			StringBuilder sb = new StringBuilder();
			sb.append(r.info().secrets()).append(r.info().secrets() == 1 ? " secret" : " secrets");
			if (r.info().crypts() > 0) sb.append(" · ").append(r.info().crypts()).append(" crypts");
			if (r.checkmark() == Checkmark.GREEN) sb.append(" · done");
			else if (r.checkmark() == Checkmark.WHITE) sb.append(" · cleared");
			box.text(sb.toString(), HudColor.TEXT);
		}
		if (debug) {
			var a = r.anchor();
			var player = Minecraft.getInstance().player;
			if (a == null) {
				box.text("no anchor yet", HudColor.LABEL);
			} else if (player != null) {
				BlockPos rel = r.toRelative(player.blockPosition());
				box.text(a.rotation().name() + " (" + r.anchorSource + ") · rel " + rel.getX() + " " + rel.getY() + " " + rel.getZ(), HudColor.LABEL);
			}
			if (r.core != null) box.text("core " + r.core + (r.kind() != null ? " · " + r.kind().name().toLowerCase(Locale.ROOT) : ""), HudColor.LABEL);
		}
		return box;
	}
}
