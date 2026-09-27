package com.scd.client.feature.dungeon;

import com.scd.client.ScdMod;
import com.scd.client.core.Events;
import com.scd.client.core.ScdLog;
import com.scd.client.storage.ScdPaths;
import com.scd.logic.dungeon.room.RoomLearner;
import com.scd.logic.dungeon.room.RoomShape;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds SCD's own room list while you play (see {@link RoomLearner}): each room you walk into is
 * recorded with its core, name, type and shape, and the action bar's "x/y Secrets" gives its secret
 * total. Saved to config/scd/dungeon/rooms_learned.json, which the room database loads over the
 * bundled list, so SCD's own observations win.
 */
final class RoomLearning {
	static final Path FILE = ScdPaths.file("dungeon/rooms_learned.json");
	private static final Pattern ACTION = Pattern.compile("(\\d+)/(\\d+) Secrets");

	private final DungeonFeature dungeon;
	private final RoomLearner learner = new RoomLearner();
	private boolean dirty;

	RoomLearning(ScdMod mod, DungeonFeature dungeon) {
		this.dungeon = dungeon;
		if (Files.isRegularFile(FILE)) {
			try (Reader r = Files.newBufferedReader(FILE)) {
				learner.load(r);
			} catch (Exception e) {
				ScdLog.warn("Learned room file " + FILE + " is invalid", e);
			}
		}
		mod.bus.subscribe(DungeonEvents.RoomEntered.class, e -> {
			if (e.room() != null) observe(e.room(), null, true);
		});
		mod.bus.subscribe(Events.ChatReceived.class, e -> {
			if (e.channel() != Events.ChatReceived.Channel.ACTION_BAR || !dungeon.state().inDungeon()) return;
			Matcher m = ACTION.matcher(e.clean());
			MappedRoom here = dungeon.rooms().current();
			if (m.find() && here != null) observe(here, Integer.parseInt(m.group(2)), false);
		});
		// Save between rooms rather than on every action bar update.
		mod.bus.subscribe(Events.Tick.class, e -> {
			if (dirty && mod.tasks.currentTick() % 200 == 0) save();
		});
		mod.bus.subscribe(Events.WorldChanged.class, e -> {
			if (dirty) save();
		});
	}

	private void observe(MappedRoom room, Integer secrets, boolean visit) {
		if (room.core == null) return;
		var info = room.info();
		RoomShape shape = info != null ? info.shape() : shapeOf(room);
		boolean isNew = learner.byCore(room.core) == null;
		if (learner.observe(room.core, room.name(), room.kind(), shape, info != null ? info.crypts() : 0, secrets, visit)) {
			dirty = true;
			if (isNew) ScdLog.info("[rooms] learned " + learner.byCore(room.core).name + " (core " + room.core + ")");
		}
	}

	private static RoomShape shapeOf(MappedRoom room) {
		if (!room.complete()) return null;
		int minX = 9, maxX = -1, minZ = 9, maxZ = -1;
		for (int[] t : room.tiles()) {
			minX = Math.min(minX, t[0]);
			maxX = Math.max(maxX, t[0]);
			minZ = Math.min(minZ, t[1]);
			maxZ = Math.max(maxZ, t[1]);
		}
		return RoomShape.fromTiles(room.tiles().size(), maxX - minX + 1, maxZ - minZ + 1);
	}

	private void save() {
		dirty = false;
		try {
			Files.createDirectories(FILE.getParent());
			Files.writeString(FILE, learner.toJson());
		} catch (Exception e) {
			ScdLog.warn("Could not save learned rooms", e);
		}
	}

	RoomLearner learner() {
		return learner;
	}
}
