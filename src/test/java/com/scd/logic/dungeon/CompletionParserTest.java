package com.scd.logic.dungeon;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CompletionParserTest {
	private static final String BORDER = "§a§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬";

	@Test
	void parsesBothBlocksOfARealCompletion() {
		List<CompletionReport> reports = new ArrayList<>();
		CompletionParser parser = new CompletionParser(reports::add);
		for (String line : List.of(BORDER,
				"                         The Catacombs - Floor VI",
				"                         Team Score: 273 (S)",
				"                   ☠ Defeated Sadan in 04m 46s (NEW RECORD!)",
				"                     +150,000 Catacombs Experience",
				"                         +42,000.5 Healer Experience",
				BORDER,
				"some unrelated line",
				BORDER,
				"                       Master Mode The Catacombs - Floor VI Stats",
				"                         Team Score: 273 (S)",
				"                   ☠ Defeated Sadan in 04m 46s",
				"                  Total Damage as Mage: 12,345,678",
				"                     Ally Healing: 1,234",
				"                    Enemies Killed: 321",
				"                         Deaths: 1",
				"                     Secrets Found: 42",
				BORDER)) {
			parser.accept(line);
		}
		assertEquals(2, reports.size());
		var first = reports.get(0);
		assertEquals("F6", first.floorKey());
		assertEquals(273, first.teamScore());
		assertEquals("S", first.scoreRank());
		assertEquals("Sadan", first.boss());
		assertEquals("04m 46s", first.clearTime());
		assertEquals(150_000.0, first.cataExp());
		assertEquals("Healer", first.classExpClass());

		var second = reports.get(1);
		assertEquals("M6", second.floorKey());
		assertEquals(12_345_678L, second.totalDamage());
		assertEquals(42, second.secretsFound());
		assertEquals(1, second.deaths());
	}

	@Test
	void ignoresBorderedBlocksWithoutABoss() {
		List<CompletionReport> reports = new ArrayList<>();
		CompletionParser parser = new CompletionParser(reports::add);
		parser.accept(BORDER);
		parser.accept("Welcome to the event!");
		parser.accept(BORDER);
		assertTrue(reports.isEmpty());
	}
}
