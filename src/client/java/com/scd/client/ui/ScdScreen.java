package com.scd.client.ui;

import com.scd.client.ui.widget.FlatButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Base for every SCD menu: a centered themed panel with a title, a scrollable body described by
 * {@link #build(Rows)}, and a fixed footer (Back/Close plus optional extra buttons). Body widgets
 * are registered for input only and drawn manually inside a scissor rect, so scrolled-out rows are
 * both clipped and unclickable. {@link #rebuild()} re-runs build() while keeping the scroll offset
 * and expanded sections, which is how screens react to state changes.
 */
public abstract class ScdScreen extends Screen {
	private static final int PADDING = Ui.PAD;
	private static final int HEADER = Ui.HEADER_H + 4;
	private static final int FOOTER = 26;
	private static final int SCROLL_STEP = 18;

	protected final Screen parent;
	private final int preferredWidth;
	private final Set<String> expanded = new HashSet<>();
	private final List<Rows.Row> rows = new ArrayList<>();
	private int panelX, panelY, panelW, panelH;
	private int viewTop, viewBottom, contentHeight;
	private double scroll;

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

	/** Extra footer buttons shown before Back/Close, as label -> action pairs. */
	protected List<FooterButton> footer() {
		return List.of();
	}

	public record FooterButton(String label, Runnable action) {
	}

	/** "SCD › Slayer" style trail from the parent chain, shown when a screen has no live subtitle. */
	private String breadcrumb() {
		List<String> parts = new ArrayList<>();
		Screen p = parent;
		while (p instanceof ScdScreen s && parts.size() < 2) {
			parts.add(0, s.getTitle().getString());
			p = s.parent;
		}
		return String.join(" › ", parts);
	}

	/** Optional line under the title (live, re-read every frame). */
	protected String subtitle() {
		return null;
	}

	@Override
	protected void init() {
		rows.clear();
		panelW = Math.min(preferredWidth, width - 20);
		panelX = (width - panelW) / 2;
		int innerX = panelX + PADDING;
		int innerW = panelW - PADDING * 2;

		Rows builder = new Rows(innerX, innerW, expanded, this::rebuild);
		build(builder);
		rows.addAll(builder.rows);
		contentHeight = builder.height();

		int maxPanelH = height - 20;
		panelH = Math.min(maxPanelH, HEADER + contentHeight + FOOTER + PADDING);
		panelY = (height - panelH) / 2;
		viewTop = panelY + HEADER;
		viewBottom = panelY + panelH - FOOTER - 4;
		clampScroll();

		for (Rows.Row row : rows) for (AbstractWidget w : row.widgets) addWidget(w);
		positionRows();

		List<FooterButton> buttons = new ArrayList<>(footer());
		if (parent != null) buttons.add(new FooterButton("Back", this::onClose));
		buttons.add(new FooterButton("Close", () -> {
			onClosing();
			Minecraft.getInstance().gui.setScreen(null);
		}));
		int gap = 6;
		int bw = (innerW - gap * (buttons.size() - 1)) / buttons.size();
		int fy = panelY + panelH - FOOTER + 4;
		for (int i = 0; i < buttons.size(); i++) {
			FooterButton b = buttons.get(i);
			addRenderableWidget(new FlatButton(innerX + i * (bw + gap), fy, bw, 16, b.label(), b.action()));
		}
	}

	/** Re-runs {@link #build}, keeping scroll position and expanded sections. */
	public void rebuild() {
		double keep = scroll;
		int focusIndex = -1;
		List<AbstractWidget> before = rowWidgets();
		for (int i = 0; i < before.size(); i++) if (before.get(i) == getFocused()) focusIndex = i;
		rebuildWidgets();
		scroll = keep;
		clampScroll();
		positionRows();
		// Keep typing into the same field when a keystroke triggered the rebuild.
		List<AbstractWidget> after = rowWidgets();
		if (focusIndex >= 0 && focusIndex < after.size() && after.get(focusIndex) instanceof net.minecraft.client.gui.components.EditBox box) {
			setFocused(box);
			box.setCursorPosition(box.getValue().length());
			box.setHighlightPos(box.getValue().length());
		}
	}

	private List<AbstractWidget> rowWidgets() {
		List<AbstractWidget> out = new ArrayList<>();
		for (Rows.Row row : rows) out.addAll(row.widgets);
		return out;
	}

	/** Hook for saving before the screen goes away (Back, Close or Esc). */
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
		if (mouseY < viewTop || mouseY > viewBottom) return false;
		scroll -= scrollY * SCROLL_STEP;
		clampScroll();
		positionRows();
		return true;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		positionRows();
		Theme t = Ui.theme();
		Ui.panel(g, panelX, panelY, panelW, panelH);
		String sub = subtitle();
		Ui.header(g, panelX + PADDING, panelY + 10, panelW - PADDING * 2, getTitle().getString(), sub != null ? sub : breadcrumb());

		g.enableScissor(panelX, viewTop, panelX + panelW, viewBottom);
		for (Rows.Row row : rows) {
			int top = viewTop + row.top - (int) scroll;
			if (top > viewBottom || top + row.height < viewTop) continue;
			if (row.painter != null) row.painter.paint(g, panelX + PADDING, top, panelW - PADDING * 2, mouseX, mouseY);
			for (AbstractWidget w : row.widgets) {
				boolean wasVisible = w.visible;
				w.visible = true; // draw partially-visible widgets; the scissor clips them
				w.extractRenderState(g, mouseX, mouseY, partialTick);
				w.visible = wasVisible;
			}
		}
		g.disableScissor();

		int viewH = viewBottom - viewTop;
		if (contentHeight > viewH) {
			int trackX = panelX + panelW - 5;
			int thumbH = Math.max(12, viewH * viewH / contentHeight);
			int thumbY = viewTop + (int) ((viewH - thumbH) * (scroll / (contentHeight - viewH)));
			g.fill(trackX, viewTop, trackX + 2, viewBottom, 0x30FFFFFF);
			g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, t.accent());
		}
		Ui.divider(g, panelX + PADDING, panelY + panelH - FOOTER, panelW - PADDING * 2);
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
}
