package com.scd.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Small chart primitives drawn with 1-pixel columns (GUI rendering has no line/polygon primitive). */
public final class Charts {
	private Charts() {
	}

	/**
	 * Area chart: a soft gradient fill under a smoothed line, with faint horizontal grid lines and the
	 * max value labelled. Empty or all-zero data shows a flat baseline.
	 */
	public static void area(GuiGraphicsExtractor g, int x, int y, int w, int h, double[] values, int color) {
		Theme t = Ui.theme();
		for (int i = 0; i <= 3; i++) {
			int gy = y + h * i / 3;
			g.fill(x, gy, x + w, gy + 1, Ui.blend(t.card(), t.border(), 0.7f));
		}
		if (values == null || values.length < 2) return;
		double max = 0;
		for (double v : values) max = Math.max(max, v);
		if (max <= 0) max = 1;
		int rgb = color & 0xFFFFFF;
		int prevY = -1;
		// One gradient fill per column plus the line: cheap enough to redraw every frame.
		for (int px = 0; px < w; px++) {
			double pos = px / (double) (w - 1) * (values.length - 1);
			int i = (int) Math.floor(pos);
			double f = pos - i;
			double a = values[Math.min(i, values.length - 1)], b = values[Math.min(i + 1, values.length - 1)];
			double sm = f * f * (3 - 2 * f);
			double v = a + (b - a) * sm;
			int vy = y + h - (int) Math.round(v / max * (h - 2)) - 1;
			if (vy < y + h - 1) g.fillGradient(x + px, vy, x + px + 1, y + h, (0x60 << 24) | rgb, rgb);
			int from = prevY < 0 ? vy : Math.min(prevY, vy);
			int to = prevY < 0 ? vy : Math.max(prevY, vy);
			g.fill(x + px, from, x + px + 1, to + 1, 0xFF000000 | rgb);
			prevY = vy;
		}
	}
}
