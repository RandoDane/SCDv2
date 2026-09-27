package com.scd.logic.dungeon.loot;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChestLootTest {
	@Test
	void parsesContentsCostAndKey() {
		var chest = ChestLoot.parse(List.of("§7Contents", "§6Necron's Handle", "§aEnchanted Book (Ultimate Wise V)",
				"§dWither Essence x20", "", "§7Cost", "§62,000,000 Coins", "§9Dungeon Chest Key", "", "§eClick to open!"));
		assertNotNull(chest);
		assertEquals(3, chest.contents().size());
		assertEquals(new ChestLoot.Entry("Wither Essence", 20), chest.contents().get(2));
		assertEquals(2_000_000, chest.coinCost());
		assertTrue(chest.needsKey());
		var free = ChestLoot.parse(List.of("Contents", "Undead Essence x5", "", "Cost", "FREE"));
		assertEquals(0, free.coinCost());
		assertFalse(free.needsKey());
		assertNull(ChestLoot.parse(List.of("Some other item")));
	}

	@Test
	void idHints() {
		assertEquals("ESSENCE_WITHER", ChestLoot.idHint("Wither Essence"));
		assertEquals("ENCHANTMENT_ULTIMATE_WISE_5", ChestLoot.idHint("Enchanted Book (Ultimate Wise V)"));
		assertEquals("ENCHANTMENT_ULTIMATE_ONE_FOR_ALL_1", ChestLoot.idHint("Enchanted Book (Ultimate One For All I)"));
		assertNull(ChestLoot.idHint("Necron's Handle"));
		assertTrue(ChestLoot.CHEST_NAME.matcher("Obsidian").matches());
		assertTrue(ChestLoot.CHEST_NAME.matcher("Bedrock Chest").matches());
	}
}
