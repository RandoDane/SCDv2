package com.scd.client.ui.widget;

import com.scd.client.ui.Theme;
import com.scd.client.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** "‹ value ›" selector: left half steps back, right half forward; the mouse wheel also cycles. */
public class CyclePicker<T> extends AbstractWidget {
	private final List<T> values;
	private final Supplier<T> getter;
	private final Consumer<T> setter;
	private final Function<T, String> label;

	public CyclePicker(int x, int y, int w, int h, List<T> values, Supplier<T> getter, Consumer<T> setter, Function<T, String> label) {
		super(x, y, w, h, Component.empty());
		this.values = values;
		this.getter = getter;
		this.setter = setter;
		this.label = label;
	}

	private void step(int delta) {
		int i = values.indexOf(getter.get());
		setter.accept(values.get(Math.floorMod((i < 0 ? 0 : i) + delta, values.size())));
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		playDownSound(Minecraft.getInstance().getSoundManager());
		step(event.x() < getX() + getWidth() / 2.0 ? -1 : 1);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (!isMouseOver(mouseX, mouseY) || scrollY == 0) return false;
		step(scrollY > 0 ? -1 : 1);
		return true;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		Theme t = Ui.theme();
		int x = getX(), y = getY(), w = getWidth(), h = getHeight();
		boolean hot = isHoveredOrFocused();
		Ui.rect(g, x, y, w, h, Ui.RADIUS_SMALL, hot ? t.cardHover() : t.card(), hot ? Ui.blend(t.border(), t.accent(), 0.45f) : t.border());
		int ty = y + (h - Ui.lineHeight()) / 2 + 1;
		boolean left = hot && mouseX < x + w / 2;
		Ui.text(g, "‹", x + 6, ty, left ? t.accent() : t.textMuted());
		Ui.rightAligned(g, "›", x + w - 6, ty, hot && !left ? t.accent() : t.textMuted());
		Ui.centered(g, Ui.ellipsize(label.apply(getter.get()), w - 26), x + w / 2, ty, t.textPrimary());
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
