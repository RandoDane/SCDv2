package com.scd.client.ui.widget;

import com.scd.client.ui.Theme;
import com.scd.client.ui.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;

/** Flat slider over [min, max] snapped to {@code step}; click or drag anywhere on the track. */
public class FlatSlider extends AbstractWidget {
	private final double min, max, step;
	private final DoubleSupplier getter;
	private final Consumer<Double> setter;
	private final DoubleFunction<String> formatter;

	public FlatSlider(int x, int y, int w, int h, double min, double max, double step,
			DoubleSupplier getter, Consumer<Double> setter, DoubleFunction<String> formatter) {
		super(x, y, w, h, Component.empty());
		this.min = min;
		this.max = max;
		this.step = step;
		this.getter = getter;
		this.setter = setter;
		this.formatter = formatter;
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		setFrom(event.x());
	}

	@Override
	protected void onDrag(MouseButtonEvent event, double dx, double dy) {
		setFrom(event.x());
	}

	private void setFrom(double mouseX) {
		double frac = Math.max(0, Math.min(1, (mouseX - getX()) / getWidth()));
		double v = min + Math.round((frac * (max - min)) / step) * step;
		v = Math.max(min, Math.min(max, v));
		if (v != getter.getAsDouble()) setter.accept(v);
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		Theme t = Ui.theme();
		int x = getX(), y = getY(), w = getWidth(), h = getHeight();
		double value = getter.getAsDouble();
		g.fill(x, y, x + w, y + h, t.trackOff());
		int filled = (int) Math.round(w * (value - min) / (max - min));
		if (filled > 0) g.fill(x, y, x + filled, y + h, t.accent());
		g.outline(x, y, w, h, isHoveredOrFocused() ? t.textSecondary() : t.border());
		Ui.centered(g, formatter.apply(value), x + w / 2, y + (h - Ui.lineHeight()) / 2 + 1, t.textPrimary());
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
