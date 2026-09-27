package com.scd.client.ui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** The sidebar's pages, registered once at startup. Each factory builds a fresh top-level page. */
public final class Nav {
	public record Item(String key, String section, String label, Supplier<ItemStack> icon, Supplier<Screen> open) {
	}

	private static final List<Item> ITEMS = new ArrayList<>();

	private Nav() {
	}

	public static void register(Item item) {
		ITEMS.add(item);
	}

	public static List<Item> items() {
		return List.copyOf(ITEMS);
	}
}
