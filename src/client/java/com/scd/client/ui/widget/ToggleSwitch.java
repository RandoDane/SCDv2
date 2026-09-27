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

/** Pill switch with a round knob, bound to a getter/setter so it always shows the live value. */
public class ToggleSwitch extends AbstractWidget {
	public static final int WIDTH = 24;
	public static final int HEIGHT = 13;

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
		int track = on ? t.accent() : (isHoveredOrFocused() ? Ui.blend(t.trackOff(), 0xFFFFFFFF, 0.08f) : t.trackOff());
		Ui.rect(g, x, y, w, h, h / 2f, track);
		float knob = h - 4;
		float kx = on ? x + w - knob - 2 : x + 2;
		Ui.rect(g, kx, y + 2, knob, knob, knob / 2f, on ? 0xFFFFFFFF : t.textSecondary());
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, Component.literal(getMessage().getString() + ": " + (getter.getAsBoolean() ? "on" : "off")));
	}
}
