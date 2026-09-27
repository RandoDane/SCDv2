package com.scd.client.feature.dungeon;

import com.scd.logic.dungeon.CompletionReport;

/** Events the dungeon feature publishes. */
public final class DungeonEvents {
	private DungeonEvents() {
	}

	/**
	 * A run finished. Fired once per real completion (Hypixel's two report blocks are merged:
	 * this fires on the first, the richest report is available via the dungeon feature afterwards).
	 */
	public record RunCompleted(CompletionReport report, long clearTimeMs) {
	}

	/** The player walked into a different room (null: left the room grid, e.g. into the boss). */
	public record RoomEntered(MappedRoom room) {
	}
}
