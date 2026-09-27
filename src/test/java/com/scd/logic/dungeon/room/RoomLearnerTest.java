package com.scd.logic.dungeon.room;

import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

class RoomLearnerTest {
	@Test
	void learnsRoomsAndNamesUnknownOnesLater() {
		RoomLearner l = new RoomLearner();
		assertTrue(l.observe(42, null, RoomKind.NORMAL, RoomShape.ONE_BY_ONE, 0, null, true));
		assertEquals("Unknown 42", l.byCore(42).name);
		assertTrue(l.observe(42, null, null, null, 0, 5, false));
		assertEquals(5, l.byCore(42).maxSecrets);
		assertTrue(l.observe(42, "Cage", RoomKind.NORMAL, RoomShape.ONE_BY_ONE, 1, null, false));
		assertEquals("Cage", l.byCore(42).name);
		assertEquals(0, l.namedCount() - 1);
		assertFalse(l.observe(42, "Cage", RoomKind.NORMAL, RoomShape.ONE_BY_ONE, 1, 5, false));

		RoomLearner back = new RoomLearner();
		back.load(new StringReader(l.toJson()));
		assertEquals("Cage", back.byCore(42).name);
		assertEquals(5, back.byCore(42).maxSecrets);
		assertEquals(1, back.byCore(42).visits);
		// The file is valid for the room database too.
		RoomDatabase db = new RoomDatabase();
		assertEquals(1, db.load(new StringReader(l.toJson())));
		assertEquals(5, db.byCore(42).secrets());
		// Unnamed learned rooms aren't loaded as real rooms (their secret total may be unknown).
		RoomLearner fresh = new RoomLearner();
		fresh.observe(7, null, RoomKind.NORMAL, RoomShape.ONE_BY_ONE, 0, null, true);
		assertEquals(0, new RoomDatabase().load(new StringReader(fresh.toJson()), true));
	}
}
