package com.scd.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

import java.util.Locale;

/**
 * Drawing primitives for every SCD surface.
 *
 * Text uses the bundled Poppins TTF (smooth, anti-aliased) without Minecraft's hard black drop
 * shadow - the pixel font plus shadow is what made the old UI feel "sharp". Rounded rectangles are
 * rasterized at the monitor's real pixel resolution (not GUI pixels) with anti-aliased corners, so
 * they stay soft at every GUI scale. No drop shadows anywhere: depth comes from surface lightness
 * and hairline borders.
 */
public final class Ui {
	public static final int DANGER = 0xFFFF6B6B;
	public static final int WARNING = 0xFFFFA45B;
	public static final int SUCCESS = 0xFF4ADE80;
	public static final int SELL = 0xFF4ADE80;
	public static final int BUY = 0xFFFFA45B;
	public static final int GOLD = 0xFFFFC857;

	/** Shared spacing so every surface lines up. */
	public static final int PAD = 12;
	public static final int GAP = 5;
	public static final int CONTROL_H = 18;
	public static final int RADIUS = 5;
	public static final int RADIUS_SMALL = 4;

	public static final FontDescription REGULAR = new FontDescription.Resource(Identifier.fromNamespaceAndPath("scd", "ui"));
	public static final FontDescription BOLD = new FontDescription.Resource(Identifier.fromNamespaceAndPath("scd", "ui_bold"));
	public static final FontDescription TITLE = new FontDescription.Resource(Identifier.fromNamespaceAndPath("scd", "ui_title"));

	private static final String[] RARITIES = {"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE", "SPECIAL", "VERY SPECIAL"};
	private static final int[] RARITY_COLORS = {0xFFFFFFFF, 0xFF55FF55, 0xFF5555FF, 0xFFAA00AA, 0xFFFFAA00, 0xFFFF55FF, 0xFF55FFFF, 0xFFFF5555, 0xFFFF5555};

	private static volatile Theme theme = Theme.PRESETS.get(0);
	private static volatile boolean smoothFont = true;

	private Ui() {
	}

	public static Theme theme() {
		return theme;
	}

	public static void setTheme(Theme newTheme) {
		theme = newTheme;
	}

	public static void setSmoothFont(boolean enabled) {
		smoothFont = enabled;
	}

	public static Font font() {
		return Minecraft.getInstance().font;
	}

	// ---- text -----------------------------------------------------------------------------------

	public static MutableComponent styled(String text, FontDescription face) {
		MutableComponent c = Component.literal(text);
		return smoothFont ? c.withStyle(s -> s.withFont(face)) : c;
	}

	public static void text(GuiGraphicsExtractor g, String text, int x, int y, int color) {
		g.text(font(), styled(text, REGULAR), x, y, color, false);
	}

	public static void text(GuiGraphicsExtractor g, Component text, int x, int y, int color) {
		g.text(font(), text, x, y, color, false);
	}

	public static void bold(GuiGraphicsExtractor g, String text, int x, int y, int color) {
		g.text(font(), styled(text, BOLD), x, y, color, false);
	}

	/** Large heading (about 1.5x body size). */
	public static void title(GuiGraphicsExtractor g, String text, int x, int y, int color) {
		g.text(font(), styled(text, TITLE), x, y, color, false);
	}

	public static void centered(GuiGraphicsExtractor g, String text, int centerX, int y, int color) {
		text(g, text, centerX - width(text) / 2, y, color);
	}

	public static void rightAligned(GuiGraphicsExtractor g, String text, int rightX, int y, int color) {
		text(g, text, rightX - width(text), y, color);
	}

	/** Small uppercase label, like a section caption. */
	public static void section(GuiGraphicsExtractor g, String text, int x, int y) {
		bold(g, text.toUpperCase(Locale.ROOT), x, y, theme.textMuted());
	}

	public static int width(String text) {
		return font().width(styled(text, REGULAR));
	}

	public static int widthBold(String text) {
		return font().width(styled(text, BOLD));
	}

	public static int widthTitle(String text) {
		return font().width(styled(text, TITLE));
	}

	public static int lineHeight() {
		return font().lineHeight;
	}

	/** Shortens with an ellipsis so the text fits {@code maxWidth}. */
	public static String ellipsize(String text, int maxWidth) {
		if (width(text) <= maxWidth) return text;
		String ell = "…";
		int lo = 0, hi = text.length();
		while (lo < hi) {
			int mid = (lo + hi + 1) / 2;
			if (width(text.substring(0, mid) + ell) <= maxWidth) lo = mid;
			else hi = mid - 1;
		}
		return text.substring(0, lo) + ell;
	}

	// ---- shapes ---------------------------------------------------------------------------------

	/** Anti-aliased rounded rectangle filled with {@code color}, in current GUI coordinates. */
	public static void rect(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, int color) {
		withPixels(g, (k) -> fillRoundPx(g, Math.round(x * k), Math.round(y * k), Math.round(w * k), Math.round(h * k), radius * k, color));
	}

	/** Rounded rectangle with a 1-device-pixel hairline border. */
	public static void rect(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, int fill, int border) {
		withPixels(g, (k) -> {
			int X = Math.round(x * k), Y = Math.round(y * k), W = Math.round(w * k), H = Math.round(h * k);
			float r = radius * k;
			fillRoundPx(g, X, Y, W, H, r, border);
			fillRoundPx(g, X + 1, Y + 1, W - 2, H - 2, Math.max(0, r - 1), fill);
		});
	}

	/**
	 * Anti-aliased rounded outline only (1 device pixel wide) - for selection/focus rings drawn over
	 * content that must stay visible inside.
	 */
	public static void ring(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, int color) {
		withPixels(g, (k) -> {
			int X = Math.round(x * k), Y = Math.round(y * k), W = Math.round(w * k), H = Math.round(h * k);
			int R = (int) Math.min(Math.floor(radius * k), Math.min(W, H) / 2);
			g.fill(X + R, Y, X + W - R, Y + 1, color);
			g.fill(X + R, Y + H - 1, X + W - R, Y + H, color);
			g.fill(X, Y + R, X + 1, Y + H - R, color);
			g.fill(X + W - 1, Y + R, X + W, Y + H - R, color);
			int alpha = color >>> 24, rgb = color & 0xFFFFFF;
			for (int py = 0; py < R; py++) {
				for (int px = 0; px < R; px++) {
					double dx = R - (px + 0.5), dy = R - (py + 0.5);
					double d = Math.abs(Math.sqrt(dx * dx + dy * dy) - (R - 0.5));
					double cov = 1 - d;
					if (cov <= 0.03) continue;
					int c = ((int) Math.round(alpha * Math.min(1, cov)) << 24) | rgb;
					g.fill(X + px, Y + py, X + px + 1, Y + py + 1, c);
					g.fill(X + W - 1 - px, Y + py, X + W - px, Y + py + 1, c);
					g.fill(X + px, Y + H - 1 - py, X + px + 1, Y + H - py, c);
					g.fill(X + W - 1 - px, Y + H - 1 - py, X + W - px, Y + H - py, c);
				}
			}
		});
	}

	/** The main window surface. */
	public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		rect(g, x, y, w, h, 8, theme.window(), theme.border());
	}

	/** A raised card; hover lightens the surface. */
	public static void card(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean hovered) {
		rect(g, x, y, w, h, RADIUS, hovered ? theme.cardHover() : theme.card(), theme.border());
	}

	/** HUD box background (slightly translucent, no border, no shadow). {@code color} is ARGB. */
	public static void hudPanel(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		rect(g, x, y, w, h, 6, color);
	}

	public static void divider(GuiGraphicsExtractor g, int x, int y, int width) {
		g.fill(x, y, x + width, y + 1, theme.border());
	}

	/** Rounded progress bar. */
	public static void bar(GuiGraphicsExtractor g, int x, int y, int w, int h, float frac, int color) {
		float r = h / 2f;
		rect(g, x, y, w, h, r, 0x50000000 | (theme.trackOff() & 0xFFFFFF));
		float filled = w * Math.max(0, Math.min(1, frac));
		if (filled >= 1) rect(g, x, y, Math.max(h, filled), h, r, color);
	}

	private interface PixelDraw {
		void draw(float pixelsPerUnit);
	}

	/** Switches the pose so one unit is one physical pixel, then draws. */
	private static void withPixels(GuiGraphicsExtractor g, PixelDraw draw) {
		var pose = g.pose();
		float k = pose.m00() * Minecraft.getInstance().getWindow().getGuiScale();
		if (k <= 0) return;
		pose.pushMatrix();
		try {
			pose.scale(1f / k, 1f / k);
			draw.draw(k);
		} finally {
			pose.popMatrix();
		}
	}

	private static void fillRoundPx(GuiGraphicsExtractor g, int X, int Y, int W, int H, float radius, int color) {
		if (W <= 0 || H <= 0) return;
		int R = (int) Math.min(Math.floor(radius), Math.min(W, H) / 2);
		if (R <= 0) {
			g.fill(X, Y, X + W, Y + H, color);
			return;
		}
		g.fill(X, Y + R, X + W, Y + H - R, color);
		int alpha = color >>> 24;
		int rgb = color & 0xFFFFFF;
		for (int i = 0; i < R; i++) {
			double dy = R - (i + 0.5);
			double inset = R - Math.sqrt(Math.max(0, (double) R * R - dy * dy));
			int full = (int) Math.ceil(inset);
			int partial = (int) Math.floor(inset);
			double coverage = full - inset;
			int top = Y + i;
			int bottom = Y + H - 1 - i;
			if (W - 2 * full > 0) {
				g.fill(X + full, top, X + W - full, top + 1, color);
				g.fill(X + full, bottom, X + W - full, bottom + 1, color);
			}
			if (partial < full && coverage > 0.03) {
				int a = (int) Math.round(alpha * coverage);
				int c = (a << 24) | rgb;
				g.fill(X + partial, top, X + partial + 1, top + 1, c);
				g.fill(X + W - 1 - partial, top, X + W - partial, top + 1, c);
				g.fill(X + partial, bottom, X + partial + 1, bottom + 1, c);
				g.fill(X + W - 1 - partial, bottom, X + W - partial, bottom + 1, c);
			}
		}
	}

	// ---- colors ---------------------------------------------------------------------------------

	/** Linear blend of two opaque colors (t = 0 gives a, 1 gives b); keeps a's alpha. */
	public static int blend(int a, int b, float t) {
		int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
		int gg = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
		int bb = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
		return (a & 0xFF000000) | (r << 16) | (gg << 8) | bb;
	}

	/** Hypixel's own tooltip color for a rarity; secondary text color for unknown. */
	public static int rarityColor(String rarity) {
		int rank = rarityRank(rarity);
		return rank >= 0 ? RARITY_COLORS[rank] : theme.textSecondary();
	}

	public static int rarityRank(String rarity) {
		if (rarity == null) return -1;
		String upper = rarity.toUpperCase(Locale.ROOT);
		for (int i = 0; i < RARITIES.length; i++) if (RARITIES[i].equals(upper)) return i;
		return -1;
	}

	public static boolean contains(int x, int y, int w, int h, double mx, double my) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}
}
