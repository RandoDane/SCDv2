package com.scd.logic;

import java.util.regex.Pattern;

/** Minecraft account-name validation - used to filter Hypixel's fake tab-list filler entries ("!A-a") out of player lists. */
public final class Ign {
	private static final Pattern VALID = Pattern.compile("^\\w{1,16}$");

	private Ign() {
	}

	public static boolean isValid(String name) {
		return name != null && VALID.matcher(name).matches();
	}
}
