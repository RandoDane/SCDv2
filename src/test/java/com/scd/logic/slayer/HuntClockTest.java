package com.scd.logic.slayer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HuntClockTest {
	@Test
	void afkTimeDoesNotCount() {
		HuntClock c = new HuntClock();
		c.start("ENDERMAN", 0);
		// Kills every 3s for 30s.
		for (long t = 3_000; t <= 30_000; t += 3_000) {
			for (long s = t - 2_950; s <= t; s += 50) c.tick(s);
			c.progress(t);
		}
		assertEquals(30_000, c.activeMs());
		// Then AFK at the top for 10 minutes, ticking.
		for (long t = 30_050; t <= 630_000; t += 50) c.tick(t);
		assertTrue(c.paused(630_000));
		long window = c.window(); // 2.5 x 3s = 7.5s
		assertEquals(7_500, window);
		assertEquals(30_000 + window, c.activeMs(), "only the pace window after the last kill counts");
	}

	@Test
	void ownActionsKeepATankyFightCounted() {
		HuntClock c = new HuntClock();
		c.start("ZOMBIE", 0);
		for (long t = 50; t <= 20_000; t += 50) {
			if (t % 2_000 == 0) c.action(t); // hitting every 2s, no kill yet
			c.tick(t);
		}
		assertEquals(20_000, c.activeMs());
		assertFalse(c.paused(20_000));
	}

	@Test
	void windowFollowsPaceAndIsClamped() {
		HuntClock c = new HuntClock();
		c.start("SPIDER", 0);
		assertEquals(HuntClock.DEFAULT_WINDOW_MS, c.window());
		long t = 0;
		for (int i = 0; i < 5; i++) c.progress(t += 1_000);
		assertEquals(HuntClock.MIN_WINDOW_MS, c.window());
		for (int i = 0; i < 40; i++) c.progress(t += 60_000);
		assertEquals(HuntClock.MAX_WINDOW_MS, c.window());
		// Pace is per slayer type.
		c.start("WOLF", t);
		assertEquals(HuntClock.DEFAULT_WINDOW_MS, c.window());
	}
}
