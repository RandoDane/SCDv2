package com.scd.client.feature.accessory;

import com.scd.client.hypixel.Items;
import com.scd.logic.Numbers;
import com.scd.logic.Text;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the in-game Accessory Bag passively, page by page, as the player browses it (no clicking on
 * their behalf). The title is "Accessory Bag (X/Y)"; every real accessory - and nothing else in the
 * menu - has an "Accessory Power: +N" lore line, so that line alone identifies them without knowing
 * slot layouts. Once every page has been seen the summed power matches the game's own total.
 */
final class BagScanner {
	private static final Pattern TITLE = Pattern.compile("^Accessory Bag \\((\\d+)/(\\d+)\\)$");
	private static final Pattern POWER = Pattern.compile("Accessory Power:\\s*\\+([\\d,]+)");
	private static final Pattern RARITY = Pattern.compile("VERY SPECIAL|SPECIAL|DIVINE|MYTHIC|LEGENDARY|EPIC|RARE|UNCOMMON|COMMON");

	record Scanned(String name, String rarity, int power) {
	}

	private final Map<String, Scanned> scanned = new LinkedHashMap<>();
	private final Set<Integer> pages = new HashSet<>();
	private int totalPages;

	static boolean isBag(Screen screen) {
		return TITLE.matcher(Text.clean(screen.getTitle().getString())).matches();
	}

	void scan(Screen screen) {
		Matcher t = TITLE.matcher(Text.clean(screen.getTitle().getString()));
		if (!t.matches() || !(screen instanceof AbstractContainerScreen<?> c)) return;
		pages.add(Integer.parseInt(t.group(1)));
		totalPages = Math.max(totalPages, Integer.parseInt(t.group(2)));
		for (var slot : c.getMenu().slots) {
			if (!slot.hasItem()) continue;
			Integer power = null;
			String rarity = null;
			for (String line : Items.lore(slot.getItem())) {
				Matcher p = POWER.matcher(line);
				if (p.find()) power = Numbers.parseIntOrNull(p.group(1));
				Matcher r = RARITY.matcher(line);
				if (r.find()) rarity = r.group();
			}
			if (power == null) continue;
			String name = Items.name(slot.getItem());
			scanned.put(name, new Scanned(name, rarity, power));
		}
	}

	void reset() {
		scanned.clear();
		pages.clear();
		totalPages = 0;
	}

	boolean complete() {
		return totalPages > 0 && pages.size() >= totalPages;
	}

	int pagesSeen() {
		return pages.size();
	}

	int totalPages() {
		return totalPages;
	}

	int count() {
		return scanned.size();
	}

	int totalPower() {
		return scanned.values().stream().mapToInt(Scanned::power).sum();
	}

	Collection<Scanned> items() {
		return scanned.values();
	}

	boolean has(String name) {
		return scanned.containsKey(name);
	}
}
