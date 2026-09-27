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

/** Slim rounded track with a knob; the value label sits to the left of the track. Click or drag. */
public class FlatSlider extends AbstractWidget {
	private static final int LABEL_W = 44;

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

	private int trackX() {
		return getX() + LABEL_W;
	}

	private int trackW() {
		return getWidth() - LABEL_W - 4;
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
		double frac = Math.max(0, Math.min(1, (mouseX - trackX()) / trackW()));
		double v = min + Math.round((frac * (max - min)) / step) * step;
		v = Math.max(min, Math.min(max, v));
		if (v != getter.getAsDouble()) setter.accept(v);
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		Theme t = Ui.theme();
		double value = getter.getAsDouble();
		float frac = (float) ((value - min) / (max - min));
		int cy = getY() + getHeight() / 2;
		Ui.rightAligned(g, formatter.apply(value), trackX() - 8, cy - Ui.lineHeight() / 2 + 1, t.textPrimary());
		int tx = trackX(), tw = trackW();
		Ui.rect(g, tx, cy - 2, tw, 4, 2, t.trackOff());
		if (frac > 0) Ui.rect(g, tx, cy - 2, Math.max(4, tw * frac), 4, 2, t.accent());
		float knob = 10;
		float kx = tx + tw * frac - knob / 2;
		Ui.rect(g, kx, cy - knob / 2, knob, knob, knob / 2, isHoveredOrFocused() ? 0xFFFFFFFF : Ui.blend(t.textPrimary(), t.accent(), 0.15f));
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
