package com.scd.client.feature.dungeon;

import com.scd.client.ScdMod;
import com.scd.client.core.Events;
import com.scd.client.core.ScdLog;
import com.scd.logic.dungeon.room.Checkmark;
import com.scd.logic.dungeon.room.DungeonGrid;
import net.minecraft.world.entity.ambient.Bat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Secrets found per room, for every room - including ones teammates clear far away.
 *
 * <p>Truth sources: the action bar ("3/5 Secrets") is exact for the room you stand in, and a green
 * map checkmark means all of a room's secrets are done. For everything else the tab's party total
 * ("Secrets Found: N") says how many were found; each new one is attributed to a room by
 * evidence - a wither-essence skull vanishing, another player picking up an item, a bat dying -
 * or, failing that, to the only unfinished room that has a teammate in it. What can't be placed
 * stays "unassigned" instead of being guessed into the wrong room.
 */
final class SecretTracker {
	private static final Pattern ACTION = Pattern.compile("(\\d+)/(\\d+) Secrets");
	private static final Pattern TAB_COUNT = Pattern.compile("^\\s*Secrets Found:\\s*(\\d+)\\s*$");
	private static final long EVIDENCE_TTL_MS = 4_000;
	private static final long TEAMMATE_GUESS_AFTER_MS = 3_000;

	static final class RoomSecrets {
		int found;
		/** Set by the action bar or a green check; inferred counts may be corrected. */
		boolean exact;
		boolean inferred;
	}

	private record Evidence(int tile, long at, String source) {
	}

	private final ScdMod mod;
	private final DungeonFeature dungeon;
	private final Map<MappedRoom, RoomSecrets> rooms = new IdentityHashMap<>();
	private final Deque<Evidence> evidence = new ArrayDeque<>();
	private int tabTotal = -1;
	private long unassignedSince;

	SecretTracker(ScdMod mod, DungeonFeature dungeon) {
		this.mod = mod;
		this.dungeon = dungeon;
		mod.bus.subscribe(Events.ChatReceived.class, e -> {
			if (e.channel() != Events.ChatReceived.Channel.ACTION_BAR || !dungeon.state().inDungeon()) return;
			Matcher m = ACTION.matcher(e.clean());
			if (!m.find()) return;
			MappedRoom here = dungeon.rooms().current();
			if (here == null) return;
			RoomSecrets s = of(here);
			s.found = Integer.parseInt(m.group(1));
			s.exact = true;
			s.inferred = false;
		});
		mod.bus.subscribe(Events.SkullRemoved.class, e -> evidence(e.pos().getX(), e.pos().getZ(), "skull"));
		mod.bus.subscribe(Events.ItemTakenByOther.class, e -> evidence((int) Math.floor(e.pos().x), (int) Math.floor(e.pos().z), "item"));
		mod.bus.subscribe(Events.EntityDied.class, e -> {
			if (e.entity() instanceof Bat b) evidence(b.getBlockX(), b.getBlockZ(), "bat");
		});
		mod.bus.subscribe(Events.Tick.class, e -> {
			if (!dungeon.state().inDungeon()) {
				if (!rooms.isEmpty() || tabTotal >= 0) reset();
				return;
			}
			if (mod.tasks.currentTick() % 4 == 0) update();
		});
	}

	private void reset() {
		rooms.clear();
		evidence.clear();
		tabTotal = -1;
		unassignedSince = 0;
	}

	private RoomSecrets of(MappedRoom r) {
		return rooms.computeIfAbsent(r, k -> new RoomSecrets());
	}

	private void evidence(int x, int z, String source) {
		if (!dungeon.state().inDungeon()) return;
		int tx = DungeonGrid.tileOf(x), tz = DungeonGrid.tileOf(z);
		if (!DungeonGrid.inGrid(tx, tz)) return;
		evidence.addLast(new Evidence(DungeonGrid.index(tx, tz), System.currentTimeMillis(), source));
	}

	private void update() {
		long now = System.currentTimeMillis();
		while (!evidence.isEmpty() && now - evidence.peekFirst().at() > EVIDENCE_TTL_MS) evidence.removeFirst();
		// Green check: everything in that room is done.
		for (MappedRoom r : dungeon.rooms().rooms()) {
			if (r.info() == null || r.checkmark() != Checkmark.GREEN) continue;
			RoomSecrets s = of(r);
			s.found = r.info().secrets();
			s.exact = true;
			s.inferred = false;
		}
		int total = tabCount();
		if (total < 0) return;
		tabTotal = total;
		int pool = unassigned();
		// Place new finds by evidence first.
		var it = evidence.iterator();
		while (pool > 0 && it.hasNext()) {
			Evidence ev = it.next();
			MappedRoom r = dungeon.rooms().roomAtTile(ev.tile());
			if (r == null || r.info() == null || r == dungeon.rooms().current()) continue;
			RoomSecrets s = of(r);
			if (s.found >= r.info().secrets()) continue;
			s.found++;
			s.inferred = true;
			pool--;
			it.remove();
			ScdLog.debug("[secrets] +1 " + r.label() + " from " + ev.source());
		}
		if (pool <= 0) {
			unassignedSince = 0;
			return;
		}
		if (unassignedSince == 0) unassignedSince = now;
		if (now - unassignedSince < TEAMMATE_GUESS_AFTER_MS) return;
		// No evidence: the only unfinished room with a teammate in it, if there is exactly one.
		MappedRoom only = null;
		int candidates = 0;
		for (int[] mark : dungeon.rooms().teammateMarks()) {
			MappedRoom r = dungeon.rooms().roomAtTile(dungeon.rooms().tileAtMapPixel(mark[0], mark[1]));
			if (r == null || r.info() == null || r == dungeon.rooms().current()) continue;
			if (of(r).found >= r.info().secrets()) continue;
			if (r != only) {
				candidates++;
				only = r;
			}
		}
		if (candidates == 1) {
			RoomSecrets s = of(only);
			s.found++;
			s.inferred = true;
			unassignedSince = 0;
			ScdLog.debug("[secrets] +1 " + only.label() + " (only teammate room)");
		}
	}

	/** Party total from the tab list, or -1. */
	private int tabCount() {
		for (String line : mod.game.tabList()) {
			Matcher m = TAB_COUNT.matcher(line);
			if (m.matches()) return Integer.parseInt(m.group(1));
		}
		return -1;
	}

	/** Finds the tab counts that aren't placed in any room yet. */
	int unassigned() {
		if (tabTotal < 0) return 0;
		int placed = 0;
		for (RoomSecrets s : rooms.values()) placed += s.found;
		return Math.max(0, tabTotal - placed);
	}

	/** Found secrets in a room (0 if nothing known). */
	int found(MappedRoom r) {
		RoomSecrets s = rooms.get(r);
		return s != null ? s.found : 0;
	}

	boolean inferred(MappedRoom r) {
		RoomSecrets s = rooms.get(r);
		return s != null && s.inferred && !s.exact;
	}

	/** Identified rooms with secrets still missing, most remaining first. */
	List<MappedRoom> unfinished() {
		List<MappedRoom> out = new ArrayList<>();
		for (MappedRoom r : dungeon.rooms().rooms()) {
			if (r.info() == null || r.info().secrets() == 0) continue;
			if (found(r) < r.info().secrets()) out.add(r);
		}
		out.sort((a, b) -> Integer.compare(b.info().secrets() - found(b), a.info().secrets() - found(a)));
		return out;
	}

}
