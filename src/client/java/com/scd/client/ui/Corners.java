package com.scd.client.ui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Anti-aliased rounded corners as textures, generated once per radius (in physical pixels) and
 * drawn 1:1 in pixel space. A rounded rectangle is then 4 corner blits + 3 fills - instead of
 * hundreds of single-pixel fills per rectangle, which made the menus stutter.
 */
final class Corners {
	private static final Map<Integer, Identifier> TEXTURES = new HashMap<>();

	private Corners() {
	}

	/** White disc of diameter 2R with an anti-aliased edge; tinted per draw. */
	static Identifier texture(int r) {
		return TEXTURES.computeIfAbsent(r, radius -> {
			int size = radius * 2;
			NativeImage img = new NativeImage(size, size, true);
			for (int y = 0; y < size; y++) {
				for (int x = 0; x < size; x++) {
					double dx = x + 0.5 - radius, dy = y + 0.5 - radius;
					double cov = Math.max(0, Math.min(1, radius - Math.sqrt(dx * dx + dy * dy) + 0.5));
					img.setPixel(x, y, ((int) Math.round(cov * 255) << 24) | 0xFFFFFF);
				}
			}
			Identifier id = Identifier.fromNamespaceAndPath("scd", "dynamic/corner_" + radius);
			Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "scd corner " + radius, img));
			return id;
		});
	}

	/** Rounded rect in physical-pixel coordinates (the caller has set up a 1 unit = 1 pixel pose). */
	static void fill(GuiGraphicsExtractor g, int x, int y, int w, int h, float radius, int color) {
		if (w <= 0 || h <= 0) return;
		int r = (int) Math.min(Math.floor(radius), Math.min(w, h) / 2);
		if (r <= 0) {
			g.fill(x, y, x + w, y + h, color);
			return;
		}
		g.fill(x + r, y, x + w - r, y + h, color);
		g.fill(x, y + r, x + r, y + h - r, color);
		g.fill(x + w - r, y + r, x + w, y + h - r, color);
		Identifier tex = texture(r);
		int s = r * 2;
		g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0, 0, r, r, s, s, color);
		g.blit(RenderPipelines.GUI_TEXTURED, tex, x + w - r, y, r, 0, r, r, s, s, color);
		g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y + h - r, 0, r, r, r, s, s, color);
		g.blit(RenderPipelines.GUI_TEXTURED, tex, x + w - r, y + h - r, r, r, r, r, s, s, color);
	}
}
