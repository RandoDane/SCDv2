package com.scd.client.hud;

import com.scd.client.config.ConfigManager;
import com.scd.client.config.HudLayout;
import com.scd.client.ui.Ui;
import com.scd.client.ui.widget.FlatButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Edits every enabled HUD in one place over the live game view: drag to move (snapping to screen
 * edges and centre lines), scroll over a box to resize, arrow keys to nudge the selected box
 * (Shift = 10px), R to reset it, B to toggle its background. Positions are re-anchored on drop, so
 * boxes keep their corner when the window is resized.
 */
public final class HudEditorScreen extends Screen {
	private static final int SNAP = 5;

	private final Screen parent;
	private final HudManager huds;
	private final ConfigManager config;
	private final List<HudManager.Placed> placed = new ArrayList<>();
	private HudElement selected;
	private HudElement dragging;
	private double grabDx, grabDy;
	private Integer guideX, guideY;

	public HudEditorScreen(Screen parent, HudManager huds, ConfigManager config) {
		super(Component.literal("SCD HUD Editor"));
		this.parent = parent;
		this.huds = huds;
		this.config = config;
	}

	@Override
	protected void init() {
		huds.setSuppressed(true);
		int bw = 110;
		int y = height - 24;
		addRenderableWidget(new FlatButton(width / 2 - bw - 3, y, bw, 16, "Reset all", () -> {
			for (HudElement e : huds.elements()) config.get().huds.put(e.id(), e.defaults().copy());
			config.save();
		}).danger());
		addRenderableWidget(new FlatButton(width / 2 + 3, y, bw, 16, "Done", this::onClose));
	}

	@Override
	public void removed() {
		huds.setSuppressed(false);
		config.save();
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().gui.setScreen(parent);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		placed.clear();
		for (HudElement e : huds.elements()) {
			if (!e.enabled()) continue;
			HudManager.Placed p = huds.place(e, true, width, height);
			if (p != null) placed.add(p);
		}

		if (guideX != null) g.fill(guideX, 0, guideX + 1, height, 0x80FFFFFF);
		if (guideY != null) g.fill(0, guideY, width, guideY + 1, 0x80FFFFFF);

		// Selected box last, so it's always on top of anything sharing its spot.
		placed.sort(java.util.Comparator.comparing(p -> p.element() == selected));
		HudManager.Placed hovered = at(mouseX, mouseY);
		for (HudManager.Placed p : placed) {
			huds.draw(g, p);
			boolean isSel = p.element() == selected;
			boolean isHover = hovered != null && hovered.element() == p.element();
			int edge = isSel ? Ui.theme().accent() : isHover ? 0xC0FFFFFF : 0x50FFFFFF;
			Ui.ring(g, p.x() - 1, p.y() - 1, p.width() + 2, p.height() + 2, 7, edge);
			if (isSel || isHover) {
				String tag = p.element().name() + "  " + Math.round(p.scale() * 100) + "%";
				int ty = p.y() > 12 ? p.y() - 11 : p.y() + p.height() + 3;
				Ui.rect(g, p.x() - 1, ty - 3, Ui.width(tag) + 10, 13, 4, Ui.theme().window());
				Ui.text(g, tag, p.x() + 4, ty, edge);
			}
		}

		String help = "Drag to move · Scroll resize · Arrows nudge · R reset · B background · Double-click: next in stack";
		Ui.rect(g, width / 2 - Ui.width(help) / 2 - 8, 4, Ui.width(help) + 16, 15, 7, Ui.theme().window());
		Ui.centered(g, help, width / 2, 8, Ui.theme().textSecondary());
		if (placed.isEmpty()) {
			Ui.centered(g, "No HUDs are enabled - turn some on in /scd first.", width / 2, height / 2, Ui.theme().textSecondary());
		}
		super.extractRenderState(g, mouseX, mouseY, partialTick);
	}

	private HudManager.Placed at(double mx, double my) {
		for (int i = placed.size() - 1; i >= 0; i--) {
			HudManager.Placed p = placed.get(i);
			if (Ui.contains(p.x(), p.y(), p.width(), p.height(), mx, my)) return p;
		}
		return null;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) return true;
		if (event.button() != 0) return false;
		HudManager.Placed p = at(event.x(), event.y());
		// Clicking the selected box again picks the next box stacked under the cursor, so
		// overlapping HUDs (e.g. Slayer and Dungeon score share a corner) can each be reached.
		if (p != null && p.element() == selected) {
			List<HudManager.Placed> stack = placed.stream().filter(q -> Ui.contains(q.x(), q.y(), q.width(), q.height(), event.x(), event.y())).toList();
			if (stack.size() > 1 && doubleClick) p = stack.get(0);
		}
		selected = p != null ? p.element() : null;
		if (p != null) {
			dragging = p.element();
			grabDx = event.x() - p.x();
			grabDy = event.y() - p.y();
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging == null) return super.mouseDragged(event, dx, dy);
		HudManager.Placed p = find(dragging);
		if (p == null) return true;
		int x = (int) Math.round(event.x() - grabDx);
		int y = (int) Math.round(event.y() - grabDy);
		guideX = null;
		guideY = null;
		// Snap each axis to screen edges or the centre line.
		int[] xs = {0, width / 2 - p.width() / 2, width - p.width()};
		int[] xGuides = {0, width / 2, width - 1};
		for (int i = 0; i < xs.length; i++) {
			if (Math.abs(x - xs[i]) <= SNAP) {
				x = xs[i];
				guideX = xGuides[i];
			}
		}
		int[] ys = {0, height / 2 - p.height() / 2, height - p.height()};
		int[] yGuides = {0, height / 2, height - 1};
		for (int i = 0; i < ys.length; i++) {
			if (Math.abs(y - ys[i]) <= SNAP) {
				y = ys[i];
				guideY = yGuides[i];
			}
		}
		x = Math.max(0, Math.min(width - p.width(), x));
		y = Math.max(0, Math.min(height - p.height(), y));
		huds.layout(dragging).placeAt(x, y, p.width(), p.height(), width, height);
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragging = null;
		guideX = null;
		guideY = null;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		HudManager.Placed p = at(mx, my);
		if (p == null || sy == 0) return false;
		HudLayout layout = huds.layout(p.element());
		layout.scale = Math.round(Math.max(0.5f, Math.min(2.5f, layout.scale + (sy > 0 ? 0.1f : -0.1f))) * 10) / 10f;
		selected = p.element();
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (selected != null) {
			HudManager.Placed p = find(selected);
			HudLayout layout = huds.layout(selected);
			int step = (event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0 ? 10 : 1;
			int dx = 0, dy = 0;
			switch (event.key()) {
				case GLFW.GLFW_KEY_LEFT -> dx = -step;
				case GLFW.GLFW_KEY_RIGHT -> dx = step;
				case GLFW.GLFW_KEY_UP -> dy = -step;
				case GLFW.GLFW_KEY_DOWN -> dy = step;
				case GLFW.GLFW_KEY_R -> {
					config.get().huds.put(selected.id(), selected.defaults().copy());
					return true;
				}
				case GLFW.GLFW_KEY_B -> {
					layout.background = !layout.background;
					return true;
				}
				default -> {
				}
			}
			if ((dx != 0 || dy != 0) && p != null) {
				int x = Math.max(0, Math.min(width - p.width(), p.x() + dx));
				int y = Math.max(0, Math.min(height - p.height(), p.y() + dy));
				layout.placeAt(x, y, p.width(), p.height(), width, height);
				return true;
			}
		}
		return super.keyPressed(event);
	}

	private HudManager.Placed find(HudElement e) {
		for (HudManager.Placed p : placed) if (p.element() == e) return p;
		return null;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	public static String hex(int argb) {
		return String.format(Locale.ROOT, "#%06X", argb & 0xFFFFFF);
	}
}
