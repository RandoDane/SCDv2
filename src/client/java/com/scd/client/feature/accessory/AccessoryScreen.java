package com.scd.client.feature.accessory;

import com.scd.client.ScdMod;
import com.scd.client.net.Backend;
import com.scd.client.ui.Rows;
import com.scd.client.ui.ScdScreen;
import com.scd.client.ui.Ui;
import com.scd.logic.Numbers;
import net.minecraft.client.gui.screens.Screen;

import java.util.Comparator;
import java.util.List;

/** Owned and missing accessories from the profile lookup, missing ones priced from the market. */
public final class AccessoryScreen extends ScdScreen {
	private final ScdMod mod;
	private final AccessoryFeature feature;
	private boolean showMissing = true;
	private AccessoryService.Status lastStatus;

	public AccessoryScreen(Screen parent, ScdMod mod, AccessoryFeature feature) {
		super("Accessories", parent, 330);
		this.mod = mod;
		this.feature = feature;
	}

	@Override
	protected void init() {
		if (feature.service().status() == AccessoryService.Status.IDLE) feature.service().refresh();
		lastStatus = feature.service().status();
		super.init();
	}

	@Override
	public void tick() {
		if (feature.service().status() != lastStatus) {
			lastStatus = feature.service().status();
			rebuild();
		}
	}

	@Override
	protected void onClosing() {
		mod.configManager.save();
	}

	@Override
	protected List<FooterButton> footer() {
		return List.of(new FooterButton("Refresh", () -> {
			feature.service().refresh();
			rebuild();
		}));
	}

	@Override
	protected void build(Rows rows) {
		var c = mod.config().accessories;
		rows.toggle("Bag overlay", "Missing-accessories panel next to the in-game Accessory Bag", () -> c.bagOverlay, v -> c.bagOverlay = v);
		AccessoryService s = feature.service();
		switch (s.status()) {
			case IDLE, LOADING -> {
				rows.note("Loading your accessory bag...");
				return;
			}
			case ERROR -> {
				rows.line(() -> "Failed: " + s.error(), () -> Ui.DANGER);
				rows.note("Needs a reachable SCD backend with a Hypixel API key (Settings → Backend).");
				return;
			}
			default -> {
			}
		}
		Backend.AccessorySummary sum = s.summary();
		rows.value("Accessory Power", () -> sum.accessoryPower() != null ? String.valueOf(sum.accessoryPower()) : "?");
		if (sum.peakMagicalPower() != null) rows.value("Peak ever", () -> String.valueOf(sum.peakMagicalPower()));
		if (feature.scanner().complete()) rows.value("Live bag scan", () -> String.valueOf(feature.scanner().totalPower()));
		rows.cycle("Show", List.of(true, false), () -> showMissing, v -> {
			showMissing = v;
			rebuild();
		}, v -> v ? "Missing (" + sum.missing().size() + ")" : "Owned (" + sum.accessoryCount() + ")");

		int x0 = rows.x();
		int w = rows.width();
		if (showMissing) {
			var list = sum.missing().stream()
					.sorted(Comparator.comparingDouble((Backend.MissingAccessory m) -> {
						Double v = s.coinsPerPower(m);
						return v != null ? v : Double.POSITIVE_INFINITY;
					}))
					.toList();
			rows.note("Sorted by coins per Magical Power - the cheapest power first.");
			for (var m : list) {
				Double price = s.price(m);
				Double per = s.coinsPerPower(m);
				rows.custom(11, (g, x, y, rw, mx, my) -> {
					Ui.text(g, Ui.ellipsize(m.name() + (m.upgrade() ? " ↑" : ""), w - 120), x0, y + 1, Ui.rarityColor(m.tier()));
					Ui.rightAligned(g, "+" + m.magicalPowerGain(), x0 + w - 90, y + 1, Ui.theme().textSecondary());
					Ui.rightAligned(g, price != null ? Numbers.coins(price) : "?", x0 + w - 40, y + 1, Ui.GOLD);
					Ui.rightAligned(g, per != null ? Numbers.coins(per) : "", x0 + w, y + 1, Ui.theme().textMuted());
				}, List.of());
			}
		} else {
			for (var a : sum.accessories()) {
				rows.custom(11, (g, x, y, rw, mx, my) -> {
					Ui.text(g, Ui.ellipsize(a.name() + (a.count() > 1 ? " x" + a.count() : ""), w - 50), x0, y + 1, Ui.rarityColor(a.rarity()));
					Ui.rightAligned(g, a.magicalPower() + " MP", x0 + w, y + 1, Ui.theme().textSecondary());
				}, List.of());
			}
		}
	}
}
