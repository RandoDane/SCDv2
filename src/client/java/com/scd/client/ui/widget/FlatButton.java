package com.scd.client.ui.widget;

import com.scd.client.ui.Theme;
import com.scd.client.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Rounded button. SECONDARY (card surface) is the default; PRIMARY fills with the accent for the one
 * main action on a surface; DANGER tints red for destructive actions; GHOST has no surface until hovered.
 */
public class FlatButton extends AbstractWidget {
	public enum Style { SECONDARY, PRIMARY, DANGER, GHOST }

	private final Runnable onPress;
	private Style style = Style.SECONDARY;

	public FlatButton(int x, int y, int width, int height, String label, Runnable onPress) {
		super(x, y, width, height, Component.literal(label));
		this.onPress = onPress;
	}

	public FlatButton primary() {
		style = Style.PRIMARY;
		return this;
	}

	public FlatButton danger() {
		style = Style.DANGER;
		return this;
	}

	public FlatButton ghost() {
		style = Style.GHOST;
		return this;
	}

	public FlatButton tooltip(String text) {
		setTooltip(Tooltip.create(Component.literal(text)));
		return this;
	}

	public FlatButton enabled(boolean enabled) {
		this.active = enabled;
		return this;
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		if (!isActive()) return;
		playDownSound(Minecraft.getInstance().getSoundManager());
		onPress.run();
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		Theme t = Ui.theme();
		int x = getX(), y = getY(), w = getWidth(), h = getHeight();
		boolean hot = isActive() && isHoveredOrFocused();
		int fill, border, text;
		if (!isActive()) {
			fill = t.card();
			border = t.border();
			text = t.textMuted();
		} else {
			switch (style) {
				case PRIMARY -> {
					fill = hot ? Ui.blend(t.accent(), 0xFFFFFFFF, 0.12f) : t.accent();
					border = fill;
					text = contrastText(fill);
				}
				case DANGER -> {
					fill = hot ? Ui.blend(t.card(), Ui.DANGER, 0.35f) : Ui.blend(t.card(), Ui.DANGER, 0.18f);
					border = Ui.blend(t.border(), Ui.DANGER, 0.5f);
					text = t.textPrimary();
				}
				case GHOST -> {
					fill = hot ? t.cardHover() : 0;
					border = hot ? t.border() : 0;
					text = hot ? t.textPrimary() : t.textSecondary();
				}
				default -> {
					fill = hot ? t.cardHover() : t.card();
					border = hot ? Ui.blend(t.border(), t.accent(), 0.45f) : t.border();
					text = t.textPrimary();
				}
			}
		}
		if (fill != 0) Ui.rect(g, x, y, w, h, Ui.RADIUS_SMALL, fill, border);
		String label = Ui.ellipsize(getMessage().getString(), w - 8);
		Ui.centered(g, label, x + w / 2, y + (h - Ui.lineHeight()) / 2 + 1, text);
	}

	/** Dark text on light accents (Monochrome white, Arctic cyan), white on dark ones. */
	public static int contrastTextFor(int bg) {
		return contrastText(bg);
	}

	static int contrastText(int bg) {
		int r = (bg >> 16) & 0xFF, gg = (bg >> 8) & 0xFF, b = bg & 0xFF;
		double lum = 0.2126 * r + 0.7152 * gg + 0.0722 * b;
		return lum > 160 ? 0xFF15161E : 0xFFFFFFFF;
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
