package com.scd.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BackendUrlTest {
	@Test
	void acceptsNormalPorts() {
		assertEquals("http://localhost:3000", BackendUrl.validate("localhost:3000/").url());
		assertTrue(BackendUrl.validate("http://example.com").ok());
		assertTrue(BackendUrl.validate("https://example.com:8443").ok());
	}

	@Test
	void outboundCallsToAnyPortAreFine() {
		// 20/443/8000 are only off-limits for binding; the client just connects out.
		assertTrue(BackendUrl.validate("https://example.com").ok());
		assertTrue(BackendUrl.validate("http://host:8000").ok());
	}

	@Test
	void rejectsGarbage() {
		assertFalse(BackendUrl.validate("").ok());
		assertFalse(BackendUrl.validate("ftp://host:21").ok());
		assertFalse(BackendUrl.validate("http://").ok());
	}

	@Test
	void marketKeyOnlyGoesToMarketHost() {
		assertTrue(BackendUrl.isAllowedMarketUrl("https://market.scd.wtf/api/bazaar/products"));
		assertFalse(BackendUrl.isAllowedMarketUrl("https://evil.example/api"));
		assertFalse(BackendUrl.isAllowedMarketUrl("https://market.scd.wtf.evil.example/api"));
		assertFalse(BackendUrl.isAllowedMarketUrl("http://market.scd.wtf:8000/api"));
	}
}
