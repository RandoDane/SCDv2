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
	private static final int COL_W = 124, HEAD_H = 16, ROW_H = 14, OPT_H = 13, GAP = 6;
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
	/** Menu units -> GUI units, and the menu's virtual size (the menu ignores the game's GUI scale). */
	private float scale = 1;
	private int vw, vh;

	/** Side panel block; options are re-read every frame (carry lists, stats change live). */
	public record PanelSection(String title, java.util.function.Supplier<List<Opt>> options) {
	}

	/** The text option being edited, and its unsaved value. */
	private Opt.Text editing;
	private String editBuffer = "";

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
	 * The menu keeps one on-screen size whatever the GUI scale: 2 screen pixels per menu unit at
	 * 1080p (GUI scale 2 there), proportional to the window height, times the "Menu size" setting.
	 */
	private void updateScale() {
		var window = net.minecraft.client.Minecraft.getInstance().getWindow();
		// Whole steps from the window height (a 1080p screen gives 2 even when windowed and a bit
		// shorter), then the Menu size setting fine-tunes it.
		float wanted = Math.max(1, Math.round(window.getHeight() / 540f)) * mod.config().general.menuScale / 100f;
		// Snap to quarter pixels: fonts exist for exactly those densities, so glyphs map 1:1 to pixels.
		float density = quarter(wanted);
		scale = density / window.getGuiScale();
		vw = Math.round(width / scale);
		vh = Math.round(height / scale);
		// Text size is applied as its own (also snapped) scale with a font made for that density.
		textDensity = quarter(density * mod.config().general.menuTextSize / 100f);
	}

	private float textDensity = 2;

	private static float quarter(float v) {
		return Math.max(0.5f, Math.min(6f, Math.round(v * 4) / 4f));
	}

	/** Font + text scale for drawing (and measuring) menu text; always paired with {@link #endText()}. */
	private void beginText() {
		Ui.setHudDensity(textDensity);
		Ui.setTextScale(textDensity / (scale * net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale()));
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
		int perRow = Math.max(1, (vw - panelW() - GAP) / (COL_W + GAP));
		int rows = (categories.size() + perRow - 1) / perRow;
		int x = GAP + (index % perRow) * (COL_W + GAP);
		int y = GAP + (index / perRow) * (vh / rows);
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
			Ui.rect(g, GAP, vh - 20, w, 16, 4, t.window(), t.border());
			Ui.text(g, Ui.ellipsize(hover, w - 12), GAP + 6, vh - 16, t.textSecondary());
		}
	}

	/** A column stops above any column placed below it (and at the screen bottom), scrolling inside. */
	private int bottomLimit(int index, int[] pos) {
		int limit = vh - GAP;
		for (int j = 0; j < categories.size(); j++) {
			if (j == index) continue;
			int[] o = position(categories.get(j), j);
			boolean overlapX = o[0] < pos[0] + COL_W && o[0] + COL_W > pos[0];
			if (o[1] <= pos[1]) continue;
			if (overlapX && o[1] > pos[1]) limit = Math.min(limit, o[1] - GAP);
		}
		return limit;
	}

	private String drawCategory(GuiGraphicsExtractor g, Category c, int[] pos, int maxBottom, int mx, int my) {
		Theme t = Ui.theme();
		int x = pos[0], y = pos[1];
		// Header: the drag handle.
		Ui.rect(g, x, y, COL_W, HEAD_H, 3, t.accent());
		Ui.centered(g, c.name().toUpperCase(java.util.Locale.ROOT), x + COL_W / 2, y + 4, 0xFFFFFFFF);
		headerRects.put(c.name(), new int[]{x, y, COL_W, HEAD_H});
		// Right-click on the header folds the category away (left-drag moves it).
		if (mod.config().general.clickGuiCollapsed.contains(c.name())) return null;
		int top = y + HEAD_H;
		double scroll = SCROLL.getOrDefault(c.name(), 0.0);
		// +1: a background-coloured line along the bottom, inside the accent outline.
		int contentH = contentHeight(c) + 1;
		int visibleH = Math.max(0, Math.min(contentH, maxBottom - top));
		scroll = Math.max(0, Math.min(scroll, contentH - visibleH));
		SCROLL.put(c.name(), scroll);
		g.fill(x, top, x + COL_W, top + visibleH, t.window());
		g.outline(x, y, COL_W, HEAD_H + visibleH, t.accent());
		// Rows sit inside a 1px background-coloured inner border (sides and bottom) within the outline.
		g.enableScissor(x + 2, top, x + COL_W - 2, top + visibleH - 1);
		String hover = null;
		int ry = top - (int) scroll;
		for (Module m : c.modules()) {
			String key = c.name() + "/" + m.name;
			boolean on = m.on != null && m.on.getAsBoolean();
			boolean hot = mx >= x && mx < x + COL_W && my >= Math.max(ry, top) && my < Math.min(ry + ROW_H, top + visibleH);
			int bg = on ? (t.accent() & 0x00FFFFFF) | 0x70000000 : hot ? t.cardHover() : t.window();
			g.fill(x + 2, ry, x + COL_W - 2, ry + ROW_H, bg);
			Ui.text(g, Ui.ellipsize(m.name, COL_W - 14), x + 6, ry + 3, on ? 0xFFFFFFFF : t.textPrimary());
			if (hot) hover = m.description;
			if (visible(ry, ROW_H, top, visibleH)) {
				// Rows without options can't be expanded.
				Runnable expand = m.options.isEmpty() ? null : () -> toggleExpanded(key);
				hits.add(new Hit(x, Math.max(ry, top), COL_W, ROW_H, m.on != null ? () -> m.set.accept(!m.on.getAsBoolean()) : expand,
						expand, null, m.description));
			}
			ry += ROW_H;
			if (EXPANDED.contains(key) && !m.options.isEmpty()) {
				for (Opt o : m.options) {
					drawOpt(g, o, x + 2, ry, COL_W - 4, 6, top, visibleH, mx, my);
					ry += OPT_H;
				}
				g.fill(x + 2, ry, x + COL_W - 2, ry + 1, t.border());
				ry += 1;
			}
		}
		g.disableScissor();
		return hover;
	}

	private static boolean visible(int y, int h, int top, int visibleH) {
		return y + h > top && y < top + visibleH;
	}

	private int contentHeight(Category c) {
		int h = 0;
		for (Module m : c.modules()) {
			h += ROW_H;
			if (!m.options.isEmpty() && EXPANDED.contains(c.name() + "/" + m.name)) h += m.options.size() * OPT_H + 1;
		}
		return h;
	}

	private void toggleExpanded(String key) {
		if (!EXPANDED.remove(key)) EXPANDED.add(key);
	}

	/** One option line; registers its click target. */
	private void drawOpt(GuiGraphicsExtractor g, Opt o, int x, int y, int w, int indent, int clipTop, int clipH, int mx, int my) {
		Theme t = Ui.theme();
		g.fill(x, y, x + w, y + OPT_H, t.sidebar());
		int tx = x + indent;
		boolean vis = visible(y, OPT_H, clipTop, clipH);
		int hy = Math.max(y, clipTop);
		switch (o) {
			case Opt.Toggle tg -> {
				boolean on = tg.get().getAsBoolean();
				Ui.text(g, Ui.ellipsize(tg.label(), w - indent - 16), tx, y + 2, t.textSecondary());
				int bx = x + w - 11;
				Ui.rect(g, bx, y + 3, 7, 7, 2, on ? t.accent() : t.trackOff());
				if (vis) hits.add(new Hit(x, hy, w, OPT_H, () -> tg.set().accept(!tg.get().getAsBoolean()), null, null, null));
			}
			case Opt.Slider sl -> {
				double v = sl.get().getAsDouble();
				String val = sl.fmt().apply(v);
				Ui.text(g, Ui.ellipsize(sl.label(), w / 2), tx, y + 2, t.textSecondary());
				Ui.rightAligned(g, val, x + w - 4, y + 2, t.textPrimary());
				int trackX = tx, trackW = w - indent - 6;
				float frac = (float) ((v - sl.min()) / (sl.max() - sl.min()));
				g.fill(trackX, y + OPT_H - 2, trackX + trackW, y + OPT_H - 1, t.trackOff());
				g.fill(trackX, y + OPT_H - 2, trackX + Math.round(trackW * frac), y + OPT_H - 1, t.accent());
				if (vis) hits.add(new Hit(trackX, hy, trackW, OPT_H, null, null, sl, null));
			}
			case Opt.Cycle cy -> {
				Ui.text(g, Ui.ellipsize(cy.label(), w / 2), tx, y + 2, t.textSecondary());
				Ui.rightAligned(g, cy.fmt().apply(cy.get().get()), x + w - 4, y + 2, t.accent());
				if (vis) hits.add(new Hit(x, hy, w, OPT_H, () -> step(cy, 1), () -> step(cy, -1), null, null));
			}
			case Opt.Action a -> {
				boolean hot = mx >= x && mx < x + w && my >= y && my < y + OPT_H;
				Ui.text(g, Ui.ellipsize(a.label(), w - indent - 4), tx, y + 2, hot ? t.accent() : t.textPrimary());
				if (vis) hits.add(new Hit(x, hy, w, OPT_H, a.run(), null, null, null));
			}
			case Opt.Text tx2 -> {
				boolean editingThis = editing == tx2;
				Ui.text(g, Ui.ellipsize(tx2.label(), w / 3), tx, y + 2, t.textSecondary());
				String v = editingThis ? editBuffer + ((System.currentTimeMillis() / 500) % 2 == 0 ? "_" : " ") : tx2.get().get();
				int vx = x + w / 3 + 4, vw = w - w / 3 - 8;
				g.fill(vx - 2, y + 1, x + w - 2, y + OPT_H - 1, editingThis ? t.field() : t.window());
				// Show the end of what's being typed.
				String shown = v;
				while (Ui.width(shown) > vw && shown.length() > 1) shown = shown.substring(1);
				if (!editingThis) shown = Ui.ellipsize(v, vw);
				Ui.text(g, shown, vx, y + 2, editingThis ? t.textPrimary() : t.textSecondary());
				if (vis) hits.add(new Hit(x, hy, w, OPT_H, () -> startEdit(tx2), null, null, null));
			}
			case Opt.Color col -> {
				Integer v = col.get().get();
				Ui.text(g, Ui.ellipsize(col.label(), w - 40), tx, y + 2, t.textSecondary());
				int sx = x + w - 12;
				if (v == null) Ui.rightAligned(g, "theme", sx - 4, y + 2, t.textMuted());
				g.fill(sx, y + 3, sx + 7, y + 10, v == null ? t.accent() : v);
				g.outline(sx, y + 3, 7, 7, t.border());
				if (vis) hits.add(new Hit(x, hy, w, OPT_H, () -> stepColor(col, 1), () -> stepColor(col, -1), null, null));
			}
			case Opt.Buttons bs -> {
				int n = bs.names().size();
				int capW = bs.label().isEmpty() ? 0 : Math.min(w / 2, Ui.width(bs.label()) + 8);
				if (capW > 0) Ui.text(g, Ui.ellipsize(bs.label(), capW - 4), tx, y + 2, t.textSecondary());
				int bx = x + capW + (capW > 0 ? 0 : 2), bw = (w - capW - 2) / Math.max(1, n);
				for (int i = 0; i < n; i++) {
					int zx = bx + i * bw;
					boolean hot = mx >= zx && mx < zx + bw && my >= y && my < y + OPT_H;
					g.fill(zx + 1, y + 1, zx + bw - 1, y + OPT_H - 1, hot ? t.cardHover() : t.card());
					Ui.centered(g, bs.names().get(i), zx + bw / 2, y + 2, t.textPrimary());
					if (vis) hits.add(new Hit(zx, hy, bw, OPT_H, bs.actions().get(i), null, null, null));
				}
			}
			case Opt.Info in -> {
				Ui.text(g, Ui.ellipsize(in.label(), w / 2), tx, y + 2, t.textMuted());
				Ui.rightAligned(g, Ui.ellipsize(in.value().get(), w / 2 - 4), x + w - 4, y + 2, t.textSecondary());
			}
		}
	}

	private void startEdit(Opt.Text t) {
		editing = t;
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
		return true;
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
		if (editing == null) return super.keyPressed(event);
		switch (event.key()) {
			case org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE -> {
				if (!editBuffer.isEmpty()) editBuffer = editBuffer.substring(0, editBuffer.length() - 1);
			}
			case org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER -> commitEdit();
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
		g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, LOGO, px + 8, 6, 16, 16);
		int top = 28;
		int y = top - (int) panelScroll;
		g.enableScissor(px, top, vw, vh);
		for (PanelSection s : panel) {
			Ui.section(g, s.title(), px + 8, y + 3);
			y += 14;
			for (Opt o : s.options().get()) {
				drawOpt(g, o, px + 4, y, pw - 8, 5, top, vh - top, mx, my);
				y += OPT_H + 1;
			}
			y += 6;
		}
		int contentH = y + (int) panelScroll - top;
		panelScroll = Math.max(0, Math.min(panelScroll, Math.max(0, contentH - (vh - top))));
		g.disableScissor();
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
			int x = (int) Math.max(0, Math.min(vw - panelW() - COL_W, event.x() / scale - dragDx));
			int y = (int) Math.max(0, Math.min(vh - HEAD_H, event.y() / scale - dragDy));
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
			if (mx >= p[0] && mx < p[0] + COL_W && my >= p[1]) {
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
