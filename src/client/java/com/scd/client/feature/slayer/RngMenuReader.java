package com.scd.client.feature.slayer;

import com.scd.client.hypixel.Items;
import com.scd.logic.Numbers;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Passively reads RNG Meter state from open menus - no clicking on the player's behalf:
 * <ol>
 *   <li>the Slayer menu's "&lt;Boss&gt; RNG Meter" item: "Selected Drop" (name on the next lore line)
 *       plus a "current/total" fraction;</li>
 *   <li>the "&lt;Boss&gt; RNG Meter" screen: one item per drop whose lore carries that drop's own
 *       required total, anchored on "Filling the meter increases the drop chance".</li>
 * </ol>
 * Re-run every tick while the screen is open, because Hypixel streams the real item stacks in
 * after the screen opens.
 */
final class RngMenuReader {
	private static final Pattern METER_NAME = Pattern.compile("^(.+?) RNG Meter$");
	private static final Pattern PERCENT = Pattern.compile("Progress:\\s*([\\d.]+)\\s*%", Pattern.CASE_INSENSITIVE);
	private static final Pattern FRACTION = Pattern.compile("([\\d,.]+)\\s*([kKmMbB]?)\\s*/\\s*([\\d,.]+)\\s*([kKmMbB]?)");
	private static final String DROP_ANCHOR = "filling the meter increases the drop chance";

	private final RngMeter meter;

	RngMenuReader(RngMeter meter) {
		this.meter = meter;
	}

	void scan(Screen screen) {
		if (!(screen instanceof AbstractContainerScreen<?> container)) return;
		SlayerType screenType = meterType(com.scd.logic.Text.clean(screen.getTitle().getString()));
		for (var slot : container.getMenu().slots) {
			if (!slot.hasItem()) continue;
			ItemStack stack = slot.getItem();
			List<String> lore = Items.lore(stack);
			if (lore.isEmpty()) continue;
			String name = Items.name(stack);
			SlayerType itemType = meterType(name);
			if (itemType != null) {
				mainIcon(itemType, lore);
			} else if (screenType != null && lore.stream().anyMatch(l -> l.toLowerCase(Locale.ROOT).contains(DROP_ANCHOR))) {
				dropDetail(screenType, name, lore);
			}
		}
	}

	private void mainIcon(SlayerType type, List<String> lore) {
		String selected = null;
		for (int i = 0; i + 1 < lore.size(); i++) {
			if (lore.get(i).equalsIgnoreCase("Selected Drop") && !lore.get(i + 1).isBlank()) {
				selected = lore.get(i + 1).trim();
				meter.recordSelectedDrop(type, selected);
			}
		}
		progress(type, lore, selected);
	}

	private void dropDetail(SlayerType type, String drop, List<String> lore) {
		for (String line : lore) {
			Matcher f = FRACTION.matcher(line);
			if (f.find()) {
				meter.recordRequired(type, drop, Math.round(Numbers.compact(f.group(3), f.group(4))));
				break;
			}
		}
		boolean selected = lore.stream().anyMatch(l -> l.equalsIgnoreCase("SELECTED"));
		if (selected) meter.recordSelectedDrop(type, drop);
		progress(type, lore, selected ? drop : null);
	}

	private void progress(SlayerType type, List<String> lore, String dropForTotal) {
		for (String line : lore) {
			Matcher f = FRACTION.matcher(line);
			if (f.find()) {
				try {
					meter.recordStoredXp(type, Math.round(Numbers.compact(f.group(1), f.group(2))));
					if (dropForTotal != null) meter.recordRequired(type, dropForTotal, Math.round(Numbers.compact(f.group(3), f.group(4))));
				} catch (NumberFormatException ignored) {
					// partially rendered lore
				}
			}
			Matcher p = PERCENT.matcher(line);
			if (p.find()) {
				Double pct = Numbers.parseDoubleOrNull(p.group(1));
				if (pct != null) meter.recordChance(type, pct);
			}
		}
	}

	private static SlayerType meterType(String text) {
		Matcher m = METER_NAME.matcher(text);
		return m.matches() ? SlayerType.fromBossText(m.group(1)) : null;
	}
}
