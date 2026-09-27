package com.scd.logic.slayer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NameplateTest {
	@Test
	void readsCurrentOverMax() {
		var hp = Nameplate.health("☠ Revenant Horror 380,000/400,000❤");
		assertEquals(380_000, hp.current());
		assertEquals(400_000, hp.max());
	}

	@Test
	void readsAbbreviatedSingleValue() {
		var hp = Nameplate.health("☠ Voidgloom Seraph 12.4M❤");
		assertEquals(12_400_000, hp.current(), 1);
		assertNull(hp.max());
	}

	@Test
	void unreadableNameplatesReturnNull() {
		assertNull(Nameplate.health("Spawned by: Someone"));
		assertNull(Nameplate.health(null));
	}

	@Test
	void detectsCorpseAndShield() {
		assertTrue(Nameplate.isDead("Revenant Horror 0❤"));
		assertFalse(Nameplate.isDead("Revenant Horror 10❤"));
		assertEquals(new Nameplate.Shield(true, 45), Nameplate.shield("Voidgloom Seraph 45 Hits"));
		assertFalse(Nameplate.shield("Voidgloom Seraph 20M❤").active());
	}

	@Test
	void tierOrdering() {
		assertTrue(SlayerTier.atLeast("IV", "III"));
		assertFalse(SlayerTier.atLeast("II", "III"));
		assertFalse(SlayerTier.atLeast(null, "I"));
		assertEquals("IV", SlayerTier.normalize("iv"));
	}
}
