package com.scd.client.hypixel;

import java.util.Set;

/** Area names SCD cares about, used as a fallback when the sidebar's location glyph has been re-skinned. */
final class KnownAreas {
	private static final Set<String> AREAS = Set.of(
			"The End", "Void Sepulture", "Dragon's Nest", "Zealot Bruiser Hideout",
			"Crimson Isle", "Stronghold", "Smoldering Tomb", "Burning Desert", "Dragontail",
			"Spider's Den", "Arachne's Sanctuary", "Grandma's House", "Spider Mound", "Archaeologist's Camp",
			"Graveyard", "Coal Mine", "Howling Cave", "Ruins", "Stillgarden Rift", "Stillgarden",
			"Living Cave", "Wizard Tower");

	private KnownAreas() {
	}

	static boolean isKnown(String line) {
		return AREAS.contains(line);
	}
}
