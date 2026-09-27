package com.scd.client.ui;

import com.scd.client.ui.widget.FlatButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Every SCD page lives in one window: a sidebar with the page list on the left, and on the right a
 * header (title, subtitle, back/close), a scrollable body described by {@link #build(Rows)} and an
 * optional footer with the page's actions. Body widgets are registered for input only and drawn
 * inside a scissor rect, so scrolled-out rows are both clipped and unclickable. {@link #rebuild()}
 * re-runs build() keeping scroll, expanded sections and text focus.
 */
public abstract class ScdScreen extends Screen {
	private static final int SIDEBAR_W = 118;
	private static final int HEADER_H = 36;
	private static final int FOOTER_H = 30;
	private static final int NAV_ROW_MAX = 22;
	private int navRow = NAV_ROW_MAX;
	private static final int SCROLL_STEP = 20;

	private static final net.minecraft.resources.Identifier LOGO = net.minecraft.resources.Identifier.fromNamespaceAndPath("scd", "logo");

	protected final Screen parent;
	private final int preferredWidth;
	private final Set<String> expanded = new HashSet<>();
	private final List<Rows.Row> rows = new ArrayList<>();
	private int winX, winY, winW, winH;
	private int bodyX, bodyW, viewTop, viewBottom, contentHeight;
	private boolean sidebar;
	private double scroll;
	private FlatButton backButton;

	protected ScdScreen(String title, Screen parent, int preferredWidth) {
		super(Component.literal(title));
		this.parent = parent;
		this.preferredWidth = preferredWidth;
	}

	protected ScdScreen(String title, Screen parent) {
		this(title, parent, 320);
	}

	/** Describe the body. Called on open, on resize and on every {@link #rebuild()}. */
	protected abstract void build(Rows rows);

	/** The page's actions, shown right-aligned in the footer (first one is the primary action). */
	protected List<FooterButton> footer() {
		return List.of();
	}

	public record FooterButton(String label, Runnable action) {
	}

	/** Muted text next to the title (live, re-read every frame). */
	protected String subtitle() {
		return null;
	}

	/** Which sidebar entry is highlighted; sub-pages inherit their parent's. */
	protected String navKey() {
		return parent instanceof ScdScreen s ? s.navKey() : null;
	}

	@Override
	protected void init() {
		rows.clear();
		sidebar = width >= 400;
		winW = Math.min(width - 16, (sidebar ? SIDEBAR_W : 0) + Math.max(preferredWidth, 340) + 20);
		winH = Math.min(height - 16, 380);
		winX = (width - winW) / 2;
		winY = (height - winH) / 2;
		int contentLeft = winX + (sidebar ? SIDEBAR_W : 0);
		bodyX = contentLeft + Ui.PAD + 4;
		bodyW = winX + winW - Ui.PAD - 6 - bodyX;
		boolean hasFooter = !footer().isEmpty();
		viewTop = winY + HEADER_H + 8;
		viewBottom = winY + winH - (hasFooter ? FOOTER_H : 8);

		Rows builder = new Rows(bodyX, bodyW, expanded, this::rebuild);
		build(builder);
		rows.addAll(builder.rows);
		contentHeight = builder.height();
		clampScroll();
		for (Rows.Row row : rows) for (AbstractWidget w : row.widgets) addWidget(w);
		positionRows();

		int right = winX + winW - Ui.PAD;
		addRenderableWidget(new FlatButton(right - 18, winY + 9, 18, 18, "×", this::closeAll).ghost().tooltip("Close"));
		backButton = null;
		if (parent != null) {
			backButton = addRenderableWidget(new FlatButton(contentLeft + Ui.PAD, winY + 9, 18, 18, "‹", this::onClose).ghost().tooltip("Back"));
		}
		if (hasFooter) {
			List<FooterButton> buttons = footer();
			int bx = right;
			for (int i = buttons.size() - 1; i >= 0; i--) {
				FooterButton b = buttons.get(i);
				int bw = Math.max(64, Ui.width(b.label()) + 20);
				bx -= bw;
				FlatButton button = new FlatButton(bx, winY + winH - FOOTER_H + 6, bw, Ui.CONTROL_H, b.label(), b.action());
				if (i == 0) button.primary();
				addRenderableWidget(button);
				bx -= 6;
			}
		}
		if (sidebar) {
			navRow = computeNavRow();
			int ny = winY + 52;
			String lastSection = null;
			for (Nav.Item item : Nav.items()) {
				if (!Objects.equals(item.section(), lastSection)) {
					ny += lastSection == null ? 0 : 8;
					ny += 14;
					lastSection = item.section();
				}
				addRenderableWidget(new NavRow(winX + 8, ny, SIDEBAR_W - 16, navRow - 2, item, Objects.equals(item.key(), navKey())));
				ny += navRow;
			}
		}
	}

	/** Shrinks sidebar rows (down to 16px) so every page fits the window height. */
	private int computeNavRow() {
		List<Nav.Item> items = Nav.items();
		long sections = items.stream().map(Nav.Item::section).distinct().count();
		int fixed = 52 + (int) sections * 14 + (int) Math.max(0, sections - 1) * 8 + 8;
		int avail = winH - fixed;
		return Math.max(16, Math.min(NAV_ROW_MAX, items.isEmpty() ? NAV_ROW_MAX : avail / items.size()));
	}

	private void closeAll() {
		onClosing();
		Minecraft.getInstance().gui.setScreen(null);
	}

	/** Re-runs {@link #build}, keeping scroll position, expanded sections and text focus. */
	public void rebuild() {
		double keep = scroll;
		int focusIndex = -1;
		List<AbstractWidget> before = rowWidgets();
		for (int i = 0; i < before.size(); i++) if (before.get(i) == getFocused()) focusIndex = i;
		rebuildWidgets();
		scroll = keep;
		clampScroll();
		positionRows();
		List<AbstractWidget> after = rowWidgets();
		if (focusIndex >= 0 && focusIndex < after.size() && after.get(focusIndex) instanceof com.scd.client.ui.widget.TextField box) {
			setFocused(box);
			box.moveCursorToEnd();
		}
	}

	private List<AbstractWidget> rowWidgets() {
		List<AbstractWidget> out = new ArrayList<>();
		for (Rows.Row row : rows) out.addAll(row.widgets);
		return out;
	}

	/** Hook for saving before the screen goes away (back, close or Esc). */
	protected void onClosing() {
	}

	@Override
	public void onClose() {
		onClosing();
		Minecraft.getInstance().gui.setScreen(parent);
	}

	private void clampScroll() {
		double max = Math.max(0, contentHeight - (viewBottom - viewTop));
		scroll = Math.max(0, Math.min(max, scroll));
	}

	private void positionRows() {
		for (Rows.Row row : rows) {
			int top = viewTop + row.top - (int) scroll;
			for (int i = 0; i < row.widgets.size(); i++) {
				AbstractWidget w = row.widgets.get(i);
				int wy = top + row.widgetOffsets.get(i);
				w.setY(wy);
				w.visible = wy >= viewTop && wy + w.getHeight() <= viewBottom;
			}
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;
		if (mouseY < viewTop || mouseY > viewBottom || mouseX < bodyX - 8) return false;
		scroll -= scrollY * SCROLL_STEP;
		clampScroll();
		positionRows();
		return true;
	}

	/** Average CPU time (ns) SCD spends building one frame of this window - for performance tests. */
	public static volatile long avgFrameNanos;

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		long start = System.nanoTime();
		draw(g, mouseX, mouseY, partialTick);
		long took = System.nanoTime() - start;
		avgFrameNanos = avgFrameNanos == 0 ? took : (avgFrameNanos * 15 + took) / 16;
	}

	private void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		positionRows();
		Theme t = Ui.theme();
		Ui.panel(g, winX, winY, winW, winH);
		int contentLeft = winX + (sidebar ? SIDEBAR_W : 0);
		if (sidebar) {
			Ui.rect(g, winX + 1, winY + 1, SIDEBAR_W - 1, winH - 2, 7, t.sidebar());
			g.fill(winX + SIDEBAR_W, winY + 1, winX + SIDEBAR_W + 1, winY + winH - 1, t.border());
			// scd.wtf logo: 64px pixel art on a 4px grid, drawn at 16 GUI px so it stays crisp at every GUI scale.
			g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, LOGO, winX + 12, winY + 14, 16, 16);
			Ui.title(g, "SCD", winX + 34, winY + 14, t.textPrimary());
			String lastSection = null;
			int ny = winY + 52;
			for (Nav.Item item : Nav.items()) {
				if (!Objects.equals(item.section(), lastSection)) {
					ny += lastSection == null ? 0 : 8;
					Ui.section(g, item.section(), winX + 14, ny + 2);
					ny += 14;
					lastSection = item.section();
				}
				ny += navRow;
			}
		}

		int titleX = contentLeft + Ui.PAD + (backButton != null ? 24 : 4);
		String title = getTitle().getString();
		Ui.title(g, title, titleX, winY + 12, t.textPrimary());
		String sub = subtitle();
		if (sub != null && !sub.isBlank()) {
			int sx = titleX + Ui.widthTitle(title) + 10;
			g.fill(sx - 5, winY + 14, sx - 4, winY + 26, t.border());
			Ui.text(g, Ui.ellipsize(sub, winX + winW - 40 - sx), sx, winY + 16, t.textMuted());
		}
		g.fill(contentLeft + 1, winY + HEADER_H, winX + winW - 1, winY + HEADER_H + 1, t.border());

		g.enableScissor(contentLeft + 1, viewTop - 2, winX + winW - 1, viewBottom);
		for (Rows.Row row : rows) {
			int top = viewTop + row.top - (int) scroll;
			if (top > viewBottom || top + row.height < viewTop) continue;
			if (row.painter != null) row.painter.paint(g, bodyX, top, bodyW, mouseX, mouseY);
			for (AbstractWidget w : row.widgets) {
				boolean wasVisible = w.visible;
				w.visible = true;
				w.extractRenderState(g, mouseX, mouseY, partialTick);
				w.visible = wasVisible;
			}
		}
		g.disableScissor();

		int viewH = viewBottom - viewTop;
		if (contentHeight > viewH) {
			int trackX = winX + winW - 7;
			int thumbH = Math.max(16, viewH * viewH / contentHeight);
			int thumbY = viewTop + (int) ((viewH - thumbH) * (scroll / (contentHeight - viewH)));
			Ui.rect(g, trackX, thumbY, 3, thumbH, 1.5f, Ui.blend(t.border(), t.textMuted(), 0.4f));
		}
		if (!footer().isEmpty()) g.fill(contentLeft + 1, viewBottom, winX + winW - 1, viewBottom + 1, t.border());
		super.extractRenderState(g, mouseX, mouseY, partialTick);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** Opens a screen from anywhere - deferred a tick so a closing chat screen can't overwrite it. */
	public static void open(Screen screen) {
		Minecraft.getInstance().execute(() -> Minecraft.getInstance().gui.setScreen(screen));
	}

	/** Sidebar entry: item icon + label; the active page gets an accent-tinted pill. */
	private final class NavRow extends AbstractWidget {
		private final Nav.Item item;
		private final boolean selected;

		NavRow(int x, int y, int w, int h, Nav.Item item, boolean selected) {
			super(x, y, w, h, Component.literal(item.label()));
			this.item = item;
			this.selected = selected;
		}

		@Override
		public void onClick(MouseButtonEvent event, boolean doubleClick) {
			playDownSound(Minecraft.getInstance().getSoundManager());
			onClosing();
			Minecraft.getInstance().gui.setScreen(item.open().get());
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
			Theme t = Ui.theme();
			int x = getX(), y = getY(), w = getWidth(), h = getHeight();
			if (selected) Ui.rect(g, x, y, w, h, Ui.RADIUS, t.accentOver(t.sidebar(), 0.18f));
			else if (isHovered()) Ui.rect(g, x, y, w, h, Ui.RADIUS, Ui.blend(t.sidebar(), 0xFFFFFFFF, 0.05f));
			var pose = g.pose();
			pose.pushMatrix();
			float iconScale = Math.min(0.875f, (h - 2) / 16f);
			pose.translate(x + 6, y + (h - 16 * iconScale) / 2);
			pose.scale(iconScale, iconScale);
			g.item(item.icon().get(), 0, 0);
			pose.popMatrix();
			int color = selected ? Ui.blend(t.accent(), 0xFFFFFFFF, 0.25f) : isHovered() ? t.textPrimary() : t.textSecondary();
			if (selected) Ui.bold(g, item.label(), x + 26, y + (h - 8) / 2, color);
			else Ui.text(g, item.label(), x + 26, y + (h - 8) / 2, color);
		}

		@Override
		protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
			defaultButtonNarrationText(output);
		}
	}
}
