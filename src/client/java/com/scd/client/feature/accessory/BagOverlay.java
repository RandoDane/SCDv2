package com.scd.client.feature.accessory;

import com.scd.client.config.ScdConfig;
import com.scd.client.hypixel.ItemIcons;
import com.scd.client.net.Backend;
import com.scd.client.ui.Theme;
import com.scd.client.ui.Ui;
import com.scd.client.ui.widget.FlatButton;
import com.scd.logic.Numbers;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

/**
 * Panel drawn beside the vanilla Accessory Bag: live scan progress and exact Accessory Power, then
 * a paged icon grid of what's still missing, sortable by rarity (MAX), price, or coins per Magical
 * Power (VALUE - the best upgrades for the money). The vanilla screen isn't ours, so the buttons are
 * long-lived widgets positioned and drawn every frame, with clicks intercepted before they reach it.
 */
final class BagOverlay {
	enum Sort { MAX, PRICE, VALUE }

	private static final int COLS = 8;
	private static final int CELL = 20;
	private static final int GAP = 2;
	private static final int PAD = 10;
	private static final int WIDTH = PAD * 2 + COLS * CELL + (COLS - 1) * GAP;

	private final AccessoryService service;
	private final BagScanner scanner;
	private final Supplier<ScdConfig> config;
	private int page;
	private final FlatButton sortButton;
	private final FlatButton prev;
	private final FlatButton next;
	private final FlatButton retry;

	BagOverlay(AccessoryService service, BagScanner scanner, Supplier<ScdConfig> config) {
		this.service = service;
		this.scanner = scanner;
		this.config = config;
		this.sortButton = new FlatButton(0, 0, 70, 14, "", this::cycleSort);
		this.prev = new FlatButton(0, 0, 40, 14, "‹ Prev", () -> page--);
		this.next = new FlatButton(0, 0, 40, 14, "Next ›", () -> page++);
		this.retry = new FlatButton(0, 0, 60, 14, "Retry", service::refresh);
	}

	void resetPage() {
		page = 0;
	}

	private Sort sort() {
		try {
			return Sort.valueOf(config.get().accessories.sort);
		} catch (IllegalArgumentException e) {
			return Sort.MAX;
		}
	}

	private void cycleSort() {
		config.get().accessories.sort = Sort.values()[(sort().ordinal() + 1) % Sort.values().length].name();
		page = 0;
	}

	/** Missing list minus anything the live scan has already seen (the profile snapshot goes stale mid-session). */
	private List<Backend.MissingAccessory> missing() {
		List<Backend.MissingAccessory> list = new ArrayList<>();
		for (var m : service.missing()) if (!scanner.has(m.name())) list.add(m);
		Comparator<Backend.MissingAccessory> order = switch (sort()) {
			case MAX -> Comparator.comparingInt((Backend.MissingAccessory m) -> Ui.rarityRank(m.tier())).reversed();
			case PRICE -> Comparator.comparingDouble(m -> orInf(service.price(m)));
			case VALUE -> Comparator.comparingDouble(m -> orInf(service.coinsPerPower(m)));
		};
		list.sort(order.thenComparing(Backend.MissingAccessory::name));
		return list;
	}

	private static double orInf(Double d) {
		return d != null ? d : Double.POSITIVE_INFINITY;
	}

	void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float pt, int screenHeight) {
		Theme t = Ui.theme();
		int x = 8, y = 8;
		int lh = Ui.lineHeight() + 3;
		boolean loaded = service.status() == AccessoryService.Status.LOADED;
		List<Backend.MissingAccessory> missing = loaded ? missing() : List.of();

		int fixed = PAD + 17 + lh * 3 + 8 + 18 + 30 + PAD;
		int maxRows = Math.max(1, Math.min(5, (screenHeight - 16 - fixed) / (CELL + GAP)));
		int perPage = maxRows * COLS;
		int pages = Math.max(1, (missing.size() + perPage - 1) / perPage);
		page = Math.max(0, Math.min(page, pages - 1));
		int from = page * perPage, to = Math.min(missing.size(), from + perPage);
		int gridRows = loaded ? Math.max(1, (to - from + COLS - 1) / COLS) : 0;
		boolean nav = pages > 1;
		int height = fixed - 30 + gridRows * (CELL + GAP) + (nav ? 20 : 0) + (loaded ? 0 : 10);

		Ui.panel(g, x, y, WIDTH, height);
		Ui.title(g, "Accessories", x + PAD, y + PAD - 1, t.textPrimary());
		Ui.rightAligned(g, "SCD", x + WIDTH - PAD, y + PAD + 2, t.textMuted());
		g.fill(x + 1, y + PAD + 17, x + WIDTH - 1, y + PAD + 18, t.border());
		int cy = y + PAD + 24;
		Ui.text(g, "Scanned " + scanner.count() + " · page " + scanner.pagesSeen() + "/" + Math.max(1, scanner.totalPages()), x + PAD, cy, t.textSecondary());
		cy += lh;
		if (scanner.complete()) {
			Ui.text(g, "Accessory Power: ", x + PAD, cy, t.textSecondary());
			Ui.text(g, String.valueOf(scanner.totalPower()), x + PAD + Ui.width("Accessory Power: "), cy, t.accent());
		} else {
			Ui.text(g, "Browse every page to finish the scan", x + PAD, cy, t.textMuted());
		}
		cy += lh;
		var sum = service.summary();
		if (sum != null && sum.peakMagicalPower() != null) Ui.text(g, "Peak ever: " + sum.peakMagicalPower(), x + PAD, cy, t.textMuted());
		cy += lh + 3;

		String header = switch (service.status()) {
			case IDLE, LOADING -> "Missing: loading...";
			case ERROR -> "Missing: " + Ui.ellipsize(service.error() != null ? service.error() : "unavailable", WIDTH - 90);
			case LOADED -> "Missing (" + missing.size() + ")";
		};
		Ui.text(g, header, x + PAD, cy + 3, t.textPrimary());
		sortButton.visible = loaded && !missing.isEmpty();
		retry.visible = service.status() == AccessoryService.Status.ERROR;
		FlatButton right = retry.visible ? retry : sortButton;
		right.setX(x + WIDTH - PAD - right.getWidth());
		right.setY(cy);
		sortButton.setMessage(Component.literal(switch (sort()) {
			case MAX -> "Rarity";
			case PRICE -> "Price ↑";
			case VALUE -> "Coins/MP";
		}));
		if (right.visible) right.extractRenderState(g, mouseX, mouseY, pt);
		cy += 18;

		Backend.MissingAccessory hovered = null;
		for (int i = from; i < to; i++) {
			int idx = i - from;
			int bx = x + PAD + (idx % COLS) * (CELL + GAP);
			int by = cy + (idx / COLS) * (CELL + GAP);
			var m = missing.get(i);
			boolean hover = Ui.contains(bx, by, CELL, CELL, mouseX, mouseY);
			Ui.rect(g, bx, by, CELL, CELL, 4, hover ? t.cardHover() : t.card(),
					hover ? t.accent() : Ui.blend(t.border(), Ui.rarityColor(m.tier()), 0.45f));
			g.item(ItemIcons.of(m.icon()), bx + 2, by + 2);
			if (hover) hovered = m;
		}
		cy += gridRows * (CELL + GAP);

		prev.visible = next.visible = nav;
		if (nav) {
			int half = (WIDTH - PAD * 2 - 40) / 2;
			prev.setX(x + PAD);
			prev.setWidth(half);
			next.setX(x + WIDTH - PAD - half);
			next.setWidth(half);
			prev.setY(cy + 2);
			next.setY(cy + 2);
			prev.active = page > 0;
			next.active = page < pages - 1;
			Ui.centered(g, (page + 1) + "/" + pages, x + WIDTH / 2, cy + 6, t.textMuted());
			prev.extractRenderState(g, mouseX, mouseY, pt);
			next.extractRenderState(g, mouseX, mouseY, pt);
		}
		if (hovered != null) tooltip(g, hovered, mouseX, mouseY);
	}

	private void tooltip(GuiGraphicsExtractor g, Backend.MissingAccessory m, int mx, int my) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.literal(m.name() + (m.upgrade() ? " (upgrade)" : "")).withStyle(s -> s.withColor(TextColor.fromRgb(Ui.rarityColor(m.tier()) & 0xFFFFFF))));
		Double price = service.price(m);
		Double per = service.coinsPerPower(m);
		lines.add(Component.literal("Price: " + (price != null ? Numbers.coins(price) : "unknown")).withStyle(ChatFormatting.GOLD));
		lines.add(Component.literal("+" + m.magicalPowerGain() + " MP" + (per != null ? " · " + Numbers.coins(per) + " per MP" : "")).withStyle(ChatFormatting.AQUA));
		if (m.requirement() != null) lines.add(Component.literal("Requires: " + m.requirement()).withStyle(ChatFormatting.RED));
		if (m.obtainMethod() != null) lines.add(Component.literal("Obtain: " + m.obtainMethod()).withStyle(ChatFormatting.GRAY));
		g.setComponentTooltipForNextFrame(Ui.font(), lines, mx, my);
	}

	/** True if the click hit one of our buttons (and must not reach the vanilla screen). */
	boolean click(MouseButtonEvent event) {
		if (event.button() != 0) return false;
		for (FlatButton b : List.of(sortButton, retry, prev, next)) {
			if (b.visible && b.isActive() && b.isMouseOver(event.x(), event.y())) {
				b.onClick(event, false);
				return true;
			}
		}
		return false;
	}
}
