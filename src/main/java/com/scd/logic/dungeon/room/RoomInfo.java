package com.scd.logic.dungeon.room;

import java.util.List;

/** One entry of the room database: a room identified by any of its core hashes. */
public record RoomInfo(String name, RoomKind kind, RoomShape shape, List<Integer> cores, int crypts, int secrets, int trappedChests) {
}
