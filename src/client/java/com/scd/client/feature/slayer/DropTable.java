package com.scd.client.feature.slayer;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Which item gains count as a Slayer drop, per type - the "quest active + item allowlist" approach
 * SkyHanni's own profit tracker uses, since mandatory Telekinesis means most drops never exist as a
 * ground entity that could be tied to a specific kill. Ids are transcribed from SkyHanni-REPO's
 * SlayerProfitTrackerItems.json; display names are the fallback for sack notifications, which only
 * ever give a name.
 */
final class DropTable {
	private static final Map<SlayerType, List<String>> IDS = new EnumMap<>(SlayerType.class);
	private static final Map<SlayerType, List<String>> NAMES = new EnumMap<>(SlayerType.class);

	static {
		IDS.put(SlayerType.ZOMBIE, List.of("REVENANT_FLESH", "FOUL_FLESH", "ZOMBIE_SLAYER_RUNE", "UNDEAD_CATALYST", "BEHEADED_HORROR",
				"REVENANT_CATALYST", "SNAKE_RUNE", "REVENANT_VISCERA", "SCYTHE_BLADE", "SHARD_OF_THE_SHREDDED", "WARDEN_HEART",
				"ROTTEN_FLESH", "GOLD_INGOT", "GOLDEN_POWDER", "DYE_MATCHA", "FESTERING_MAGGOT", "SEVERED_HAND",
				"ENCHANTMENT_SMITE_6", "ENCHANTMENT_SMITE_7"));
		NAMES.put(SlayerType.ZOMBIE, List.of("Revenant Flesh", "Foul Flesh", "Beheaded Horror", "Revenant Viscera", "Scythe Blade",
				"Shard of the Shredded", "Warden Heart", "Rotten Flesh", "Matcha Dye", "Festering Maggot", "Severed Hand", "Smite"));

		IDS.put(SlayerType.SPIDER, List.of("TARANTULA_WEB", "TOXIC_ARROW_POISON", "BITE_RUNE", "SPIDER_CATALYST", "FLY_SWATTER",
				"TARANTULA_TALISMAN", "DIGESTED_MOSQUITO", "SPIDER_EYE", "STRING", "BONE", "SPIDERS_DEN_TOP_TRAVEL_SCROLL",
				"ARACHNE_KEEPER_FRAGMENT", "BURNING_EYE", "DARKNESS_WITHIN_RUNE", "TARANTULA_CATALYST", "VIAL_OF_VENOM",
				"PRIMORDIAL_EYE", "ENSNARED_SNAIL", "SHRIVELED_WASP", "TARANTULA_SILK", "DYE_BRICK_RED", "ENCHANTMENT_BANE_OF_ARTHROPODS_6"));
		NAMES.put(SlayerType.SPIDER, List.of("Tarantula Web", "Toxic Arrow Poison", "Fly Swatter", "Digested Mosquito", "Spider Eye",
				"String", "Bone", "Burning Eye", "Vial of Venom", "Primordial Eye", "Ensnared Snail", "Shriveled Wasp",
				"Tarantula Silk", "Bane of Arthropods"));

		IDS.put(SlayerType.WOLF, List.of("WOLF_TOOTH", "HAMSTER_WHEEL", "SPIRIT_RUNE", "FURBALL", "RED_CLAW_EGG", "COUTURE_RUNE",
				"OVERFLUX_CAPACITOR", "GRIZZLY_BAIT", "BONE", "WOLF_TALISMAN", "PARK_CAVE_TRAVEL_SCROLL", "WEAK_WOLF_CATALYST",
				"PET_ITEM_FORAGING_SKILL_BOOST_EPIC", "DYE_CELESTE", "ENCHANTMENT_CRITICAL_6"));
		NAMES.put(SlayerType.WOLF, List.of("Wolf Tooth", "Hamster Wheel", "Furball", "Red Claw Egg", "Overflux Capacitor",
				"Grizzly Bait", "Bone", "Critical"));

		IDS.put(SlayerType.ENDERMAN, List.of("NULL_SPHERE", "TWILIGHT_ARROW_POISON", "ENDERSNAKE_RUNE", "SUMMONING_EYE",
				"TRANSMISSION_TUNER", "NULL_ATOM", "HAZMAT_ENDERMAN", "POCKET_ESPRESSO_MACHINE", "DRAGON_RUNE", "HANDY_BLOOD_CHALICE",
				"SINFUL_DICE", "EXCEEDINGLY_RARE_ENDER_ARTIFACT_UPGRADER", "PET_SKIN_ENDERMAN_SLAYER", "ETHERWARP_MERGER",
				"JUDGEMENT_CORE", "ENCHANTED_ENDER_PEARL", "ENDER_PEARL", "ENDERMAN_CORTEX_REWRITER", "DYE_BYZANTIUM",
				"ENDSTONE_IDOL", "ENCHANTMENT_MANA_STEAL_1", "ENCHANTMENT_SMARTY_PANTS_1", "ENCHANTMENT_ENDER_SLAYER_7"));
		NAMES.put(SlayerType.ENDERMAN, List.of("Null Sphere", "Null Atom", "Twilight Arrow Poison", "Transmission Tuner",
				"Summoning Eye", "Hazmat Enderman", "Handy Blood Chalice", "Pocket Espresso Machine",
				"Exceedingly Rare Ender Artifact Upgrade", "Judgement Core", "Etherwarp Merger", "End Stone Idol",
				"Byzantium Dye", "Ender Pearl", "Mana Steal", "Smarty Pants", "Ender Slayer"));

		IDS.put(SlayerType.BLAZE, List.of("DERELICT_ASHE", "LAVATEARS_RUNE", "WISP_POTION", "ARROW_BUNDLE_MAGMA", "MANA_DISINTEGRATOR",
				"SCORCHED_BOOKS", "KELVIN_INVERTER", "BLAZE_ROD_DISTILLATE", "GLOWSTONE_DUST_DISTILLATE", "MAGMA_CREAM_DISTILLATE",
				"NETHER_STALK_DISTILLATE", "CRUDE_GABAGOOL_DISTILLATE", "SCORCHED_POWER_CRYSTAL", "ARCHFIEND_DICE", "FIERY_BURST_RUNE",
				"FLAWED_OPAL_GEM", "HIGH_CLASS_ARCHFIEND_DICE", "WILSON_ENGINEERING_PLANS", "SUBZERO_INVERTER", "BLAZE_ASHES",
				"BLAZE_ROD", "ENCHANTED_BLAZE_POWDER", "NETHERRACK_LOOKING_SUNSHADE", "MILLENIA_OLD_BLAZE_ASHES",
				"SWORD_OF_BAD_HEALTH", "DYE_FLAME", "ENCHANTMENT_FIRE_ASPECT_3", "ENCHANTMENT_ULTIMATE_REITERATE_1",
				"ENCHANTMENT_SMOLDERING_1"));
		NAMES.put(SlayerType.BLAZE, List.of("Derelict Ashe", "Kelvin Inverter", "Scorched Power Crystal", "Archfiend Dice",
				"Flawed Opal Gemstone", "High Class Archfiend Dice", "Wilson's Engineering Plans", "Subzero Inverter", "Blaze Ashes",
				"Blaze Rod", "Blaze Powder", "Flame Dye", "Fire Aspect", "Ultimate Reiterate", "Smoldering", "Scorched Books"));

		IDS.put(SlayerType.VAMPIRE, List.of("COVEN_SEAL", "ENCHANTED_BOOK_BUNDLE_QUANTUM", "SOULTWIST_RUNE", "BUBBA_BLISTER",
				"CHOCOLATE_CHIP", "GUARDIAN_LUCKY_BLOCK", "MCGRUBBER_BURGER", "UNFANGED_VAMPIRE_PART", "ENCHANTED_BOOK_BUNDLE_THE_ONE",
				"HEMOVIBE", "VAMPIRIC_MELON", "DYE_SANGRIA"));
		NAMES.put(SlayerType.VAMPIRE, List.of("Coven Seal", "Enchanted Book Bundle", "Bubba Blister", "Fang-tastic Chocolate Chip",
				"Guardian Lucky Block", "McGrubber's Burger", "Unfanged Vampire Part", "Hemovibe", "Vampiric Melon", "Sangria Dye"));
	}

	private DropTable() {
	}

	/** Exact id match when an id is known; otherwise a display-name match (sack notifications). */
	static boolean isDrop(SlayerType type, String itemId, String displayName) {
		if (itemId != null && IDS.getOrDefault(type, List.of()).contains(itemId)) return true;
		if (displayName == null) return false;
		String needle = displayName.trim().toLowerCase(Locale.ROOT);
		for (String known : NAMES.getOrDefault(type, List.of())) {
			if (needle.contains(known.toLowerCase(Locale.ROOT))) return true;
		}
		return false;
	}
}
