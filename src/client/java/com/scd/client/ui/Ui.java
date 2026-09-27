package com.scd.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Locale;

/**
 * Drawing primitives shared by every SCD screen and HUD, reading the single active {@link Theme}.
 * Semantic colors (danger, warning, success, buy/sell) are fixed on purpose - they carry meaning
 * that must not change with the theme.
 */
public final class Ui {
	public static final int SHADOW = 0x4D000000;
	public static final int DANGER = 0xFFFF5555;
	public static final int WARNING = 0xFFFF8855;
	public static final int SUCCESS = 0xFF55FF55;
	public static final int SELL = 0xFF55FF55;
	public static final int BUY = 0xFFFFAA55;
	public static final int GOLD = 0xFFFFAA00;

	private static final Identifier HUD_PANEL = Identifier.fromNamespaceAndPath("scd", "hud/panel");
	private static final int HUD_SHADOW_SPREAD = 2;

	private static final String[] RARITIES = {"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE", "SPECIAL", "VERY SPECIAL"};
	private static final int[] RARITY_COLORS = {0xFFFFFFFF, 0xFF55FF55, 0xFF5555FF, 0xFFAA00AA, 0xFFFFAA00, 0xFFFF55FF, 0xFF55FFFF, 0xFFFF5555, 0xFFFF5555};

	private static volatile Theme theme = Theme.PRESETS.get(0);

	/** Shared spacing so every surface lines up: outer padding, gap between rows, control height. */
	public static final int PAD = 12;
	public static final int GAP = 4;
	public static final int CONTROL_H = 16;
	public static final int HEADER_H = 26;

	private Ui() {
	}

	public static Theme theme() {
		return theme;
	}

	public static void setTheme(Theme newTheme) {
		theme = newTheme;
	}

	public static Font font() {
		return Minecraft.getInstance().font;
	}

	// ---- surfaces -------------------------------------------------------------------------------

	/**
	 * Every top-level surface - menus, the accessory overlay, HUD boxes - uses this same rounded
	 * panel, so SCD reads as one consistent UI instead of three different styles.
	 */
	public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		hudPanel(g, x, y, w, h, theme.hudTint());
	}

	/**
	 * Standard panel header: title on the left, optional muted subtitle on the right, and an
	 * accent-led divider underneath. Returns the y where content should start.
	 */
	public static int header(GuiGraphicsExtractor g, int x, int y, int w, String title, String subtitle) {
		Theme t = theme;
		text(g, title, x, y, t.textPrimary());
		if (subtitle != null && !subtitle.isEmpty()) {
			int room = w - width(title) - 12;
			if (room > 20) rightAligned(g, ellipsize(subtitle, room), x + w, y, t.textMuted());
		}
		int ly = y + lineHeight() + 4;
		g.fill(x, ly, x + w, ly + 1, t.border());
		g.fill(x, ly, x + Math.min(w, 28), ly + 1, t.accent());
		return ly + 6;
	}

	/** Raised card with an accent bar on the left - navigation rows and section headers. */
	public static void card(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean hovered) {
		Theme t = theme;
		g.fillGradient(x, y, x + w, y + h, hovered ? t.cardHoverTop() : t.cardTop(), hovered ? t.cardHoverBottom() : t.cardBottom());
		g.outline(x, y, w, h, hovered ? t.accent() : t.border());
		g.fill(x, y, x + 3, y + h, t.accent());
	}

	/** Rounded nine-slice HUD background with a concentric shadow halo; {@code tint} multiplies the sprite. */
	public static void hudPanel(GuiGraphicsExtractor g, int x, int y, int w, int h, int tint) {
		int s = HUD_SHADOW_SPREAD;
		g.blitSprite(RenderPipelines.GUI_TEXTURED, HUD_PANEL, x - s, y - s, w + s * 2, h + s * 2, SHADOW);
		g.blitSprite(RenderPipelines.GUI_TEXTURED, HUD_PANEL, x, y, w, h, tint);
	}

	public static void divider(GuiGraphicsExtractor g, int x, int y, int width) {
		g.fill(x, y, x + width, y + 1, theme.border());
	}

	public static void bar(GuiGraphicsExtractor g, int x, int y, int w, int h, float frac, int color) {
		g.fill(x, y, x + w, y + h, 0x60000000);
		int filled = Math.round(w * Math.max(0, Math.min(1, frac)));
		if (filled > 0) g.fill(x, y, x + filled, y + h, color);
	}

	// ---- text -----------------------------------------------------------------------------------

	public static void text(GuiGraphicsExtractor g, String text, int x, int y, int color) {
		g.text(font(), text, x, y, color, true);
	}

	public static void text(GuiGraphicsExtractor g, Component text, int x, int y, int color) {
		g.text(font(), text, x, y, color, true);
	}

	public static void centered(GuiGraphicsExtractor g, String text, int centerX, int y, int color) {
		g.text(font(), text, centerX - font().width(text) / 2, y, color, true);
	}

	public static void rightAligned(GuiGraphicsExtractor g, String text, int rightX, int y, int color) {
		g.text(font(), text, rightX - font().width(text), y, color, true);
	}

	/** Small-caps style section label in the muted color. */
	public static void section(GuiGraphicsExtractor g, String text, int x, int y) {
		text(g, text.toUpperCase(Locale.ROOT), x, y, theme.textMuted());
	}

	/** Text at an integer multiple of native size - fractional scales resample the bitmap font unevenly. */
	public static void scaled(GuiGraphicsExtractor g, String text, int x, int y, int color, int scale) {
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(scale, scale);
		g.text(font(), text, 0, 0, color, true);
		pose.popMatrix();
	}

	/** Shortens with an ellipsis so the text fits {@code maxWidth}. */
	public static String ellipsize(String text, int maxWidth) {
		Font f = font();
		if (f.width(text) <= maxWidth) return text;
		String ell = "…";
		int budget = maxWidth - f.width(ell);
		return f.plainSubstrByWidth(text, Math.max(0, budget)) + ell;
	}

	public static int width(String text) {
		return font().width(text);
	}

	public static int lineHeight() {
		return font().lineHeight;
	}

	// ---- rarity ---------------------------------------------------------------------------------

	/** Hypixel's own tooltip color for a rarity; secondary text color for unknown. */
	public static int rarityColor(String rarity) {
		int rank = rarityRank(rarity);
		return rank >= 0 ? RARITY_COLORS[rank] : theme.textSecondary();
	}

	/** 0 = COMMON .. higher is rarer; -1 for unknown. */
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
