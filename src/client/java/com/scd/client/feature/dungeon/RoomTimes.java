package com.scd.client.feature.dungeon;

import com.scd.client.ScdMod;
import com.scd.client.core.Chat;
import com.scd.client.core.Events;
import com.scd.client.core.ServerLag;
import com.scd.logic.Numbers;
import com.scd.logic.dungeon.room.RoomKind;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * How long each room takes you: from first stepping in until the map marks it cleared (white or
 * green check). Kept per room name (best + recent history) so slow rooms stand out; server lag
 * during the clear is subtracted before comparing.
 */
final class RoomTimes {
	private static final int KEPT = 30;

	private final ScdMod mod;
	private final DungeonFeature dungeon;
	private final Map<MappedRoom, long[]> entered = new IdentityHashMap<>(); // {wallMs, lagSnapshot}

	RoomTimes(ScdMod mod, DungeonFeature dungeon) {
		this.mod = mod;
		this.dungeon = dungeon;
		mod.bus.subscribe(DungeonEvents.RoomEntered.class, e -> {
			MappedRoom r = e.room();
			if (r == null || r.name() == null || r.checkmark().cleared() || entered.containsKey(r)) return;
			RoomKind k = r.kind();
			if (k == RoomKind.ENTRANCE || k == RoomKind.BLOOD || k == RoomKind.FAIRY) return;
			entered.put(r, new long[]{System.currentTimeMillis(), ServerLag.lostMs()});
		});
		mod.bus.subscribe(Events.Tick.class, e -> {
			if (!dungeon.state().inDungeon()) {
				entered.clear();
				return;
			}
			if (mod.tasks.currentTick() % 5 == 0) check();
		});
	}

	private void check() {
		var it = entered.entrySet().iterator();
		while (it.hasNext()) {
			var e = it.next();
			MappedRoom room = e.getKey();
			if (!room.checkmark().cleared()) continue;
			// Read before removing: an IdentityHashMap entry is invalid once removed.
			long[] started = e.getValue();
			it.remove();
			long raw = System.currentTimeMillis() - started[0];
			long lag = Math.min(raw, ServerLag.since(started[1]));
			record(room.name(), raw - lag, lag);
		}
	}

	private void record(String name, long ms, long lag) {
		var data = dungeon.records();
		List<Long> hist = data.roomTimes.computeIfAbsent(name, k -> new ArrayList<>());
		Long best = hist.stream().min(Long::compare).orElse(null);
		double avg = hist.stream().mapToLong(Long::longValue).average().orElse(0);
		hist.add(ms);
		while (hist.size() > KEPT) hist.removeFirst();
		dungeon.recordsDirty();
		var c = mod.config().dungeon;
		if (!c.roomTimeMessage) return;
		boolean pb = best != null && ms < best;
		if (c.roomTimeOnlyPb && !pb) return;
		StringBuilder sb = new StringBuilder(name + " cleared in " + Numbers.durationTenths(ms));
		if (c.roomTimeLag && lag >= 300) sb.append(" (+").append(Numbers.durationTenths(lag)).append(" lag)");
		if (c.roomTimePb && best != null) sb.append(" · PB ").append(Numbers.durationTenths(Math.min(best, ms)));
		if (c.roomTimeAvg && avg > 0) sb.append(" · avg ").append(Numbers.durationTenths(Math.round(avg)));
		Chat.info(Component.literal(sb.toString()).withStyle(pb ? ChatFormatting.GOLD : ChatFormatting.GRAY));
	}

	/** Live pace in a room being timed: "0:14.2 · -3.8s vs PB" (or just the time without a PB); null otherwise. */
	String live(MappedRoom room) {
		long[] started = room != null ? entered.get(room) : null;
		if (started == null) return null;
		long raw = System.currentTimeMillis() - started[0];
		long ms = raw - Math.min(raw, ServerLag.since(started[1]));
		List<Long> hist = dungeon.records().roomTimes.get(room.name());
		String now = Numbers.durationTenths(ms);
		if (hist == null || hist.isEmpty()) return now;
		long best = hist.stream().min(Long::compare).orElse(0L);
		long diff = ms - best;
		return now + " · " + (diff <= 0 ? "-" : "+") + String.format(java.util.Locale.ROOT, "%.1fs", Math.abs(diff) / 1000.0) + " vs PB";
	}

	/** "PB 0:23.1 · avg 0:31.0" for a room, or null without history. */
	String summary(String name) {
		List<Long> hist = dungeon.records().roomTimes.get(name);
		if (hist == null || hist.isEmpty()) return null;
		long best = hist.stream().min(Long::compare).orElse(0L);
		double avg = hist.stream().mapToLong(Long::longValue).average().orElse(0);
		return "PB " + Numbers.durationTenths(best) + " · avg " + Numbers.durationTenths(Math.round(avg)) + " (" + hist.size() + ")";
	}
}
