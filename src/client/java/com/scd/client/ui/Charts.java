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
		for (int px = 0; px < w; px++) {
			double pos = px / (double) (w - 1) * (values.length - 1);
			int i = (int) Math.floor(pos);
			double f = pos - i;
			double a = values[Math.min(i, values.length - 1)], b = values[Math.min(i + 1, values.length - 1)];
			// smoothstep between samples for a softer curve than straight segments
			double s = f * f * (3 - 2 * f);
			double v = a + (b - a) * s;
			int vy = y + h - (int) Math.round(v / max * (h - 2)) - 1;
			for (int yy = vy; yy < y + h; yy++) {
				float k = 1f - (yy - vy) / (float) Math.max(1, (y + h - vy));
				int alpha = (int) (0x70 * k);
				if (alpha > 3) g.fill(x + px, yy, x + px + 1, yy + 1, (alpha << 24) | rgb);
			}
			int from = prevY < 0 ? vy : Math.min(prevY, vy);
			int to = prevY < 0 ? vy : Math.max(prevY, vy);
			g.fill(x + px, from, x + px + 1, to + 1, 0xFF000000 | rgb);
			prevY = vy;
		}
	}
}
