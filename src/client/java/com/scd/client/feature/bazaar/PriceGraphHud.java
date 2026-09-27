package com.scd.client.feature.bazaar;

import com.scd.client.config.HudLayout;
import com.scd.client.config.ScdConfig;
import com.scd.client.hud.HudBox;
import com.scd.client.hud.HudElement;
import com.scd.client.net.Backend;
import com.scd.client.ui.Ui;
import com.scd.logic.Numbers;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Hovered item's live price, spread and a buy/sell history chart. Only visible while hovering a priced item. */
final class PriceGraphHud extends HudElement {
	private static final String PREVIEW_ITEM = "ENCHANTED_LAPIS_LAZULI";
	private static final int CHART_W = 170;
	private static final int CHART_H = 50;

	private final Supplier<ScdConfig> config;
	private final PriceService prices;
	private final HistoryCache history;
	private final HoverState hover;

	PriceGraphHud(Supplier<ScdConfig> config, PriceService prices, HistoryCache history, HoverState hover) {
		super("bazaar_graph", "Bazaar price graph", HudLayout.at(HudLayout.AnchorX.RIGHT, HudLayout.AnchorY.TOP, 8, 8));
		this.config = config;
		this.prices = prices;
		this.history = history;
		this.hover = hover;
	}

	@Override
	public boolean enabled() {
		return config.get().bazaar.graphHud;
	}

	@Override
	public HudBox build(boolean preview) {
		if (!preview && !(net.minecraft.client.Minecraft.getInstance().gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)) {
			return null; // only ever inside a menu
		}
		String id = preview ? PREVIEW_ITEM : hover.currentOrNull();
		Backend.Product p = prices.get(id);
		if (p == null) {
			if (!preview) return null;
			p = new Backend.Product(PREVIEW_ITEM, "Enchanted Lapis Lazuli", 812.4, 790.1, null);
		}
		String range = config.get().bazaar.graphRange;
		List<Backend.HistoryPoint> points = preview ? samplePoints(p) : history.get(p.itemId(), range);
		Backend.Product product = p;
		return new HudBox().minWidth(CHART_W + HudBox.PADDING * 2)
				.title(p.name())
				.text("Sell " + Numbers.coins(p.sellPrice()) + "   Buy " + Numbers.coins(p.buyPrice()))
				.text("Spread " + Numbers.percent(p.spreadPercent(), 1) + "   " + range)
				.custom(CHART_W, CHART_H + 12, (g, x, y, w, h) -> chart(g, x, y, w, CHART_H, points, product));
	}

	private static List<Backend.HistoryPoint> samplePoints(Backend.Product p) {
		List<Backend.HistoryPoint> out = new ArrayList<>();
		long now = System.currentTimeMillis();
		for (int i = 0; i < 40; i++) {
			double wave = Math.sin(i / 5.0) * 20 + i * 0.6;
			out.add(new Backend.HistoryPoint(now - (40 - i) * 3_600_000L, p.sellPrice() - 20 + wave, p.buyPrice() - 15 + wave));
		}
		return out;
	}

	private static void chart(GuiGraphicsExtractor g, int x, int y, int w, int h, List<Backend.HistoryPoint> raw, Backend.Product now) {
		int top = y + 11;
		g.fill(x, top, x + w, top + h, 0x40000000);
		if (raw.size() < 2) {
			Ui.text(g, "Collecting history...", x + 4, top + h / 2 - 4, Ui.theme().textMuted());
			return;
		}
		List<Backend.HistoryPoint> pts = smooth(raw);
		long minT = pts.get(0).timestampMs(), maxT = pts.get(pts.size() - 1).timestampMs();
		if (maxT <= minT) return;
		double min = Math.min(now.sellPrice(), now.buyPrice()), max = Math.max(now.sellPrice(), now.buyPrice());
		for (var p : pts) {
			min = Math.min(min, Math.min(p.sellPrice(), p.buyPrice()));
			max = Math.max(max, Math.max(p.sellPrice(), p.buyPrice()));
		}
		if (max <= min) max = min + 1;
		Ui.text(g, Numbers.coins(max), x, y, Ui.theme().textMuted());
		Ui.rightAligned(g, Numbers.coins(min), x + w, y, Ui.theme().textMuted());
		plot(g, x, top, w, h, pts, minT, maxT, min, max, true, Ui.SELL);
		plot(g, x, top, w, h, pts, minT, maxT, min, max, false, Ui.BUY);
		// Live instant prices at the right edge ("now"), not the possibly hour-old last candle.
		marker(g, x + w - 1, top + h - 1 - (now.sellPrice() - min) / (max - min) * (h - 1), Ui.SELL);
		marker(g, x + w - 1, top + h - 1 - (now.buyPrice() - min) / (max - min) * (h - 1), Ui.BUY);
	}

	/** Centered 3-point moving average - trims hour-to-hour noise without hiding the trend. */
	private static List<Backend.HistoryPoint> smooth(List<Backend.HistoryPoint> pts) {
		if (pts.size() < 3) return pts;
		List<Backend.HistoryPoint> out = new ArrayList<>(pts.size());
		for (int i = 0; i < pts.size(); i++) {
			int lo = Math.max(0, i - 1), hi = Math.min(pts.size() - 1, i + 1);
			double s = 0, b = 0;
			for (int j = lo; j <= hi; j++) {
				s += pts.get(j).sellPrice();
				b += pts.get(j).buyPrice();
			}
			int n = hi - lo + 1;
			out.add(new Backend.HistoryPoint(pts.get(i).timestampMs(), s / n, b / n));
		}
		return out;
	}

	private static void plot(GuiGraphicsExtractor g, int x, int y, int w, int h, List<Backend.HistoryPoint> pts,
			long minT, long maxT, double min, double max, boolean sell, int color) {
		double px = Double.NaN, py = Double.NaN;
		for (var p : pts) {
			double v = sell ? p.sellPrice() : p.buyPrice();
			double cx = x + (double) (p.timestampMs() - minT) / (maxT - minT) * (w - 1);
			double cy = y + h - 1 - (v - min) / (max - min) * (h - 1);
			if (!Double.isNaN(px)) segment(g, px, py, cx, cy, color);
			px = cx;
			py = cy;
		}
	}

	/** GUI rendering has no line primitive - step along the segment with a soft 1px anti-aliased fringe. */
	private static void segment(GuiGraphicsExtractor g, double x1, double y1, double x2, double y2, int color) {
		double dx = x2 - x1, dy = y2 - y1;
		int steps = Math.max(1, (int) Math.round(Math.max(Math.abs(dx), Math.abs(dy))));
		boolean vertical = Math.abs(dy) >= Math.abs(dx);
		for (int i = 0; i <= steps; i++) {
			double t = (double) i / steps, xf = x1 + dx * t, yf = y1 + dy * t;
			if (vertical) {
				int py = (int) Math.round(yf), xp = (int) Math.round(xf);
				double fringe = Math.abs(xf - xp);
				pixel(g, xp, py, color, 1);
				if (fringe > 0.05) pixel(g, xf >= xp ? xp + 1 : xp - 1, py, color, fringe);
			} else {
				int px = (int) Math.round(xf), yp = (int) Math.round(yf);
				double fringe = Math.abs(yf - yp);
				pixel(g, px, yp, color, 1);
				if (fringe > 0.05) pixel(g, px, yf >= yp ? yp + 1 : yp - 1, color, fringe);
			}
		}
	}

	private static void pixel(GuiGraphicsExtractor g, int x, int y, int color, double coverage) {
		int alpha = (int) Math.round((color >>> 24) * Math.max(0, Math.min(1, coverage)));
		if (alpha >= 12) g.fill(x, y, x + 1, y + 1, (alpha << 24) | (color & 0xFFFFFF));
	}

	private static void marker(GuiGraphicsExtractor g, double x, double y, int color) {
		int px = (int) Math.round(x), py = (int) Math.round(y);
		g.fill(px - 2, py - 2, px + 3, py + 3, 0xFFFFFFFF);
		g.fill(px - 1, py - 1, px + 2, py + 2, color);
	}
}
