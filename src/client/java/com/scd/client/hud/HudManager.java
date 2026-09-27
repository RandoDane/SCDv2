package com.scd.client.hud;

import com.scd.client.config.ConfigManager;
import com.scd.client.config.HudLayout;
import com.scd.client.core.ScdLog;
import com.scd.client.ui.Ui;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Registers a single Fabric HUD layer that draws every SCD element. Each element is rendered
 * inside its own guard, so a broken HUD can't take the others down, and in a balanced pose push/pop.
 */
public final class HudManager {
	/** Placed box of one element in GUI coordinates (already scaled). */
	public record Placed(HudElement element, HudBox box, int x, int y, int width, int height, float scale) {
	}

	private final ConfigManager config;
	private final BooleanSupplier active;
	private final List<HudElement> elements = new ArrayList<>();
	/** Element whose sample content is forced on (appearance screen), or null. */
	private volatile String previewId;
	private volatile boolean suppressed;

	public HudManager(ConfigManager config, BooleanSupplier active) {
		this.config = config;
		this.active = active;
	}

	public void add(HudElement element) {
		elements.add(element);
	}

	public List<HudElement> elements() {
		return List.copyOf(elements);
	}

	public HudLayout layout(HudElement element) {
		return config.hud(element.id(), element.defaults());
	}

	public void setPreview(String elementId) {
		previewId = elementId;
	}

	/** The HUD editor draws its own previews - suppress the live layer while it's open. */
	public void setSuppressed(boolean suppressed) {
		this.suppressed = suppressed;
	}

	public void register() {
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("scd", "hud"), (g, delta) -> {
			if (suppressed) return;
			boolean live = active.getAsBoolean();
			for (HudElement e : elements) {
				boolean preview = e.id().equals(previewId);
				if (!preview && (!live || !e.enabled())) continue;
				ScdLog.guard(e.name() + " HUD", () -> {
					Placed p = place(e, preview, g.guiWidth(), g.guiHeight());
					if (p != null) draw(g, p);
				});
			}
		});
	}

	/** Builds and positions an element; null if it has nothing to show. */
	public Placed place(HudElement e, boolean preview, int screenW, int screenH) {
		HudBox box = e.build(preview);
		if (box == null || box.isEmpty()) return null;
		HudLayout layout = layout(e);
		float scale = Math.max(0.5f, Math.min(2.5f, layout.scale));
		int w = Math.round(box.width() * scale);
		int h = Math.round(box.height() * scale);
		return new Placed(e, box, layout.resolveX(screenW, w), layout.resolveY(screenH, h), w, h, scale);
	}

	public void draw(GuiGraphicsExtractor g, Placed p) {
		HudLayout layout = layout(p.element());
		var pose = g.pose();
		pose.pushMatrix();
		try {
			pose.translate(p.x(), p.y());
			pose.scale(p.scale(), p.scale());
			if (layout.background) Ui.hudPanel(g, 0, 0, p.box().width(), p.box().height(), color(layout, HudColor.BACKGROUND));
			p.box().render(g, role -> color(layout, role));
		} finally {
			pose.popMatrix();
		}
	}

	public static int color(HudLayout layout, HudColor role) {
		Integer override = layout.colors.get(role.id());
		return override != null ? override : role.themeDefault(Ui.theme());
	}
}
