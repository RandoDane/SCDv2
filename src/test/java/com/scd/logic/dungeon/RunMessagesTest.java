package com.scd.logic.dungeon;

import org.junit.jupiter.api.Test;

import static com.scd.logic.dungeon.RunMessages.Event.*;
import static org.junit.jupiter.api.Assertions.*;

class RunMessagesTest {
	@Test
	void classifiesRunMilestones() {
		assertEquals(RUN_STARTED, RunMessages.classify("[NPC] Mort: Here, I found this map when I first entered the dungeon."));
		assertEquals(BLOOD_OPENED, RunMessages.classify("The BLOOD DOOR has been opened!"));
		assertEquals(BLOOD_OPENED, RunMessages.classify("[BOSS] The Watcher: Things feel a little more roomy now, eh?"));
		assertEquals(BLOOD_ROOM_COMPLETED, RunMessages.classify("[BOSS] The Watcher: You have proven yourself. You may pass."));
		assertEquals(BOSS_ENTERED, RunMessages.classify("[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!"));
		assertEquals(DEATH, RunMessages.classify(" ☠ Steve was killed by Withermancer and became a ghost."));
		assertNull(RunMessages.classify("☠ Defeated Maxor, Storm, Goldor, and Necron in 05m 12s"));
		assertEquals(PRINCE_KILLED, RunMessages.classify("A Prince falls. +1 Bonus Score"));
	}
}
