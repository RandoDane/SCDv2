package com.scd.client.feature.dungeon;

import com.scd.client.ScdMod;
import com.scd.client.core.Events;
import com.scd.client.core.ScdLog;
import com.scd.client.feature.bazaar.BazaarFeature;
import com.scd.client.hypixel.Items;
import com.scd.client.mixin.ContainerScreenAccessor;
import com.scd.client.ui.Ui;
import com.scd.logic.Numbers;
import com.scd.logic.Text;
import com.scd.logic.dungeon.loot.ChestLoot;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Dungeon chest profit, from the lore Hypixel shows on each chest (Croesus and the run-end chest
 * menu) and from the items inside an opened chest: contents value minus coin cost and key, on the
 * chest's tooltip, with the best chest outlined green and a worthwhile second one yellow.
 */
final class ChestProfit {
	/** Croesus run page ("Master Catacombs - Floor VII") or an opened reward chest ("Obsidian Chest"). */
	private static final Pattern CROESUS_RUN = Pattern.compile("^(?:Master )?(?:The )?Catacombs - (?:Floor .+|Entrance)$");
	private static final String KEY_ID = "DUNGEON_CHEST_KEY";

	record Valued(double value, double cost, boolean complete) {
		double profit() {
			return value - cost;
		}
	}

	private final ScdMod mod;
	private final BazaarFeature market;

	ChestProfit(ScdMod mod) {
		this.mod = mod;
		this.market = mod.feature(BazaarFeature.class);
		mod.bus.subscribe(Events.ScreenOpened.class, e -> onScreen(e.screen()));
		ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> ScdLog.guard("chest profit tooltip", () -> tooltip(stack, lines)));
	}

	private boolean enabled() {
		return mod.config().dungeon.chestProfit;
	}

	private static boolean relevantTitle(String title) {
		return CROESUS_RUN.matcher(title).matches() || ChestLoot.CHEST_NAME.matcher(title).matches();
	}

	private void onScreen(Screen screen) {
		if (!(screen instanceof AbstractContainerScreen<?> cs) || !relevantTitle(Text.clean(screen.getTitle().getString()))) return;
		ScreenEvents.afterExtract(screen).register((s, g, mx, my, pt) -> {
			if (enabled()) ScdLog.guard("chest profit overlay", () -> overlay(cs, g));
		});
	}

	/** Value of a chest item (Croesus head or run-end chest button) from its lore; null if not a chest. */
	Valued valueOfLore(List<String> lore) {
		ChestLoot.Chest chest = ChestLoot.parse(lore);
		if (chest == null) return null;
		double value = 0;
		boolean complete = true;
		for (ChestLoot.Entry e : chest.contents()) {
			Double v = market.valueOfName(e.name(), e.amount());
			if (v == null) complete = false;
			else value += v;
		}
		double cost = chest.coinCost();
		if (chest.needsKey()) {
			Double key = market.buyPrice(KEY_ID);
			if (key != null) cost += key;
			else complete = false;
		}
		return new Valued(value, cost, complete);
	}

	private void tooltip(ItemStack stack, List<Component> lines) {
		if (!enabled() || !(Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen<?> cs)) return;
		if (!relevantTitle(Text.clean(cs.getTitle().getString()))) return;
		Valued v = valueOfLore(Items.lore(stack));
		if (v == null) return;
		lines.add(Component.literal("Chest value: ").withStyle(ChatFormatting.GRAY)
				.append(Component.literal(Numbers.compactCoins(v.value()) + (v.complete() ? "" : "+")).withStyle(ChatFormatting.GOLD))
				.append(Component.literal("  cost " + Numbers.compactCoins(v.cost())).withStyle(ChatFormatting.GRAY)));
		lines.add(Component.literal("Profit: ").withStyle(ChatFormatting.GRAY)
				.append(Component.literal((v.profit() >= 0 ? "+" : "") + Numbers.compactCoins(v.profit()) + (v.complete() ? "" : " (pricing...)"))
						.withStyle(v.profit() >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED)));
	}

	private void overlay(AbstractContainerScreen<?> cs, GuiGraphicsExtractor g) {
		int left = ((ContainerScreenAccessor) cs).scd$leftPos(), top = ((ContainerScreenAccessor) cs).scd$topPos();
		Slot best = null, second = null;
		double bestP = Double.NEGATIVE_INFINITY, secondP = Double.NEGATIVE_INFINITY;
		for (Slot slot : cs.getMenu().slots) {
			if (slot.container instanceof Inventory) continue;
			ItemStack stack = slot.getItem();
			if (stack.isEmpty() || !ChestLoot.CHEST_NAME.matcher(Items.name(stack)).matches()) continue;
			Valued v = valueOfLore(Items.lore(stack));
			if (v == null) continue;
			double p = v.profit();
			if (p > bestP) {
				second = best;
				secondP = bestP;
				best = slot;
				bestP = p;
			} else if (p > secondP) {
				second = slot;
				secondP = p;
			}
		}
		if (best == null) return;
		if (bestP > 0) frame(g, left + best.x, top + best.y, Ui.SUCCESS);
		// A second chest is only worth a key if it clearly pays for it.
		Double key = market.buyPrice(KEY_ID);
		if (second != null && key != null && secondP > key) frame(g, left + second.x, top + second.y, Ui.WARNING);
		String label = "Best: " + Items.name(best.getItem()) + "  " + (bestP >= 0 ? "+" : "") + Numbers.compactCoins(bestP);
		Ui.text(g, label, left, top - 11, bestP >= 0 ? Ui.SUCCESS : Ui.DANGER);
	}

	private static void frame(GuiGraphicsExtractor g, int x, int y, int color) {
		g.outline(x - 1, y - 1, 18, 18, color);
		g.fill(x, y, x + 16, y + 16, (color & 0x00FFFFFF) | 0x40000000);
	}
}
