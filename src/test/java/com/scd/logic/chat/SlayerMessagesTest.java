package com.scd.logic.chat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SlayerMessagesTest {
	@Test
	void readsLevelAndMeterLines() {
		assertEquals("Enderman", SlayerMessages.levelUpTypeName("   §5Enderman Slayer LVL 7 - Next LVL in 100,000 XP!"));
		assertEquals(1_141_512L, SlayerMessages.rngMeterStoredXp("RNG Meter - 1,141,512 Stored XP"));
		// Anchored: a copy pasted into party chat must not count.
		assertNull(SlayerMessages.rngMeterStoredXp("Party > Bob: RNG Meter - 5 Stored XP"));
	}

	@Test
	void readsSackHoverText() {
		var gains = SlayerMessages.sackGains("Added items:\n +2 Enchanted Ender Pearl (Enchanted Combat Sack)\n +1,024 Null Sphere (Combat Sack)\n -5 Bone (Combat Sack)");
		assertEquals(List.of(
				new SlayerMessages.SackGain("Enchanted Ender Pearl", 2, "Enchanted Combat Sack"),
				new SlayerMessages.SackGain("Null Sphere", 1024, "Combat Sack")), gains);
	}

	@Test
	void questResults() {
		assertEquals(SlayerMessages.QuestResult.COMPLETE, SlayerMessages.questResult("  \u00A75\u00A7lSLAYER QUEST COMPLETE!"));
		assertEquals(SlayerMessages.QuestResult.FAILED, SlayerMessages.questResult("  SLAYER QUEST FAILED!"));
		assertNull(SlayerMessages.questResult("Party > Bob: SLAYER QUEST COMPLETE!"));
	}

	@Test
	void detectsCocoon() {
		assertTrue(SlayerMessages.isCocoon("§d§lYOU COCOONED YOUR SLAYER BOSS"));
	}
}
