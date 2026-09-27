package com.scd.client.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Where and how one HUD element is drawn. Position is stored relative to an anchor (the screen
 * edge/center nearest the box when it was dropped) rather than as raw top-left pixels, so a box
 * placed in the bottom-right corner stays in that corner when the window or GUI scale changes.
 */
public final class HudLayout {
	public enum AnchorX { LEFT, CENTER, RIGHT }

	public enum AnchorY { TOP, MIDDLE, BOTTOM }

	public AnchorX anchorX = AnchorX.LEFT;
	public AnchorY anchorY = AnchorY.TOP;
	/** Offset of the box's anchored edge from the matching screen edge, in GUI pixels. */
	public int offsetX = 8;
	public int offsetY = 8;
	public float scale = 1.0f;
	/** Draw the rounded panel behind the content (off = floating text only). */
	public boolean background = true;
	/** Player color overrides keyed by the element's color-slot id; missing entries follow the theme. */
	public Map<String, Integer> colors = new LinkedHashMap<>();

	public HudLayout() {
	}

	public static HudLayout at(AnchorX ax, AnchorY ay, int x, int y) {
		HudLayout l = new HudLayout();
		l.anchorX = ax;
		l.anchorY = ay;
		l.offsetX = x;
		l.offsetY = y;
		return l;
	}

	/** Top-left corner for a box of the given (already scaled) size on a screen of the given size. */
	public int resolveX(int screenWidth, int boxWidth) {
		int x = switch (anchorX) {
			case LEFT -> offsetX;
			case CENTER -> screenWidth / 2 - boxWidth / 2 + offsetX;
			case RIGHT -> screenWidth - boxWidth - offsetX;
		};
		return clamp(x, 0, Math.max(0, screenWidth - boxWidth));
	}

	public int resolveY(int screenHeight, int boxHeight) {
		int y = switch (anchorY) {
			case TOP -> offsetY;
			case MIDDLE -> screenHeight / 2 - boxHeight / 2 + offsetY;
			case BOTTOM -> screenHeight - boxHeight - offsetY;
		};
		return clamp(y, 0, Math.max(0, screenHeight - boxHeight));
	}

	/** Re-anchors to whichever third of the screen the box now sits in, keeping it visually in place. */
	public void placeAt(int x, int y, int boxWidth, int boxHeight, int screenWidth, int screenHeight) {
		int cx = x + boxWidth / 2;
		int cy = y + boxHeight / 2;
		anchorX = cx < screenWidth / 3 ? AnchorX.LEFT : cx > screenWidth * 2 / 3 ? AnchorX.RIGHT : AnchorX.CENTER;
		anchorY = cy < screenHeight / 3 ? AnchorY.TOP : cy > screenHeight * 2 / 3 ? AnchorY.BOTTOM : AnchorY.MIDDLE;
		offsetX = switch (anchorX) {
			case LEFT -> x;
			case CENTER -> x - (screenWidth / 2 - boxWidth / 2);
			case RIGHT -> screenWidth - boxWidth - x;
		};
		offsetY = switch (anchorY) {
			case TOP -> y;
			case MIDDLE -> y - (screenHeight / 2 - boxHeight / 2);
			case BOTTOM -> screenHeight - boxHeight - y;
		};
	}

	public HudLayout copy() {
		HudLayout l = at(anchorX, anchorY, offsetX, offsetY);
		l.scale = scale;
		l.background = background;
		l.colors = new LinkedHashMap<>(colors);
		return l;
	}

	private static int clamp(int v, int min, int max) {
		return Math.max(min, Math.min(max, v));
	}
}
