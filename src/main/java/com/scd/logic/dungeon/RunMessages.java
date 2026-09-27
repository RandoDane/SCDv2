package com.scd.logic.dungeon;

import com.scd.logic.Text;

import java.util.regex.Pattern;

/**
 * Chat lines during a run that feed the score estimate but never show on the sidebar/tab list.
 * Shapes follow Skyblocker's source; the Watcher line is confirmed from live captures.
 */
public final class RunMessages {
	public enum Event { RUN_STARTED, BLOOD_OPENED, DEATH, MIMIC_KILLED, PRINCE_KILLED, BAT_KILLED, BLOOD_ROOM_COMPLETED, BOSS_ENTERED }

	/** " ☠ Steve was killed by X and became a ghost." / " ☠ You died ..." - never the "☠ Defeated Boss in ..." report. */
	private static final Pattern DEATH = Pattern.compile("^☠ (?!Defeated )\\S+ .*");
	private static final Pattern MIMIC = Pattern.compile(".*?(?:Mimic dead!?|Mimic Killed!)$");
	private static final Pattern PRINCE = Pattern.compile(".*?(?:Prince dead!?|Prince Killed!)$|^A Prince falls\\. \\+1 Bonus Score$");
	private static final Pattern BAT = Pattern.compile(".*?(?:Bat dead!?|Bat Killed!)$|^A Bat has been slain\\. \\+1 Bonus Score$");
	// Winning the Watcher's trial - NOT "The BLOOD DOOR has been opened!", which only starts it.
	private static final Pattern BLOOD_DONE = Pattern.compile("^\\[BOSS] The Watcher: You have proven yourself\\. You may pass\\.$");
	/** Mort's opening line: the run (and Hypixel's timer) starts. Class-dependent second line included. */
	private static final Pattern RUN_START = Pattern.compile("^\\[NPC] Mort: (?:Here, I found this map when I first entered the dungeon\\.|Right-click the Orb for spells, and Left-click \\(or Drop\\) to use your Ultimate!)$");
	/** Blood door opened: the door message, or any of the Watcher's greetings (Odin's list). */
	private static final Pattern BLOOD_OPEN = Pattern.compile("^The BLOOD DOOR has been opened!$|^\\[BOSS] The Watcher: (?:Congratulations, you made it through the Entrance\\.|Ah, you've finally arrived\\.|Ah, we meet again\\.\\.\\.|So you made it this far\\.\\.\\. interesting\\.|You've managed to scratch and claw your way here, eh\\?|I'm starting to get tired of seeing you around here\\.\\.\\.|Oh\\.\\. hello\\?|Things feel a little more roomy now, eh\\?)$");
	/** The first thing each floor boss says as the party enters its room (same list as Skyblocker's DungeonBoss). */
	private static final java.util.Set<String> BOSS_ENTRY = java.util.Set.of(
			"[BOSS] Bonzo: Gratz for making it this far, but I'm basically unbeatable.",
			"[BOSS] Scarf: This is where the journey ends for you, Adventurers.",
			"[BOSS] The Professor: I was burdened with terrible news recently...",
			"[BOSS] Thorn: Welcome Adventurers! I am Thorn, the Spirit! And host of the Vegan Trials!",
			"[BOSS] Livid: Welcome, you've arrived right on time. I am Livid, the Master of Shadows.",
			"[BOSS] Sadan: So you made it all the way here... Now you wish to defy me? Sadan?!",
			"[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!");

	private RunMessages() {
	}

	/** The event this message represents, or null. */
	public static Event classify(String rawMessage) {
		String text = Text.clean(rawMessage);
		if (text.isEmpty()) return null;
		if (RUN_START.matcher(text).matches()) return Event.RUN_STARTED;
		if (BLOOD_OPEN.matcher(text).matches()) return Event.BLOOD_OPENED;
		if (DEATH.matcher(text).matches() && !text.contains("reconnected")) return Event.DEATH;
		if (MIMIC.matcher(text).matches()) return Event.MIMIC_KILLED;
		if (PRINCE.matcher(text).matches()) return Event.PRINCE_KILLED;
		if (BAT.matcher(text).matches()) return Event.BAT_KILLED;
		if (BLOOD_DONE.matcher(text).matches()) return Event.BLOOD_ROOM_COMPLETED;
		if (BOSS_ENTRY.contains(text)) return Event.BOSS_ENTERED;
		return null;
	}
}
