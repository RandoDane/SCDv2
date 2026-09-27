package com.scd.client.feature.slayer;

import java.util.List;
import java.util.Locale;

/**
 * The six Slayer bosses and everything SCD knows about each: every nameplate form (renamed forms at
 * higher tiers included, cross-checked against Skyblocker's and SkyHanni's tables), and - for the
 * sidebar sub-areas where each quest can progress. Outside them the quest is dormant: it keeps
 * running (timers, kill booking), but HUDs, cues and drop tracking stay quiet.
 */
public enum SlayerType {
	// Areas are sidebar area names where the quest can progress (SkyHanni's table, which matches
	// Hypixel's sidebar). Outside them the quest keeps running but SCD shows nothing for it.
	ZOMBIE("Zombie", "Revenant Horror", List.of("Revenant Horror", "Atoned Horror"),
			List.of("Graveyard", "Revenant Cave", "Crypts")),
	SPIDER("Spider", "Tarantula Broodfather", List.of("Tarantula Broodfather", "Conjoined Brood"),
			List.of("Spider's Den", "Spider Mound", "Arachne's Burrow", "Arachne's Sanctuary", "Burning Desert")),
	WOLF("Wolf", "Sven Packmaster", List.of("Sven Packmaster"), List.of("Ruins", "Howling Cave", "Soul Cave", "Spirit Cave")),
	ENDERMAN("Enderman", "Voidgloom Seraph", List.of("Voidgloom Seraph"), List.of("The End", "Void Sepulture", "Dragon's Nest", "Zealot Bruiser Hideout")),
	BLAZE("Blaze", "Inferno Demonlord", List.of("Inferno Demonlord"), List.of("Stronghold", "The Wasteland", "Smoldering Tomb")),
	VAMPIRE("Vampire", "Riftstalker Bloodfiend", List.of("Riftstalker Bloodfiend", "Bloodfiend"), List.of("Stillgore Château", "Oubliette"));

	private final String displayName;
	private final String bossName;
	private final List<String> nameplates;
	private final List<String> areas;

	SlayerType(String displayName, String bossName, List<String> nameplates, List<String> areas) {
		this.displayName = displayName;
		this.bossName = bossName;
		this.nameplates = nameplates;
		this.areas = areas;
	}

	public String displayName() {
		return displayName;
	}

	public String bossName() {
		return bossName;
	}

	public List<String> nameplates() {
		return nameplates;
	}

	public boolean isAreaRestricted() {
		return !areas.isEmpty();
	}

	public List<String> areas() {
		return areas;
	}

	public boolean matchesNameplate(String text) {
		for (String n : nameplates) if (text.contains(n)) return true;
		return false;
	}

	/** Matches a sidebar line or nameplate against every type's boss names. */
	public static SlayerType fromBossText(String text) {
		if (text == null) return null;
		for (SlayerType t : values()) if (t.matchesNameplate(text)) return t;
		return null;
	}

	/** "zombie", "Zombie", "revenant", "Revenant Horror" ... -> ZOMBIE. */
	public static SlayerType parse(String text) {
		if (text == null) return null;
		String t = text.trim().toLowerCase(Locale.ROOT);
		for (SlayerType type : values()) {
			if (type.name().toLowerCase(Locale.ROOT).equals(t) || type.displayName.toLowerCase(Locale.ROOT).equals(t)
					|| type.bossName.toLowerCase(Locale.ROOT).startsWith(t)) return type;
		}
		return null;
	}

	public String commandName() {
		return name().toLowerCase(Locale.ROOT);
	}
}
