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

/** Flat gradient button with a theme-accent outline. Reads the theme every frame, so it never goes stale. */
public class FlatButton extends AbstractWidget {
	private final Runnable onPress;
	private boolean danger;

	public FlatButton(int x, int y, int width, int height, String label, Runnable onPress) {
		super(x, y, width, height, Component.literal(label));
		this.onPress = onPress;
	}

	public FlatButton danger() {
		this.danger = true;
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
		boolean hovered = isActive() && isHoveredOrFocused();
		int top = !isActive() ? t.disabledTop() : hovered ? t.cardHoverTop() : t.cardTop();
		int bottom = !isActive() ? t.disabledBottom() : hovered ? t.cardHoverBottom() : t.cardBottom();
		g.fillGradient(x, y, x + w, y + h, top, bottom);
		int edge = !isActive() ? t.border() : danger ? Ui.DANGER : t.accent();
		g.outline(x, y, w, h, edge);
		String label = Ui.ellipsize(getMessage().getString(), w - 6);
		Ui.centered(g, label, x + w / 2, y + (h - Ui.lineHeight()) / 2 + 1, isActive() ? t.textPrimary() : t.textMuted());
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
