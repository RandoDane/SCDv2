package com.scd.logic.dungeon;

import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

class QuizMemoryTest {
	@Test
	void learnsTheAnswerYouPickedWhenOruoSaysCorrect() {
		QuizMemory q = new QuizMemory();
		q.onLine("Question #1", "RandoDane");
		q.onLine("How many Fairy Souls are there in Jerry's Workshop?", "RandoDane");
		q.onLine("ⓐ 9 Fairy Souls", "RandoDane");
		q.onLine("ⓑ 3 Fairy Souls", "RandoDane");
		q.onLine("ⓒ 5 Fairy Souls", "RandoDane");
		q.pick("ⓒ");
		assertTrue(q.onLine("[STATUE] Oruo the Omniscient: RandoDane answered Question #1 correctly!", "RandoDane"));
		assertEquals("5 Fairy Souls", q.answer("How many Fairy Souls are there in Jerry's Workshop?"));

		// Someone else answering doesn't teach anything (their pick is unknown).
		q.onLine("Question #2", "RandoDane");
		q.onLine("What is the status of Goldor?", "RandoDane");
		q.onLine("ⓐ Boss", "RandoDane");
		assertFalse(q.onLine("[STATUE] Oruo the Omniscient: Friend answered Question #2 correctly!", "RandoDane"));

		assertArrayEquals(new String[]{"How many Fairy Souls are there in Jerry's Workshop?", "5 Fairy Souls"}, q.lastLearned());
		// Shared answers fill gaps but never override your own.
		q.setShared(java.util.Map.of("What is the status of Goldor?", "Boss", "How many Fairy Souls are there in Jerry's Workshop?", "9 Fairy Souls"));
		assertEquals("Boss", q.answer("What is the status of Goldor?"));
		assertEquals("5 Fairy Souls", q.answer("How many Fairy Souls are there in Jerry's Workshop?"));

		QuizMemory back = new QuizMemory();
		back.load(new StringReader(q.toJson()));
		assertEquals(1, back.size());
	}
}
