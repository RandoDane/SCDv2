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
 * Text uses the bundled Inter TTF (smooth, anti-aliased) without Minecraft's hard black drop
 * shadow - the pixel font plus shadow is what made the old UI feel "sharp". Rounded rectangles are
 * rasterized at the monitor's real pixel resolution (not GUI pixels) with anti-aliased corners, so
 * they stay soft at every GUI scale. No drop shadows anywhere: depth comes from surface lightness
 * and hairline borders.
 */
public final class Ui {
	/** Semantic colors follow the theme (colour-vision-safe themes swap red/green for orange/blue). */
	public static volatile int DANGER = 0xFFFF6B6B;
	public static volatile int WARNING = 0xFFFFA45B;
	public static volatile int SUCCESS = 0xFF4ADE80;
	public static volatile int SELL = 0xFF4ADE80;
	public static volatile int BUY = 0xFFFFA45B;
	public static final int GOLD = 0xFFFFC857;

	/** Shared spacing so every surface lines up. */
	public static final int PAD = 12;
	public static final int GAP = 5;
	public static final int CONTROL_H = 18;
	public static final int RADIUS = 5;
	public static final int RADIUS_SMALL = 4;

	/**
	 * One font definition per GUI scale (1-6), rasterized at exactly that scale so every glyph pixel
	 * lands on one screen pixel - a single oversampled font shrunk with nearest-neighbour sampling
	 * looked thin and jagged at scales 2-3. Body text is Inter Medium, headings SemiBold.
	 */
	private static final int[] TEXT_SIZES = {70, 80, 90, 100, 110};
	private static final FontDescription[][] REGULAR_BY_SCALE = faces("ui_");
	private static final FontDescription[][] BOLD_BY_SCALE = faces("ui_bold_");
	private static final FontDescription[][] TITLE_BY_SCALE = faces("ui_title_");
	private static final int FULL = 3; // index of 100% in TEXT_SIZES

	/** [size index][gui scale] -> font "scd:<prefix><percent>_<scale>". */
	private static FontDescription[][] faces(String prefix) {
		FontDescription[][] out = new FontDescription[TEXT_SIZES.length][7];
		for (int s = 0; s < TEXT_SIZES.length; s++) {
			for (int i = 1; i <= 6; i++) {
				out[s][i] = new FontDescription.Resource(Identifier.fromNamespaceAndPath("scd", prefix + TEXT_SIZES[s] + "_" + i));
			}
			out[s][0] = out[s][1];
		}
		return out;
	}

	/** Allowed menu text sizes in percent (the font files exist for exactly these). */
	public static int[] textSizes() {
		return TEXT_SIZES.clone();
	}

	/** Menu text size in percent; snapped to the nearest available size. */
	public static void setMenuTextSize(int percent) {
		int best = 0;
		for (int i = 0; i < TEXT_SIZES.length; i++) if (Math.abs(TEXT_SIZES[i] - percent) < Math.abs(TEXT_SIZES[best] - percent)) best = i;
		menuSize = best;
	}

	private static volatile int menuSize = 1;

	/** The menu text size applies while an SCD menu is open; HUDs and everything else stay at 100%. */
	private static int sizeIndex() {
		return Minecraft.getInstance().gui.screen() instanceof ScdMenu ? menuSize : FULL;
	}

	/**
	 * HUD faces rasterized at quarter-step pixel densities (0.5-6 px per GUI unit). A HUD drawn at a
	 * custom size uses the one matching its real on-screen density, so text isn't resampled.
	 */
	private static final FontDescription[] HUD_REGULAR = hudFaces("hud_q");
	private static final FontDescription[] HUD_BOLD = hudFaces("hud_bold_q");
	private static final FontDescription[] HUD_TITLE = hudFaces("hud_title_q");
	/** Screen pixels per GUI unit of the HUD being drawn; 0 when not drawing a HUD. */
	private static float hudDensity;

	private static FontDescription[] hudFaces(String prefix) {
		FontDescription[] out = new FontDescription[25];
		for (int q = 2; q <= 24; q++) out[q] = new FontDescription.Resource(Identifier.fromNamespaceAndPath("scd", prefix + q));
		return out;
	}

	/** Set while a HUD (or the click GUI) is measured/drawn: screen pixels per text unit. 0 resets. */
	public static void setHudDensity(float density) {
		hudDensity = density;
	}

	private static int hudIndex() {
		return Math.max(2, Math.min(24, Math.round(hudDensity * 4)));
	}

	private static volatile int scaleOverride;

	/** Font rasterization scale for a menu drawn at its own pixel density (0 = the GUI scale). */
	public static void setScaleOverride(int scale) {
		scaleOverride = scale;
	}

	private static int scaleIndex() {
		int s = scaleOverride > 0 ? scaleOverride : Minecraft.getInstance().getWindow().getGuiScale();
		return Math.max(1, Math.min(6, s));
	}

	public static FontDescription regular() {
		if (hudDensity > 0) return HUD_REGULAR[hudIndex()];
		return REGULAR_BY_SCALE[sizeIndex()][scaleIndex()];
	}

	public static FontDescription bold() {
		if (hudDensity > 0) return HUD_BOLD[hudIndex()];
		return BOLD_BY_SCALE[sizeIndex()][scaleIndex()];
	}

	/** For text drawn with an extra pose scale (vanilla titles are 4x, subtitles 2x). */
	public static FontDescription boldAt(int extraScale) {
		return BOLD_BY_SCALE[FULL][Math.max(1, Math.min(6, scaleIndex() * extraScale))];
	}

	public static FontDescription regularAt(int extraScale) {
		return REGULAR_BY_SCALE[FULL][Math.max(1, Math.min(6, scaleIndex() * extraScale))];
	}

	public static FontDescription titleFace() {
		if (hudDensity > 0) return HUD_TITLE[hudIndex()];
		return TITLE_BY_SCALE[sizeIndex()][scaleIndex()];
	}

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
		DANGER = newTheme.negative();
		WARNING = newTheme.warning();
		SUCCESS = newTheme.positive();
		SELL = newTheme.positive();
		BUY = newTheme.warning();
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

	/**
	 * Extra scale for text only (1 = none). The click GUI sets it together with a font rasterized
	 * for exactly the resulting pixel density, so text size changes without resampling glyphs.
	 */
	private static float textScale = 1f;

	public static void setTextScale(float scale) {
		textScale = scale;
	}

	private static void draw(GuiGraphicsExtractor g, Component c, int x, int y, int color) {
		if (!smoothFont) {
			drawScaled(g, c, x, y, color);
			return;
		}
		// Minecraft puts a TTF glyph's top at 7 - bearing text units below the line. The bearing is a
		// whole number of pixels with our density-matched fonts, but 7 units often isn't (7 * 0.75 * 2
		// = 10.5px), which shifts every glyph half a pixel and drops its top row (T without a bar).
		// Nudge the line so that glyph grid lands exactly on screen pixels.
		var m = g.pose();
		float gui = Minecraft.getInstance().getWindow().getGuiScale();
		float dy = textScale == 1f ? 0 : (1f - textScale) * font().lineHeight / 2f;
		float gridY = y + dy + 7 * textScale;
		float px = (m.m00() * x + m.m10() * gridY + m.m20()) * gui;
		float py = (m.m01() * x + m.m11() * gridY + m.m21()) * gui;
		float sx = m.m00() * gui, sy = m.m11() * gui;
		float fixX = sx > 0 ? (Math.round(px) - px) / sx : 0;
		float fixY = sy > 0 ? (Math.round(py) - py) / sy : 0;
		m.pushMatrix();
		m.translate(x + fixX, y + dy + fixY);
		if (textScale != 1f) m.scale(textScale, textScale);
		g.text(font(), c, 0, 0, color, false);
		m.popMatrix();
	}

	private static void drawScaled(GuiGraphicsExtractor g, Component c, int x, int y, int color) {
		if (textScale == 1f) {
			g.text(font(), c, x, y, color, false);
			return;
		}
		var pose = g.pose();
		pose.pushMatrix();
		// Keep the text vertically centred in the line it would have occupied.
		pose.translate(x, y + (1f - textScale) * font().lineHeight / 2f);
		pose.scale(textScale, textScale);
		g.text(font(), c, 0, 0, color, false);
		pose.popMatrix();
	}

	public static void text(GuiGraphicsExtractor g, String text, int x, int y, int color) {
		draw(g, styled(text, regular()), x, y, color);
	}

	public static void text(GuiGraphicsExtractor g, Component text, int x, int y, int color) {
		draw(g, text, x, y, color);
	}

	public static void bold(GuiGraphicsExtractor g, String text, int x, int y, int color) {
		draw(g, styled(text, bold()), x, y, color);
	}

	/** Large heading (about 1.5x body size). */
	public static void title(GuiGraphicsExtractor g, String text, int x, int y, int color) {
		draw(g, styled(text, titleFace()), x, y, color);
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
		return Math.round(font().width(styled(text, regular())) * textScale);
	}

	public static int widthBold(String text) {
		return Math.round(font().width(styled(text, bold())) * textScale);
	}

	public static int widthTitle(String text) {
		return Math.round(font().width(styled(text, titleFace())) * textScale);
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
		Corners.fill(g, X, Y, W, H, radius, color);
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
