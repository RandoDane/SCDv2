package com.scd.client.ui.widget;

import com.scd.client.ui.Theme;
import com.scd.client.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Pill on/off switch bound to a getter/setter pair, so it always shows the live value. */
public class ToggleSwitch extends AbstractWidget {
	public static final int WIDTH = 26;
	public static final int HEIGHT = 12;

	private final BooleanSupplier getter;
	private final Consumer<Boolean> setter;

	public ToggleSwitch(int x, int y, String label, BooleanSupplier getter, Consumer<Boolean> setter) {
		super(x, y, WIDTH, HEIGHT, Component.literal(label));
		this.getter = getter;
		this.setter = setter;
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		playDownSound(Minecraft.getInstance().getSoundManager());
		setter.accept(!getter.getAsBoolean());
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		Theme t = Ui.theme();
		boolean on = getter.getAsBoolean();
		int x = getX(), y = getY(), w = getWidth(), h = getHeight();
		g.fill(x, y, x + w, y + h, on ? t.accent() : t.trackOff());
		g.outline(x, y, w, h, isHoveredOrFocused() ? t.textSecondary() : t.border());
		int knob = h - 4;
		int kx = on ? x + w - knob - 2 : x + 2;
		g.fill(kx, y + 2, kx + knob, y + 2 + knob, t.textPrimary());
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, Component.literal(getMessage().getString() + ": " + (getter.getAsBoolean() ? "on" : "off")));
	}
}
