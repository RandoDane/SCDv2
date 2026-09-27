package com.scd.client.ui.clickgui;

import com.scd.client.ScdMod;
import com.scd.client.ui.ScdMenu;
import com.scd.client.ui.Theme;
import com.scd.client.ui.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SCD's main menu as a click GUI: one draggable column per category, each row a feature. Left-click
 * a row to turn it on/off, right-click to show its options underneath. A panel on the right (a fifth
 * of the screen) holds general and carry settings and links to the detailed pages.
 */
public final class ClickGuiScreen extends Screen implements ScdMenu {
	/** Layout in menu units at 100% Menu size; the setting scales these, never the pixel grid. */
	private int colW = 124, headH = 16, rowH = 14, optH = 13, gap = 6;
	private static final Identifier LOGO = Identifier.fromNamespaceAndPath("scd", "logo");
	/** Expanded rows, remembered for the session. */
	private static final Set<String> EXPANDED = new HashSet<>();
	private static final Map<String, Double> SCROLL = new HashMap<>();

	private record Hit(int x, int y, int w, int h, Runnable left, Runnable right, Opt.Slider slider, String hover) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private final ScdMod mod;
	private final List<Category> categories;
	private final List<PanelSection> panel;
	private final List<Hit> hits = new ArrayList<>();
	private final Map<String, int[]> headerRects = new HashMap<>();
	private String dragging;
	private double dragDx, dragDy;
	private Hit activeSlider;
	private double panelScroll;
	private PanelPage shownPage;
	/** Menu units -> GUI units, and the menu's virtual size (the menu ignores the game's GUI scale). */
	private float scale = 1;
	private int vw, vh;

	/** Side panel block; options are re-read every frame (carry lists, stats change live). */
	public record PanelSection(String title, java.util.function.Supplier<List<Opt>> options) {
	}

	/** A page that replaces the side panel's content (with a Go back button), e.g. Carries. */
	public record PanelPage(String title, List<PanelSection> sections) {
	}

	/** The open side panel page; null = the main panel. Remembered for the session. */
	private static PanelPage page;

	public static void openPage(PanelPage p) {
		page = p;
	}

	public static void openCarries(com.scd.client.ScdMod mod) {
		openPage(ClickGuiContent.carryPage(mod));
	}

	public static void closePage() {
		page = null;
	}

	/** The text option being edited, and its unsaved value. */
	private static Opt.Text editing;
	private static String editBuffer = "";
	/** Tab completion: what was typed before the first Tab, and which match is shown. */
	private static String tabPrefix;
	private static int tabIndex;

	/** What's being typed into the field with this label right now, or null when it isn't being edited. */
	public static String liveText(String label) {
		return editing != null && editing.label().equals(label) ? editBuffer : null;
	}

	/** Palette for colour options (null first = theme colour). */
	private static final Integer[] PALETTE = {null, 0xFFFFFFFF, 0xFFAAAAAA, 0xFF555555, 0xFFFF5555, 0xFFFFAA00, 0xFFFFFF55, 0xFF55FF55,
			0xFF55FFFF, 0xFF5599FF, 0xFFAA55FF, 0xFFFF55FF};

	private Set<String> collapsed() {
		return new HashSet<>(mod.config().general.clickGuiCollapsed);
	}

	public ClickGuiScreen(ScdMod mod) {
		super(Component.literal("SCD"));
		this.mod = mod;
		this.categories = ClickGuiContent.categories(mod);
		this.panel = ClickGuiContent.panel(mod);
	}

	/** Opens a row's options (e.g. "Dungeons/Score HUD"). */
	public static void expand(String key) {
		EXPANDED.add(key);
	}

	/**
	 * The menu keeps one on-screen size whatever the GUI scale: a whole number of screen pixels per
	 * menu unit (2 at 1080p, proportional to the window height), so every 1-unit line is equally thick
	 * on all sides. "Menu size" resizes the layout itself instead of stretching the grid.
	 */
	private void updateScale() {
		var window = net.minecraft.client.Minecraft.getInstance().getWindow();
		int density = Math.max(1, Math.round(window.getHeight() / 540f));
		scale = density / (float) window.getGuiScale();
		vw = (int) (width / scale);
		vh = (int) (height / scale);
		float size = mod.config().general.menuScale / 100f;
		colW = Math.round(124 * size);
		headH = Math.round(16 * size);
		rowH = Math.round(14 * size);
		optH = Math.round(13 * size);
		gap = Math.max(2, Math.round(6 * size));
		// Text gets a font made for exactly its pixel density, so glyphs map 1:1 to pixels.
		pixelDensity = density;
		textDensity = quarter(density * size * mod.config().general.menuTextSize / 100f);
	}

	private int pixelDensity = 2;

	/** Top of text vertically centred in a line of height {@code h}. */
	private int textY(int top, int h) {
		int textH = Math.round(7 * textDensity / pixelDensity);
		return top + Math.max(0, (h - textH) / 2);
	}

	private float textDensity = 2;

	private static float quarter(float v) {
		return Math.max(0.5f, Math.min(6f, Math.round(v * 4) / 4f));
	}

	/** Font + text scale for drawing (and measuring) menu text; always paired with {@link #endText()}. */
	private void beginText() {
		Ui.setHudDensity(textDensity);
		Ui.setTextScale(textDensity / pixelDensity);
	}

	/** Category titles: bold and a step larger than row text, with a font made for that density. */
	private void headerText(GuiGraphicsExtractor g, String text, int centerX, int top) {
		headerText(g, text, centerX, top, headH);
	}

	private void headerText(GuiGraphicsExtractor g, String text, int centerX, int top, int boxH) {
		float density = quarter(textDensity * 1.3f);
		Ui.setHudDensity(density);
		Ui.setTextScale(density / pixelDensity);
		int textH = Math.round(7 * density / pixelDensity);
		Ui.bold(g, text, centerX - Ui.widthBold(text) / 2, top + Math.max(0, (boxH - textH) / 2), 0xFFFFFFFF);
		beginText();
	}

	private static void endText() {
		Ui.setHudDensity(0);
		Ui.setTextScale(1f);
	}

	private int panelW() {
		return Math.max(120, vw / 5);
	}

	private Map<String, int[]> positions() {
		return mod.config().general.clickGuiPositions;
	}

	/** Default placement: left to right, wrapping into a second band. */
	private int[] position(Category c, int index) {
		int[] saved = positions().get(c.name());
		if (saved != null) return saved;
		int perRow = Math.max(1, (vw - panelW() - gap) / (colW + gap));
		int rows = (categories.size() + perRow - 1) / perRow;
		int x = gap + (index % perRow) * (colW + gap);
		int y = gap + (index / perRow) * (vh / rows);
		return new int[]{x, y};
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		updateScale();
		var pose = g.pose();
		pose.pushMatrix();
		pose.scale(scale, scale);
		beginText();
		try {
			drawMenu(g, Math.round(mouseX / scale), Math.round(mouseY / scale));
		} finally {
			endText();
			pose.popMatrix();
		}
	}

	private void drawMenu(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		hits.clear();
		headerRects.clear();
		String hover = null;
		for (int i = 0; i < categories.size(); i++) {
			int[] pos = position(categories.get(i), i);
			String h = drawCategory(g, categories.get(i), pos, bottomLimit(i, pos), mouseX, mouseY);
			if (h != null) hover = h;
		}
		drawPanel(g, mouseX, mouseY);
		if (hover != null && !hover.isEmpty()) {
			Theme t = Ui.theme();
			int w = Math.min(vw - panelW() - 16, Ui.width(hover) + 12);
			Ui.rect(g, gap, vh - 20, w, 16, 4, t.window(), t.border());
			Ui.text(g, Ui.ellipsize(hover, w - 12), gap + 6, vh - 16, t.textSecondary());
		}
	}

	/** A column stops above any column placed below it (and at the screen bottom), scrolling inside. */
	private int bottomLimit(int index, int[] pos) {
		int limit = vh - gap;
		for (int j = 0; j < categories.size(); j++) {
			if (j == index) continue;
			int[] o = position(categories.get(j), j);
			boolean overlapX = o[0] < pos[0] + colW && o[0] + colW > pos[0];
			if (o[1] <= pos[1]) continue;
			if (overlapX && o[1] > pos[1]) limit = Math.min(limit, o[1] - gap);
		}
		return limit;
	}

	private String drawCategory(GuiGraphicsExtractor g, Category c, int[] pos, int maxBottom, int mx, int my) {
		Theme t = Ui.theme();
		int x = pos[0], y = pos[1];
		// Header: the drag handle.
		g.fill(x, y, x + colW, y + headH, t.accent());
		headerText(g, c.name().toUpperCase(java.util.Locale.ROOT), x + colW / 2, y);
		headerRects.put(c.name(), new int[]{x, y, colW, headH});
		// Right-click on the header folds the category away (left-drag moves it).
		if (mod.config().general.clickGuiCollapsed.contains(c.name())) return null;
		int top = y + headH;
		double scroll = SCROLL.getOrDefault(c.name(), 0.0);
		// +2: a background-coloured line under the last row, then the accent outline.
		int contentH = contentHeight(c) + 2;
		int visibleH = Math.max(0, Math.min(contentH, maxBottom - top));
		scroll = Math.max(0, Math.min(scroll, contentH - visibleH));
		SCROLL.put(c.name(), scroll);
		g.fill(x, top, x + colW, top + visibleH, t.window());
		// Rows sit inside a 1-unit background-coloured inner border (sides and bottom) within the outline.
		g.enableScissor(x + 2, top, x + colW - 2, top + visibleH - 2);
		String hover = null;
		int ry = top - (int) scroll;
		for (Module m : c.modules()) {
			String key = c.name() + "/" + m.name;
			boolean on = m.on != null && m.on.getAsBoolean();
			boolean hot = mx >= x && mx < x + colW && my >= Math.max(ry, top) && my < Math.min(ry + rowH, top + visibleH);
			int bg = on ? (t.accent() & 0x00FFFFFF) | 0x70000000 : hot ? t.cardHover() : t.window();
			g.fill(x + 2, ry, x + colW - 2, ry + rowH, bg);
			Ui.text(g, Ui.ellipsize(m.name, colW - 14), x + 6, textY(ry, rowH), on ? 0xFFFFFFFF : t.textPrimary());
			if (hot) hover = m.description;
			if (visible(ry, rowH, top, visibleH)) {
				// Rows without options can't be expanded.
				Runnable expand = m.options.isEmpty() ? null : () -> toggleExpanded(key);
				hits.add(new Hit(x, Math.max(ry, top), colW, rowH, m.on != null ? () -> m.set.accept(!m.on.getAsBoolean()) : expand,
						expand, null, m.description));
			}
			ry += rowH;
			if (EXPANDED.contains(key) && !m.options.isEmpty()) {
				for (Opt o : m.options) {
					drawOpt(g, o, x + 2, ry, colW - 4, 6, top, visibleH, mx, my);
					ry += optH;
				}
				g.fill(x + 2, ry, x + colW - 2, ry + 1, t.border());
				ry += 1;
			}
		}
		g.disableScissor();
		// Outline last so nothing drawn for the rows can cover it.
		g.outline(x, y, colW, headH + visibleH, t.accent());
		return hover;
	}

	private static boolean visible(int y, int h, int top, int visibleH) {
		return y + h > top && y < top + visibleH;
	}

	private int contentHeight(Category c) {
		int h = 0;
		for (Module m : c.modules()) {
			h += rowH;
			if (!m.options.isEmpty() && EXPANDED.contains(c.name() + "/" + m.name)) h += m.options.size() * optH + 1;
		}
		return h;
	}

	private void toggleExpanded(String key) {
		if (!EXPANDED.remove(key)) EXPANDED.add(key);
	}

	/** One option line; registers its click target. */
	private void drawOpt(GuiGraphicsExtractor g, Opt o, int x, int y, int w, int indent, int clipTop, int clipH, int mx, int my) {
		Theme t = Ui.theme();
		g.fill(x, y, x + w, y + optH, t.sidebar());
		int tx = x + indent;
		boolean vis = visible(y, optH, clipTop, clipH);
		int hy = Math.max(y, clipTop);
		switch (o) {
			case Opt.Toggle tg -> {
				boolean on = tg.get().getAsBoolean();
				Ui.text(g, Ui.ellipsize(tg.label(), w - indent - 16), tx, textY(y, optH), t.textSecondary());
				int bx = x + w - 11;
				Ui.rect(g, bx, y + (optH - 7) / 2, 7, 7, 2, on ? t.accent() : t.trackOff());
				if (vis) hits.add(new Hit(x, hy, w, optH, () -> tg.set().accept(!tg.get().getAsBoolean()), null, null, null));
			}
			case Opt.Slider sl -> {
				double v = sl.get().getAsDouble();
				String val = sl.fmt().apply(v);
				// Inline: label | track | value, so the bar never reads as an underline.
				int valW = Math.max(Ui.width(val), Ui.width("100%")) + 4;
				Ui.text(g, Ui.ellipsize(sl.label(), w * 2 / 5 - indent), tx, textY(y, optH), t.textSecondary());
				Ui.rightAligned(g, val, x + w - 4, textY(y, optH), t.textPrimary());
				int trackX = x + w * 2 / 5 + 2, trackW = Math.max(10, x + w - 4 - valW - trackX);
				float frac = (float) ((v - sl.min()) / (sl.max() - sl.min()));
				int ty = y + optH / 2 - 1, fill = Math.round(trackW * frac);
				g.fill(trackX, ty, trackX + trackW, ty + 2, t.trackOff());
				g.fill(trackX, ty, trackX + fill, ty + 2, t.accent());
				int knob = Math.max(trackX, Math.min(trackX + trackW - 2, trackX + fill - 1));
				g.fill(knob, ty - 2, knob + 2, ty + 4, 0xFFFFFFFF);
				if (vis) hits.add(new Hit(trackX, hy, trackW, optH, null, null, sl, null));
			}
			case Opt.Cycle cy -> {
				Ui.text(g, Ui.ellipsize(cy.label(), w / 2), tx, textY(y, optH), t.textSecondary());
				Ui.rightAligned(g, cy.fmt().apply(cy.get().get()), x + w - 4, textY(y, optH), t.accent());
				if (vis) hits.add(new Hit(x, hy, w, optH, () -> step(cy, 1), () -> step(cy, -1), null, null));
			}
			case Opt.Action a -> {
				boolean hot = mx >= x && mx < x + w && my >= y && my < y + optH;
				Ui.text(g, Ui.ellipsize(a.label(), w - indent - 4), tx, textY(y, optH), hot ? t.accent() : t.textPrimary());
				if (vis) hits.add(new Hit(x, hy, w, optH, a.run(), null, null, null));
			}
			case Opt.Text tx2 -> {
				// Panel options are rebuilt every frame, so match the field by label, not identity.
				boolean editingThis = editing != null && editing.label().equals(tx2.label());
				if (editingThis) editing = tx2;
				Ui.text(g, Ui.ellipsize(tx2.label(), w / 3), tx, textY(y, optH), t.textSecondary());
				String v = editingThis ? editBuffer + ((System.currentTimeMillis() / 500) % 2 == 0 ? "_" : " ") : tx2.get().get();
				int vx = x + w / 3 + 4, vw = w - w / 3 - 8;
				g.fill(vx - 2, y + 1, x + w - 2, y + optH - 1, editingThis ? t.field() : t.window());
				if (editingThis) g.outline(vx - 2, y + 1, x + w - vx, optH - 2, t.accent());
				// Show the end of what's being typed.
				String shown = v;
				while (Ui.width(shown) > vw && shown.length() > 1) shown = shown.substring(1);
				if (!editingThis) shown = Ui.ellipsize(v, vw);
				boolean empty = !editingThis && (v == null || v.isEmpty());
				if (empty) shown = "click to type";
				Ui.text(g, shown, vx, textY(y, optH), editingThis ? t.textPrimary() : empty ? t.textMuted() : t.textSecondary());
				if (vis) hits.add(new Hit(x, hy, w, optH, () -> startEdit(tx2), null, null, null));
			}
			case Opt.Color col -> {
				Integer v = col.get().get();
				Ui.text(g, Ui.ellipsize(col.label(), w - 40), tx, textY(y, optH), t.textSecondary());
				int sx = x + w - 12;
				if (v == null) Ui.rightAligned(g, "theme", sx - 4, textY(y, optH), t.textMuted());
				g.fill(sx, y + (optH - 7) / 2, sx + 7, y + (optH - 7) / 2 + 7, v == null ? t.accent() : v);
				g.outline(sx, y + (optH - 7) / 2, 7, 7, t.border());
				if (vis) hits.add(new Hit(x, hy, w, optH, () -> stepColor(col, 1), () -> stepColor(col, -1), null, null));
			}
			case Opt.Buttons bs -> {
				int n = bs.names().size();
				int capW = bs.label().isEmpty() ? 0 : Math.min(w / 2, Ui.width(bs.label()) + 8);
				if (capW > 0) Ui.text(g, Ui.ellipsize(bs.label(), capW - 4), tx, textY(y, optH), t.textSecondary());
				int bx = x + capW + (capW > 0 ? 0 : 2), bw = (w - capW - 2) / Math.max(1, n);
				for (int i = 0; i < n; i++) {
					int zx = bx + i * bw;
					boolean hot = mx >= zx && mx < zx + bw && my >= y && my < y + optH;
					g.fill(zx + 1, y + 1, zx + bw - 1, y + optH - 1, hot ? t.cardHover() : t.card());
					Ui.centered(g, bs.names().get(i), zx + bw / 2, textY(y, optH), t.textPrimary());
					if (vis) hits.add(new Hit(zx, hy, bw, optH, bs.actions().get(i), null, null, null));
				}
			}
			case Opt.Chips ch -> {
				int n = ch.names().size();
				int capW = ch.label().isEmpty() ? 0 : Math.min(w / 3, Ui.width(ch.label()) + 8);
				if (capW > 0) Ui.text(g, Ui.ellipsize(ch.label(), capW - 4), tx, textY(y, optH), t.textSecondary());
				int bx = x + capW + (capW > 0 ? 0 : 2), bw = (w - capW - 2) / Math.max(1, n);
				String sel = ch.selected().get();
				for (int i = 0; i < n; i++) {
					int zx = bx + i * bw;
					String name = ch.names().get(i);
					boolean on = name.equals(sel), hot = mx >= zx && mx < zx + bw && my >= y && my < y + optH;
					g.fill(zx + 1, y + 1, zx + bw - 1, y + optH - 1, on ? t.accent() : hot ? t.cardHover() : t.card());
					Ui.centered(g, Ui.ellipsize(name, bw - 4), zx + bw / 2, textY(y, optH), on ? 0xFFFFFFFF : t.textPrimary());
					if (vis) hits.add(new Hit(zx, hy, bw, optH, () -> ch.pick().accept(name), null, null, null));
				}
			}
			case Opt.Progress pr -> {
				String val = pr.value().get();
				int valW = Ui.width(val) + 8;
				Ui.text(g, Ui.ellipsize(pr.label(), w * 2 / 5 - indent), tx, textY(y, optH), t.textSecondary());
				Ui.rightAligned(g, val, x + w - 4, textY(y, optH), t.textPrimary());
				int bx = x + w * 2 / 5 + 2, bw = Math.max(10, x + w - 4 - valW - bx), by = y + optH / 2 - 2;
				float frac = (float) Math.max(0, Math.min(1, pr.fraction().getAsDouble()));
				g.fill(bx, by, bx + bw, by + 4, t.trackOff());
				g.fill(bx, by, bx + Math.round(bw * frac), by + 4, frac >= 1 ? 0xFF4ADE80 : t.accent());
			}
			case Opt.Note nt -> {
				String text = nt.text().get();
				if (text != null && !text.isEmpty()) Ui.text(g, Ui.ellipsize(text, w - indent - 4), tx, textY(y, optH), nt.color());
			}
			case Opt.Info in -> {
				Ui.text(g, Ui.ellipsize(in.label(), w / 2), tx, textY(y, optH), t.textMuted());
				Ui.rightAligned(g, Ui.ellipsize(in.value().get(), w / 2 - 4), x + w - 4, textY(y, optH), t.textSecondary());
			}
		}
	}

	private void startEdit(Opt.Text t) {
		editing = t;
		tabPrefix = null;
		editBuffer = t.get().get() == null ? "" : t.get().get();
	}

	private void commitEdit() {
		if (editing != null) editing.set().accept(editBuffer);
		editing = null;
	}

	private static void stepColor(Opt.Color col, int dir) {
		int i = java.util.Arrays.asList(PALETTE).indexOf(col.get().get());
		int n = PALETTE.length;
		col.set().accept(PALETTE[((i < 0 ? 0 : i) + dir + n) % n]);
	}

	@Override
	public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
		if (editing == null) return super.charTyped(event);
		String c = event.codepointAsString();
		if (editBuffer.length() < 200 && !c.isEmpty() && c.charAt(0) >= ' ') editBuffer += c;
		tabPrefix = null;
		return true;
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
		if (editing == null && page != null && event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
			closePage();
			return true;
		}
		if (editing == null) return super.keyPressed(event);
		switch (event.key()) {
			case org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE -> {
				if (!editBuffer.isEmpty()) editBuffer = editBuffer.substring(0, editBuffer.length() - 1);
				tabPrefix = null;
			}
			case org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER -> commitEdit();
			case org.lwjgl.glfw.GLFW.GLFW_KEY_TAB -> {
				if (editing.complete() == null) return true;
				// Repeated Tab cycles through the matches for what was typed before the first one.
				if (tabPrefix == null) {
					tabPrefix = editBuffer;
					tabIndex = 0;
				} else tabIndex++;
				List<String> matches = editing.complete().apply(tabPrefix);
				if (!matches.isEmpty()) editBuffer = matches.get(tabIndex % matches.size());
				return true;
			}
			case org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE -> editing = null;
			default -> {
				if (event.isPaste()) editBuffer += net.minecraft.client.Minecraft.getInstance().keyboardHandler.getClipboard();
			}
		}
		return true;
	}

	private static void step(Opt.Cycle cy, int dir) {
		int i = cy.values().indexOf(cy.get().get());
		int n = cy.values().size();
		cy.set().accept(cy.values().get(((i < 0 ? 0 : i) + dir + n) % n));
	}

	private void drawPanel(GuiGraphicsExtractor g, int mx, int my) {
		Theme t = Ui.theme();
		int pw = panelW(), px = vw - pw;
		g.fill(px, 0, vw, vh, t.window());
		g.fill(px, 0, px + 1, vh, t.accent());
		g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, LOGO, px + gap, 6, 16, 16);
		int top = 28;
		int bottom;
		List<PanelSection> sections;
		if (page == null) {
			// "Move HUDs" sits at the bottom as a big button; the sections scroll above it.
			int btnH = headH + 4, btnY = vh - gap - btnH;
			bottom = btnY - gap;
			bigButton(g, px + gap, btnY, pw - 2 * gap, btnH, "MOVE HUDS", mx, my, () -> net.minecraft.client.Minecraft.getInstance().gui.setScreen(
					new com.scd.client.hud.HudEditorScreen(this, mod.huds, mod.configManager)), "Drag, resize and anchor every HUD");
			sections = panel;
		} else {
			// Pages: Go back next to the logo (Esc does the same).
			int bx = px + gap + 16 + 4;
			bigButton(g, bx, 6, vw - gap - bx, 16, "GO BACK", mx, my, ClickGuiScreen::closePage, "Back to the main panel (Esc)");
			bottom = vh - gap;
			sections = page.sections();
		}
		if (page != shownPage) panelScroll = 0;
		shownPage = page;
		int y = top - (int) panelScroll;
		g.enableScissor(px, top, vw, bottom);
		// Each section is a card styled like a category column: accent header, outline, and the
		// options inside a 1-unit background-coloured inner border.
		int cx = px + gap, cw = pw - 2 * gap;
		for (PanelSection s : sections) {
			List<Opt> opts = s.options().get();
			if (opts.isEmpty()) continue;
			// +2: a background-coloured line under the last row, then the outline.
			int bodyH = opts.size() * optH + 2;
			g.fill(cx, y, cx + cw, y + headH, t.accent());
			headerText(g, s.title().toUpperCase(java.util.Locale.ROOT), cx + cw / 2, y);
			g.fill(cx, y + headH, cx + cw, y + headH + bodyH, t.window());
			int ry = y + headH;
			for (Opt o : opts) {
				drawOpt(g, o, cx + 2, ry, cw - 4, 6, top, bottom - top, mx, my);
				ry += optH;
			}
			// Outline last so nothing drawn for the rows can cover it.
			g.outline(cx, y, cw, headH + bodyH, t.accent());
			y += headH + bodyH + gap;
		}
		int contentH = y + (int) panelScroll - top;
		panelScroll = Math.max(0, Math.min(panelScroll, Math.max(0, contentH - (bottom - top))));
		g.disableScissor();
	}

	/** A large accent button with bold centred text (Move HUDs, Go back). */
	private void bigButton(GuiGraphicsExtractor g, int x, int y, int w, int h, String text, int mx, int my, Runnable action, String hover) {
		Theme t = Ui.theme();
		boolean hot = mx >= x && mx < x + w && my >= y && my < y + h;
		g.fill(x, y, x + w, y + h, (t.accent() & 0x00FFFFFF) | (hot ? 0xC0000000 : 0x70000000));
		g.outline(x, y, w, h, t.accent());
		headerText(g, text, x + w / 2, y, h);
		hits.add(new Hit(x, y, w, h, action, null, null, hover));
	}

	// ---- input ----------------------------------------------------------------------------

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		updateScale();
		double mx = event.x() / scale, my = event.y() / scale;
		if (editing != null) commitEdit();
		for (var e : headerRects.entrySet()) {
			int[] r = e.getValue();
			boolean onHeader = mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
			if (event.button() == 1 && onHeader) {
				List<String> list = mod.config().general.clickGuiCollapsed;
				if (!list.remove(e.getKey())) list.add(e.getKey());
				return true;
			}
			if (event.button() == 0 && onHeader) {
				dragging = e.getKey();
				dragDx = mx - r[0];
				dragDy = my - r[1];
				return true;
			}
		}
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit h = hits.get(i);
			if (!h.contains(mx, my)) continue;
			if (h.slider() != null && event.button() == 0) {
				activeSlider = h;
				setSlider(h, mx);
			} else if (event.button() == 0 && h.left() != null) {
				h.left().run();
			} else if (event.button() == 1 && h.right() != null) {
				h.right().run();
			}
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	private static void setSlider(Hit h, double mx) {
		Opt.Slider s = h.slider();
		double frac = Math.max(0, Math.min(1, (mx - h.x()) / h.w()));
		double v = s.min() + Math.round(frac * (s.max() - s.min()) / s.step()) * s.step();
		v = Math.max(s.min(), Math.min(s.max(), v));
		if (v != s.get().getAsDouble()) s.set().accept(v);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging != null) {
			int x = (int) Math.max(0, Math.min(vw - panelW() - colW, event.x() / scale - dragDx));
			int y = (int) Math.max(0, Math.min(vh - headH, event.y() / scale - dragDy));
			positions().put(dragging, new int[]{x, y});
			return true;
		}
		if (activeSlider != null) {
			setSlider(activeSlider, event.x() / scale);
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging != null || activeSlider != null) {
			dragging = null;
			activeSlider = null;
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double rawX, double rawY, double sx, double sy) {
		double mx = rawX / scale, my = rawY / scale;
		if (mx >= vw - panelW()) {
			panelScroll -= sy * 20;
			return true;
		}
		for (int i = 0; i < categories.size(); i++) {
			Category c = categories.get(i);
			int[] p = position(c, i);
			if (mx >= p[0] && mx < p[0] + colW && my >= p[1]) {
				SCROLL.merge(c.name(), -sy * 20, Double::sum);
				return true;
			}
		}
		return super.mouseScrolled(rawX, rawY, sx, sy);
	}

	@Override
	public void removed() {
		if (editing != null) commitEdit();
		mod.configManager.save();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
