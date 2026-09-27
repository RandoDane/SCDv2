package com.scd.client.feature.slayer;

import com.scd.client.core.ScdLog;
import com.scd.client.hypixel.Items;
import com.scd.client.storage.JsonStore;
import com.scd.logic.chat.SlayerMessages;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Tallies Slayer drops from two sources: tick-to-tick inventory diffs (Telekinesis delivers almost
 * everything straight to the inventory) and sack notifications (items that go straight into a sack
 * never touch the inventory; the item list is only in the chat line's hover text). A gain counts
 * only while a quest - or the post-kill loot window - is active, and only for items on that type's
 * {@link DropTable}. Gains while another container is open (chest, Bazaar) are ignored: those are
 * the player moving items, not loot.
 */
final class DropTracker {
	private static final int MENU_SLOT = 8;

	private final JsonStore<SlayerData> store;
	private final Supplier<SlayerType> activeType;
	private final Function<ItemStack, String> idOf;
	private final Function<String, Double> valueOf;
	private final SessionStats session;
	private Map<String, Integer> lastCounts;

	DropTracker(JsonStore<SlayerData> store, Supplier<SlayerType> activeType, Function<ItemStack, String> idOf,
			Function<String, Double> valueOf, SessionStats session) {
		this.store = store;
		this.activeType = activeType;
		this.idOf = idOf;
		this.valueOf = valueOf;
		this.session = session;
	}

	void tick() {
		var mc = Minecraft.getInstance();
		if (mc.player == null) {
			lastCounts = null;
			return;
		}
		Map<String, Integer> counts = new HashMap<>();
		Map<String, String> names = new HashMap<>();
		var inv = mc.player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (i == MENU_SLOT) continue;
			ItemStack stack = inv.getItem(i);
			if (stack.isEmpty()) continue;
			String id = idOf.apply(stack);
			if (id == null) id = "NAME:" + Items.name(stack);
			counts.merge(id, stack.getCount(), Integer::sum);
			names.putIfAbsent(id, Items.name(stack));
		}
		var screen = mc.gui.screen();
		boolean foreignContainer = screen instanceof AbstractContainerScreen<?> && !(screen instanceof InventoryScreen);
		if (lastCounts != null && !foreignContainer) {
			for (var e : counts.entrySet()) {
				int gained = e.getValue() - lastCounts.getOrDefault(e.getKey(), 0);
				if (gained > 0) consider(e.getKey().startsWith("NAME:") ? null : e.getKey(), names.get(e.getKey()), gained, "inventory");
			}
		}
		lastCounts = counts;
	}

	void onChat(Component message) {
		String hover = com.scd.client.hypixel.ChatText.hoverText(message);
		String source = hover != null ? hover : message.getString();
		for (SlayerMessages.SackGain g : SlayerMessages.sackGains(source)) consider(null, g.itemName(), g.amount(), "sack");
	}

	private void consider(String itemId, String name, int amount, String via) {
		SlayerType type = activeType.get();
		if (type == null) return;
		if (!DropTable.isDrop(type, itemId, name)) {
			ScdLog.debug("Ignored " + via + " gain " + name + " x" + amount + " (not a " + type.displayName() + " drop)");
			return;
		}
		String key = itemId != null ? itemId : "SACK:" + name;
		var drops = store.get().drops.computeIfAbsent(type.name(), k -> new HashMap<>());
		SlayerData.Drop d = drops.computeIfAbsent(key, k -> new SlayerData.Drop(name, 0));
		d.count += amount;
		d.name = name;
		store.markDirty();
		Double unit = itemId != null ? valueOf.apply(itemId) : null;
		if (unit != null) session.addDropValue(unit * amount);
		ScdLog.debug("Recorded " + via + " drop " + name + " x" + amount + " for " + type.displayName());
	}

	/** Entries for a type, highest count first. */
	List<Map.Entry<String, SlayerData.Drop>> entries(SlayerType type) {
		List<Map.Entry<String, SlayerData.Drop>> out = new ArrayList<>(store.get().drops.getOrDefault(type.name(), Map.of()).entrySet());
		out.sort((a, b) -> Long.compare(b.getValue().count, a.getValue().count));
		return out;
	}

	void remove(SlayerType type, String key) {
		var drops = store.get().drops.get(type.name());
		if (drops != null && drops.remove(key) != null) store.markDirty();
	}

	void clear(SlayerType type) {
		if (store.get().drops.remove(type.name()) != null) store.markDirty();
	}

}
