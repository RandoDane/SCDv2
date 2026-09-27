package com.scd.logic.dungeon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScoreCalculatorTest {
	private static ScoreCalculator.Inputs perfectF7(int elapsed) {
		return new ScoreCalculator.Inputs("F7", 30, 100, 100, 5, 0, 0, elapsed, true, true, true, false, false, false);
	}

	@Test
	void perfectRunScoresThreeHundredAndTwo() {
		var b = ScoreCalculator.compute(perfectF7(600));
		assertEquals(100, b.skill());
		assertEquals(100, b.explore());
		assertEquals(100, b.speed());
		assertEquals(7, b.bonus()); // 5 crypts + mimic
		assertEquals(307, b.total());
	}

	@Test
	void speedDecaysPastTheTimeLimit() {
		assertEquals(100, ScoreCalculator.speed(839, 840));
		assertEquals(95, ScoreCalculator.speed(924, 840));  // 10% over
		assertEquals(54, ScoreCalculator.speed(3000, 840)); // 257% over
		assertEquals(0, ScoreCalculator.speed(100_000, 840));
	}

	@Test
	void incompletePuzzlesAndDeathsCostSkill() {
		assertEquals(100, ScoreCalculator.skill(30, 30, 0, 0));
		assertEquals(78, ScoreCalculator.skill(30, 30, 2, 2));
	}

	@Test
	void entranceIsScaled() {
		var in = new ScoreCalculator.Inputs("E", 10, 100, 100, 0, 0, 0, 100, true, true, false, false, false, false);
		var b = ScoreCalculator.compute(in);
		assertTrue(b.entrance());
		// Entrance has no mimic, so 100% secrets implies nothing: 70 + 70 + 70 + 0.
		assertEquals(210, b.total());
	}

	@Test
	void unknownFloorIsNull() {
		assertNull(ScoreCalculator.compute(new ScoreCalculator.Inputs("F9", 0, 0, 0, 0, 0, 0, 0, false, false, false, false, false, false)));
	}

	@Test
	void impliedMimicOnlyOnMimicFloors() {
		var f5 = new ScoreCalculator.Inputs("F5", 25, 100, 100, 0, 0, 0, 100, true, true, false, false, false, false);
		assertEquals(0, ScoreCalculator.compute(f5).bonus());
		var f6 = new ScoreCalculator.Inputs("F6", 25, 100, 100, 0, 0, 0, 100, true, true, false, false, false, false);
		assertEquals(2, ScoreCalculator.compute(f6).bonus());
	}

	@Test
	void ranks() {
		assertEquals("S+", ScoreCalculator.rank(300));
		assertEquals("S", ScoreCalculator.rank(273));
		assertEquals("A", ScoreCalculator.rank(250));
	}

	@Test
	void classifiesRunMessages() {
		assertEquals(RunMessages.Event.DEATH, RunMessages.classify(" ☠ Steve was killed by a Zombie."));
		assertEquals(RunMessages.Event.BLOOD_ROOM_COMPLETED, RunMessages.classify("[BOSS] The Watcher: You have proven yourself. You may pass."));
		assertEquals(RunMessages.Event.BOSS_ENTERED, RunMessages.classify("[BOSS] Sadan: So you made it all the way here... Now you wish to defy me? Sadan?!"));
		assertNull(RunMessages.classify("[BOSS] Sadan: I am the bridge between this realm and the world below!"));
		assertEquals(RunMessages.Event.DEATH, RunMessages.classify(" ☠ Bob was killed by Crypt Lurker and became a ghost."));
		assertNull(RunMessages.classify("☠ Defeated Sadan in 04m 46s"));
		assertNull(RunMessages.classify("[BOSS] The Watcher: Oh, you've made it."));
		assertEquals(RunMessages.Event.MIMIC_KILLED, RunMessages.classify("Party > [MVP+] Bob: Mimic Killed!"));
	}

	@Test
	void totalRoomsSolvedFromClearPercentWithKnownRoomsAsFloor() {
		assertEquals(25, ScoreCalculator.totalRooms(10, 40, 0));
		// 5 rooms at 17% fits 29 and 30; with 30 rooms already seen on the map only 30 remains.
		assertEquals(29, ScoreCalculator.totalRooms(5, 17, 0));
		assertEquals(30, ScoreCalculator.totalRooms(5, 17, 30));
		assertEquals(0, ScoreCalculator.totalRooms(0, 0, 0));
		assertEquals(27, ScoreCalculator.totalRooms(0, 0, 27));
	}

	@Test
	void secretsTotalsAndNeeds() {
		assertEquals(50, ScoreCalculator.estimateTotalSecrets(20, 40.0));
		assertEquals(0, ScoreCalculator.estimateTotalSecrets(0, 0));
		// F7 (100% required), 50 secrets, 5 crypts + mimic (bonus 7), no deaths: 300 needs 33 points of 40.
		assertEquals((int) Math.ceil(50 * 33 / 40.0), ScoreCalculator.secretsNeeded(300, 50, 100, 7, 0));
		// Enough bonus makes S free.
		assertEquals(0, ScoreCalculator.secretsNeeded(270, 50, 100, 10, 0));
		// A death costs 2 points, 1 with a Spirit pet.
		assertEquals(2, ScoreCalculator.deathPenalty(1, false));
		assertEquals(1, ScoreCalculator.deathPenalty(1, true));
		assertEquals(3, ScoreCalculator.deathPenalty(2, true));
	}

	@Test
	void exactSecretTotalOverridesTabPercent() {
		var in = new ScoreCalculator.Inputs("F7", 30, 100, 30, 45, 88.0, 50, 5, 0, 0, false, 600, true, true, true, false, false, false);
		var b = ScoreCalculator.compute(in);
		assertTrue(b.exactSecrets());
		assertEquals(50, b.totalSecrets());
		assertEquals(36, b.explore() - 60); // 45/50 = 90% of the 100% requirement -> 36 of 40
	}
}
