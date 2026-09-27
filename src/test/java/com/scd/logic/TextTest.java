package com.scd.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextTest {
	@Test
	void stripsFormattingIconsAndNbsp() {
		assertEquals("Void Sepulture", Text.clean("§b §7Void Sepulture "));
		// Bogus mid-word codes some client mods inject into the sidebar.
		assertEquals("Revenant Horror IV", Text.clean("Reve§qnant Hor§zror IV"));
		assertEquals("", Text.clean(null));
	}
}
