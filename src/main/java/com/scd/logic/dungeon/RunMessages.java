package com.scd.logic.dungeon;

import com.scd.logic.Text;

import java.util.regex.Pattern;

/**
 * Chat lines during a run that feed the score estimate but never show on the sidebar/tab list.
 * Shapes follow Skyblocker's source; the Watcher line is confirmed from live captures.
 */
public final class RunMessages {
	public enum Event { DEATH, MIMIC_KILLED, PRINCE_KILLED, BAT_KILLED, BLOOD_ROOM_COMPLETED, BOSS_ENTERED }

	/** " ☠ Steve was killed by X and became a ghost." / " ☠ You died ..." - never the "☠ Defeated Boss in ..." report. */
	private static final Pattern DEATH = Pattern.compile("^☠ (?!Defeated )\\S+ .*");
	private static final Pattern MIMIC = Pattern.compile(".*?(?:Mimic dead!?|Mimic Killed!)$");
	private static final Pattern PRINCE = Pattern.compile(".*?(?:Prince dead!?|Prince Killed!)$|^A Prince falls\\. \\+1 Bonus Score$");
	private static final Pattern BAT = Pattern.compile(".*?(?:Bat dead!?|Bat Killed!)$|^A Bat has been slain\\. \\+1 Bonus Score$");
	// Winning the Watcher's trial - NOT "The BLOOD DOOR has been opened!", which only starts it.
	private static final Pattern BLOOD_DONE = Pattern.compile("^\\[BOSS] The Watcher: You have proven yourself\\. You may pass\\.$");
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
		if (DEATH.matcher(text).matches() && !text.contains("reconnected")) return Event.DEATH;
		if (MIMIC.matcher(text).matches()) return Event.MIMIC_KILLED;
		if (PRINCE.matcher(text).matches()) return Event.PRINCE_KILLED;
		if (BAT.matcher(text).matches()) return Event.BAT_KILLED;
		if (BLOOD_DONE.matcher(text).matches()) return Event.BLOOD_ROOM_COMPLETED;
		if (BOSS_ENTRY.contains(text)) return Event.BOSS_ENTERED;
		return null;
	}
}
