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
		Ui.bold(g, getMessage().getString(), x + 11, y + 6, Ui.theme().textPrimary());
		Ui.text(g, Ui.ellipsize(description, w - 34), x + 11, y + 18, Ui.theme().textMuted());
		Ui.rightAligned(g, "›", x + w - 10, y + h / 2 - 4, hovered ? Ui.theme().accent() : Ui.theme().textMuted());
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
			Ui.bold(g, getMessage().getString(), x + 11, ty, Ui.theme().textPrimary());
			int right = x + w - 10;
			Ui.rightAligned(g, open ? "▾" : "▸", right, ty, hovered ? Ui.theme().accent() : Ui.theme().textMuted());
			String s = status != null ? status.get() : null;
			if (s != null && !s.isEmpty()) {
				int sw = Ui.width(s) + 12;
				int sx = right - 14 - sw;
				Ui.rect(g, sx, y + 5, sw, h - 10, (h - 10) / 2f, Ui.theme().accentOver(Ui.theme().card(), 0.2f));
				Ui.centered(g, s, sx + sw / 2, ty, Ui.blend(Ui.theme().accent(), 0xFFFFFFFF, 0.3f));
			}
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			defaultButtonNarrationText(output);
		}
	}
}
