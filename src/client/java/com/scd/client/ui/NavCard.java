package com.scd.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

/** Two-line navigation card with an accent bar and chevron. */
class NavCard extends AbstractWidget {
	private final String description;
	private final Runnable onPress;

	NavCard(int x, int y, int w, int h, String title, String description, Runnable onPress) {
		super(x, y, w, h, Component.literal(title));
		this.description = description;
		this.onPress = onPress;
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		playDownSound(Minecraft.getInstance().getSoundManager());
		onPress.run();
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		int x = getX(), y = getY(), w = getWidth(), h = getHeight();
		boolean hovered = isHoveredOrFocused();
		Ui.card(g, x, y, w, h, hovered);
		Ui.text(g, getMessage().getString(), x + 10, y + 5, Ui.theme().textPrimary());
		Ui.text(g, Ui.ellipsize(description, w - 30), x + 10, y + 17, Ui.theme().textSecondary());
		Ui.rightAligned(g, "›", x + w - 8, y + h / 2 - 4, hovered ? Ui.theme().accent() : Ui.theme().textMuted());
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}

	/** Header row of a collapsible section. */
	static final class Expander extends AbstractWidget {
		private final Supplier<String> status;
		private final boolean open;
		private final Runnable onToggle;

		Expander(int x, int y, int w, int h, String title, Supplier<String> status, boolean open, Runnable onToggle) {
			super(x, y, w, h, Component.literal(title));
			this.status = status;
			this.open = open;
			this.onToggle = onToggle;
		}

		@Override
		public void onClick(MouseButtonEvent event, boolean doubleClick) {
			playDownSound(Minecraft.getInstance().getSoundManager());
			onToggle.run();
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
			int x = getX(), y = getY(), w = getWidth(), h = getHeight();
			boolean hovered = isHoveredOrFocused();
			Ui.card(g, x, y, w, h, hovered);
			int ty = y + (h - Ui.lineHeight()) / 2 + 1;
			Ui.text(g, getMessage().getString(), x + 10, ty, Ui.theme().textPrimary());
			int right = x + w - 8;
			Ui.rightAligned(g, open ? "▾" : "▸", right, ty, hovered ? Ui.theme().accent() : Ui.theme().textMuted());
			String s = status != null ? status.get() : null;
			if (s != null && !s.isEmpty()) Ui.rightAligned(g, s, right - 12, ty, Ui.theme().accent());
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			defaultButtonNarrationText(output);
		}
	}
}
