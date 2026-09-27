package com.scd.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NumbersTest {
	@Test
	void parsesCompactAmounts() {
		assertEquals(1_300_000L, Numbers.parseCompactLong("1.3m").getAsLong());
		assertEquals(800_000L, Numbers.parseCompactLong("800k").getAsLong());
		assertEquals(50_000L, Numbers.parseCompactLong("50000").getAsLong());
		assertEquals(1_234_567L, Numbers.parseCompactLong("1,234,567").getAsLong());
		assertEquals(2_000_000_000L, Numbers.parseCompactLong("2B").getAsLong());
		assertTrue(Numbers.parseCompactLong("abc").isEmpty());
		assertTrue(Numbers.parseCompactLong("").isEmpty());
		assertTrue(Numbers.parseCompactLong("1.2.3").isEmpty());
	}

	@Test
	void formatsCoinsAndCounts() {
		assertEquals("950", Numbers.coins(950));
		assertEquals("12.5", Numbers.coins(12.5));
		assertEquals("0", Numbers.coins(0));
		assertEquals("1.2K", Numbers.coins(1_234));
		assertEquals("3.40M", Numbers.coins(3_400_000, 2));
		assertEquals("2.5K", Numbers.compactCount(2_541));
		assertEquals("15M", Numbers.compactCount(15_000_000));
		assertEquals("99,899", Numbers.compactCount(99_899, 99_900));
	}

	@Test
	void formatsDurations() {
		assertEquals("2:05", Numbers.duration(125_000));
		assertEquals("1:02:05", Numbers.duration(3_725_000));
		assertEquals("0:00", Numbers.duration(-5));
	}

	@Test
	void parsesClearTime() {
		assertEquals(286_000, Numbers.parseClearTimeMs("04m 46s"));
		assertEquals(42_000, Numbers.parseClearTimeMs("42s"));
		assertEquals(3_723_000, Numbers.parseClearTimeMs("1h 02m 03s"));
		assertEquals(0, Numbers.parseClearTimeMs(null));
	}

	@Test
	void romanNumerals() {
		assertEquals(4, Numbers.romanToInt("iv"));
		assertEquals("VII", Numbers.intToRoman(7));
		assertNull(Numbers.romanToInt("XIV"));
	}

	@org.junit.jupiter.api.Test
	void durationTenths() {
		org.junit.jupiter.api.Assertions.assertEquals("12.3s", Numbers.durationTenths(12_399));
		org.junit.jupiter.api.Assertions.assertEquals("0.0s", Numbers.durationTenths(40));
		org.junit.jupiter.api.Assertions.assertEquals("1:15.0", Numbers.durationTenths(75_060));
		org.junit.jupiter.api.Assertions.assertEquals("1:02:03.4", Numbers.durationTenths(3_723_450));
	}
}
