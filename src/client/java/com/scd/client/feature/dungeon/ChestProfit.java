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
 * menu): every item priced through scd.wtf (Bazaar insta-sell or sell offer; Auction House lowest
 * BIN or estimate, names resolved with auction search), minus the coin cost and the key. Shown as
 * an item-by-item tooltip, a panel beside the menu with every chest by profit, and the best chest
 * outlined green (a second one yellow when it pays for its key).
 */
final class ChestProfit {
	/** Croesus run page ("Master Catacombs - Floor VII") or an opened reward chest ("Obsidian Chest"). */
	private static final Pattern CROESUS_RUN = Pattern.compile("^(?:Master )?(?:The )?Catacombs - (?:Floor .+|Entrance)$");
	private static final String KEY_ID = "DUNGEON_CHEST_KEY";

	/** One line of a chest: the item and its value (null while the price is loading/unknown). */
	record Line(String name, int amount, Double value) {
	}

	record Valued(double value, double cost, boolean complete, List<Line> lines) {
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

	/** Coin value of one unit, by the chosen price basis; null while unknown. */
	private Double unit(String name) {
		String id = market.idForName(name);
		if (id == null) return null;
		var c = mod.config().dungeon;
		var bz = market.prices().get(id);
		if (bz != null) {
			double v = c.chestBazaarPrice.equals("Sell offer") ? bz.buyPrice() : bz.sellPrice();
			if (v > 0) return v;
		}
		var ah = market.auctions().get(id);
		if (ah == null) return null;
		Double lbin = ah.lbin(), estimate = ah.price();
		return c.chestAuctionPrice.equals("Estimate") ? (estimate != null ? estimate : lbin) : (lbin != null ? lbin : estimate);
	}

	/** Value of a chest item (Croesus head or run-end chest button) from its lore; null if not a chest. */
	Valued valueOfLore(List<String> lore) {
		ChestLoot.Chest chest = ChestLoot.parse(lore);
		if (chest == null) return null;
		double value = 0;
		boolean complete = true;
		List<Line> lines = new java.util.ArrayList<>();
		for (ChestLoot.Entry e : chest.contents()) {
			Double u = unit(e.name());
			Double v = u != null ? u * e.amount() : null;
			lines.add(new Line(e.name(), e.amount(), v));
			if (v == null) complete = false;
			else value += v;
		}
		double cost = chest.coinCost();
		if (chest.needsKey()) {
			Double key = market.buyPrice(KEY_ID);
			if (key != null) cost += key;
			else complete = false;
		}
		return new Valued(value, cost, complete, lines);
	}

	private void tooltip(ItemStack stack, List<Component> lines) {
		if (!enabled() || !mod.config().dungeon.chestProfitTooltip || !(Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen<?> cs)) return;
		if (!relevantTitle(Text.clean(cs.getTitle().getString()))) return;
		Valued v = valueOfLore(Items.lore(stack));
		if (v == null) return;
		lines.add(Component.empty());
		for (Line l : v.lines()) {
			lines.add(Component.literal(" " + l.name() + (l.amount() > 1 ? " x" + l.amount() : "") + "  ").withStyle(ChatFormatting.GRAY)
					.append(Component.literal(l.value() != null ? Numbers.compactCoins(l.value()) : "?").withStyle(l.value() != null ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY)));
		}
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
		List<Object[]> all = new java.util.ArrayList<>();
		for (Slot slot : cs.getMenu().slots) {
			if (slot.container instanceof Inventory) continue;
			ItemStack stack = slot.getItem();
			if (stack.isEmpty() || !ChestLoot.CHEST_NAME.matcher(Items.name(stack)).matches()) continue;
			Valued v = valueOfLore(Items.lore(stack));
			if (v == null) continue;
			all.add(new Object[]{Items.name(stack), v});
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
		var c = mod.config().dungeon;
		if (c.chestProfitHighlight && bestP > 0) frame(g, left + best.x, top + best.y, Ui.SUCCESS);
		// A second chest is only worth a key if it clearly pays for it.
		Double key = market.buyPrice(KEY_ID);
		if (c.chestProfitHighlight && second != null && key != null && secondP > key) frame(g, left + second.x, top + second.y, Ui.WARNING);
		if (c.chestProfitPanel) panel(cs, g, all, left, top);
		if (!c.chestProfitLabel) return;
		String label = "Best: " + Items.name(best.getItem()) + "  " + (bestP >= 0 ? "+" : "") + Numbers.compactCoins(bestP);
		Ui.text(g, label, left, top - 11, bestP >= 0 ? Ui.SUCCESS : Ui.DANGER);
	}

	/** Every chest by profit, left of the menu (right of it when there's no room). */
	private void panel(AbstractContainerScreen<?> cs, GuiGraphicsExtractor g, List<Object[]> all, int left, int top) {
		all.sort((a, b) -> Double.compare(((Valued) b[1]).profit(), ((Valued) a[1]).profit()));
		// As wide as its text; on the side of the menu with more room, and always on screen.
		int w = Ui.widthBold("Chest profit") + 12;
		for (Object[] row : all) {
			Valued v = (Valued) row[1];
			w = Math.max(w, Ui.width((String) row[0]) + Ui.width("+" + Numbers.compactCoins(v.profit()) + "?") + 18);
			w = Math.max(w, Ui.width(Numbers.compactCoins(v.value()) + " value · " + Numbers.compactCoins(v.cost()) + " cost") + 12);
		}
		int rowH = 22, h = 18 + all.size() * rowH;
		int right = left + ((ContainerScreenAccessor) cs).scd$imageWidth();
		int x = left >= cs.width - right ? left - w - 6 : right + 6;
		x = Math.max(2, Math.min(x, cs.width - w - 2));
		var t = Ui.theme();
		Ui.rect(g, x, top, w, h, 4, t.window(), t.border());
		Ui.bold(g, "Chest profit", x + 6, top + 5, t.textPrimary());
		int y = top + 18;
		for (Object[] row : all) {
			Valued v = (Valued) row[1];
			double p = v.profit();
			Ui.text(g, (String) row[0], x + 6, y, t.textPrimary());
			Ui.rightAligned(g, (p >= 0 ? "+" : "") + Numbers.compactCoins(p) + (v.complete() ? "" : "?"), x + w - 6, y, p >= 0 ? Ui.SUCCESS : Ui.DANGER);
			Ui.text(g, Numbers.compactCoins(v.value()) + " value · " + Numbers.compactCoins(v.cost()) + " cost", x + 6, y + 10, t.textMuted());
			y += rowH;
		}
	}

	private static void frame(GuiGraphicsExtractor g, int x, int y, int color) {
		g.outline(x - 1, y - 1, 18, 18, color);
		g.fill(x, y, x + 16, y + 16, (color & 0x00FFFFFF) | 0x40000000);
	}
}
