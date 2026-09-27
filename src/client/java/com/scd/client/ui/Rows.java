package com.scd.client.ui;

import com.scd.client.ui.widget.CyclePicker;
import com.scd.client.ui.widget.FlatButton;
import com.scd.client.ui.widget.FlatSlider;
import com.scd.client.ui.widget.TextField;
import com.scd.client.ui.widget.ToggleSwitch;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Declarative row builder for {@link ScdScreen}. Each call appends one row; the builder tracks the
 * running height, so screens describe <i>what</i> they show and never compute y offsets by hand.
 */
public final class Rows {
	/** Draws the non-widget parts of a row at its current (scrolled) top-left. */
	public interface Painter {
		void paint(GuiGraphicsExtractor g, int x, int y, int width, int mouseX, int mouseY);
	}

	/** One laid-out row: its widgets keep their offset from the row's top. */
	public static final class Row {
		final int top;
		final int height;
		final Painter painter;
		final List<AbstractWidget> widgets = new ArrayList<>();
		final List<Integer> widgetOffsets = new ArrayList<>();

		Row(int top, int height, Painter painter) {
			this.top = top;
			this.height = height;
			this.painter = painter;
		}
	}

	public static final int ROW = 20;
	private static final int GAP = 4;

	final List<Row> rows = new ArrayList<>();
	private final int x;
	private final int width;
	private final Set<String> expanded;
	private final Runnable rebuild;
	private int y;
	private int indent;

	Rows(int x, int width, Set<String> expanded, Runnable rebuild) {
		this.x = x;
		this.width = width;
		this.expanded = expanded;
		this.rebuild = rebuild;
	}

	public int width() {
		return width - indent;
	}

	public int x() {
		return x + indent;
	}

	int height() {
		return y;
	}

	private Row add(int height, Painter painter) {
		Row row = new Row(y, height, painter);
		rows.add(row);
		y += height + GAP;
		return row;
	}

	private <W extends AbstractWidget> W widget(Row row, W widget) {
		row.widgets.add(widget);
		row.widgetOffsets.add(widget.getY());
		return widget;
	}

	public void space(int height) {
		y += height;
	}

	public void header(String text) {
		if (!rows.isEmpty()) y += 4;
		int ix = x();
		int w = width();
		add(12, (g, rx, ry, rw, mx, my) -> {
			Ui.section(g, text, ix, ry + 2);
			Ui.divider(g, ix + Ui.width(text.toUpperCase()) + 6, ry + 6, w - Ui.width(text.toUpperCase()) - 6);
		});
	}

	/** Multi-line muted paragraph, word-wrapped to the row width. */
	public void note(String text) {
		int ix = x();
		var lines = Ui.font().split(net.minecraft.network.chat.Component.literal(text), width());
		int h = lines.size() * (Ui.lineHeight() + 1);
		add(h, (g, rx, ry, rw, mx, my) -> {
			int ly = ry;
			for (var line : lines) {
				g.text(Ui.font(), line, ix, ly, Ui.theme().textMuted(), true);
				ly += Ui.lineHeight() + 1;
			}
		});
	}

	/** Label on the left, live value (re-read every frame) on the right. */
	public void value(String label, Supplier<String> value) {
		int ix = x();
		int w = width();
		add(12, (g, rx, ry, rw, mx, my) -> {
			Ui.text(g, label, ix, ry + 2, Ui.theme().textSecondary());
			Ui.rightAligned(g, value.get(), ix + w, ry + 2, Ui.theme().textPrimary());
		});
	}

	/** Free text line whose content and color are computed every frame. */
	public void line(Supplier<String> text, Supplier<Integer> color) {
		int ix = x();
		int w = width();
		add(11, (g, rx, ry, rw, mx, my) -> Ui.text(g, Ui.ellipsize(text.get(), w), ix, ry + 1, color.get()));
	}

	public void toggle(String label, String description, BooleanSupplier getter, Consumer<Boolean> setter) {
		int ix = x();
		int w = width();
		int height = description != null ? 22 : 14;
		Row row = add(height, (g, rx, ry, rw, mx, my) -> {
			Ui.text(g, label, ix, ry + 2, Ui.theme().textSecondary());
			if (description != null) Ui.text(g, Ui.ellipsize(description, w - ToggleSwitch.WIDTH - 8), ix, ry + 12, Ui.theme().textMuted());
		});
		ToggleSwitch sw = new ToggleSwitch(ix + w - ToggleSwitch.WIDTH, 1, label, getter, setter);
		widget(row, sw);
	}

	public FlatButton button(String label, Runnable action) {
		Row row = add(16, null);
		return widget(row, new FlatButton(x(), 0, width(), 16, label, action));
	}

	/** Several equal-width buttons on one row. Labels and actions are paired by index. */
	public List<FlatButton> buttons(List<String> labels, List<Runnable> actions) {
		Row row = add(16, null);
		int n = labels.size();
		int gap = 6;
		int bw = (width() - gap * (n - 1)) / n;
		List<FlatButton> out = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			out.add(widget(row, new FlatButton(x() + i * (bw + gap), 0, bw, 16, labels.get(i), actions.get(i))));
		}
		return out;
	}

	public <T> void cycle(String label, List<T> values, Supplier<T> getter, Consumer<T> setter, Function<T, String> fmt) {
		int ix = x();
		int pickerW = Math.min(140, width() / 2);
		Row row = add(16, (g, rx, ry, rw, mx, my) -> Ui.text(g, label, ix, ry + 4, Ui.theme().textSecondary()));
		widget(row, new CyclePicker<>(ix + width() - pickerW, 0, pickerW, 16, values, getter, setter, fmt));
	}

	public void slider(String label, double min, double max, double step, DoubleSupplier getter, Consumer<Double> setter, DoubleFunction<String> fmt) {
		int ix = x();
		int sw = Math.min(140, width() / 2);
		Row row = add(14, (g, rx, ry, rw, mx, my) -> Ui.text(g, label, ix, ry + 3, Ui.theme().textSecondary()));
		widget(row, new FlatSlider(ix + width() - sw, 0, sw, 14, min, max, step, getter, setter, fmt));
	}

	/** Label above a full-width text field. */
	public TextField text(String label, String hint, String value, Predicate<Character> filter, Consumer<String> onChange) {
		int ix = x();
		Row row = add(label != null ? 28 : 16, label == null ? null
				: (g, rx, ry, rw, mx, my) -> Ui.section(g, label, ix, ry));
		return widget(row, new TextField(ix, label != null ? 12 : 0, width(), 16, hint, value, filter, onChange));
	}

	/** A clickable navigation card (title + description + chevron). */
	public void nav(String title, String description, Runnable open) {
		int ix = x();
		int w = width();
		Row row = add(30, null);
		widget(row, new NavCard(ix, 0, w, 30, title, description, open));
	}

	/**
	 * Collapsible section: a header card with an optional live status badge; {@code body} only adds
	 * its rows while expanded. Expansion state survives rebuilds via the screen's key set.
	 */
	public void section(String key, String title, Supplier<String> status, Consumer<Rows> body) {
		boolean open = expanded.contains(key);
		int ix = x();
		int w = width();
		Row row = add(18, null);
		widget(row, new NavCard.Expander(ix, 0, w, 18, title, status, open, () -> {
			if (!expanded.remove(key)) expanded.add(key);
			rebuild.run();
		}));
		if (open) {
			indent += 10;
			body.accept(this);
			indent -= 10;
			y += 2;
		}
	}

	/** Anything else: fixed height, custom painting, optional widgets positioned relative to the row. */
	public void custom(int height, Painter painter, List<? extends AbstractWidget> widgets) {
		Row row = add(height, painter);
		for (AbstractWidget w : widgets) widget(row, w);
	}
}
