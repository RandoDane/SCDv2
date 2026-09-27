package com.scd.client.feature.slayer;

import java.util.List;
import java.util.Locale;

/**
 * The six Slayer bosses and everything SCD knows about each: every nameplate form (renamed forms at
 * higher tiers included, cross-checked against Skyblocker's and SkyHanni's tables), and - for the
 * three whose mobs only exist in specific places - the sidebar sub-areas where a quest can progress.
 * Outside those areas the quest is treated as dormant, so HUDs, cues and drop tracking stay quiet.
 */
public enum SlayerType {
	ZOMBIE("Zombie", "Revenant Horror", List.of("Revenant Horror", "Atoned Horror"), List.of()),
	SPIDER("Spider", "Tarantula Broodfather", List.of("Tarantula Broodfather", "Conjoined Brood"),
			List.of("Spider's Den", "Burning Desert", "Dragontail", "Arachne's Sanctuary", "Spider Mound", "Grandma's House", "Archaeologist's Camp")),
	WOLF("Wolf", "Sven Packmaster", List.of("Sven Packmaster"), List.of()),
	ENDERMAN("Enderman", "Voidgloom Seraph", List.of("Voidgloom Seraph"), List.of("The End", "Void Sepulture", "Dragon's Nest", "Zealot Bruiser Hideout")),
	BLAZE("Blaze", "Inferno Demonlord", List.of("Inferno Demonlord"), List.of("Crimson Isle", "Stronghold", "Smoldering Tomb", "Burning Desert", "Dragontail")),
	VAMPIRE("Vampire", "Riftstalker Bloodfiend", List.of("Riftstalker Bloodfiend", "Bloodfiend"), List.of());

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
