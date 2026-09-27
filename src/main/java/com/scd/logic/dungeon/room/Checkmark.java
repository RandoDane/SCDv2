package com.scd.logic.dungeon.room;

/** The mark Hypixel's map draws in a room's centre. */
public enum Checkmark {
	/** Not visible on the map yet. */
	UNDISCOVERED,
	/** Opened, nothing drawn yet. */
	NONE,
	/** All mobs cleared. */
	WHITE,
	/** All mobs and secrets done. */
	GREEN,
	/** Failed (puzzle). */
	RED,
	/** Seen through a door but not opened. */
	QUESTION;

	public static Checkmark fromMapColor(byte color) {
		return switch (color) {
			case 34 -> WHITE;
			case 30 -> GREEN;
			case 18 -> RED;
			case 119 -> QUESTION;
			default -> null;
		};
	}

	/** White or green: counts as a completed room for score. */
	public boolean cleared() {
		return this == WHITE || this == GREEN;
	}
}
