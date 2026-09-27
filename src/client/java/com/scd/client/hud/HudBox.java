package com.scd.client.hud;

import com.scd.client.ui.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What a HUD element wants drawn this frame, as a list of rows. The manager measures it once
 * (every row knows its own size), sizes the panel to fit and then renders - so there is exactly
 * one layout pass and the measured and drawn geometry can never disagree.
 * Colors are either a {@link HudColor} role (themeable, player-overridable) or a fixed ARGB value
 * for semantic colors like warnings and health bars.
 */
public final class HudBox {
	public static final int PADDING = 7;
	private static final int LINE = 10;

	/** Resolves a role to a concrete color for the HUD being drawn. */
	public interface Palette {
		int color(HudColor role);
	}

	public interface Painter {
		void paint(GuiGraphicsExtractor g, int x, int y, int width, int height);
	}

	private sealed interface Row permits Text, Bar, Divider, Space, Hero, Grid, Item, Custom {
		int height();

		int width();
	}

	private record Text(String text, HudColor role, Integer fixed, boolean caps) implements Row {
		public int height() {
			return LINE;
		}

		public int width() {
			String s = caps ? text.toUpperCase(Locale.ROOT) : text;
			return caps || role == HudColor.TITLE ? Ui.widthBold(s) : Ui.width(s);
		}
	}

	private record Bar(float frac, int color) implements Row {
		public int height() {
			return 6;
		}

		public int width() {
			return 0;
		}
	}

	private record Divider() implements Row {
		public int height() {
			return 5;
		}

		public int width() {
			return 0;
		}
	}

	private record Space(int height) implements Row {
		public int width() {
			return 0;
		}
	}

	private record Hero(String label, String value) implements Row {
		public int height() {
			return LINE + 19;
		}

		public int width() {
			return Math.max(Ui.widthBold(label.toUpperCase(Locale.ROOT)), Ui.widthTitle(value));
		}
	}

	private record Grid(List<String[]> cells) implements Row {
		public int height() {
			return ((cells.size() + 1) / 2) * (LINE * 2 + 3);
		}

		public int width() {
			int col = 0;
			for (String[] c : cells) col = Math.max(col, Math.max(Ui.width(c[0].toUpperCase(Locale.ROOT)), Ui.width(c[1])));
			return col * 2 + 12;
		}
	}

	private record Item(ItemStack stack, String text, HudColor role, Integer fixed) implements Row {
		public int height() {
			return 18;
		}

		public int width() {
			return 20 + Ui.width(text);
		}
	}

	private record Custom(int width, int height, Painter painter) implements Row {
	}

	private final List<Row> rows = new ArrayList<>();
	private int minWidth = 110;

	public HudBox minWidth(int width) {
		this.minWidth = width;
		return this;
	}

	public HudBox title(String text) {
		rows.add(new Text(text, HudColor.TITLE, null, false));
		return this;
	}

	public HudBox text(String text) {
		rows.add(new Text(text, HudColor.TEXT, null, false));
		return this;
	}

	public HudBox text(String text, HudColor role) {
		rows.add(new Text(text, role, null, false));
		return this;
	}

	/** Fixed-color line (warnings, alerts) that ignores theme and overrides. */
	public HudBox colored(String text, int argb) {
		rows.add(new Text(text, null, argb, false));
		return this;
	}

	public HudBox label(String text) {
		rows.add(new Text(text, HudColor.LABEL, null, true));
		return this;
	}

	public HudBox bar(float frac, int argb) {
		rows.add(new Bar(frac, argb));
		return this;
	}

	public HudBox divider() {
		if (!rows.isEmpty() && !(rows.get(rows.size() - 1) instanceof Divider)) rows.add(new Divider());
		return this;
	}

	public HudBox space(int height) {
		rows.add(new Space(height));
		return this;
	}

	/** Small label over one big (2x) number - the single most important stat. */
	public HudBox hero(String label, String value) {
		rows.add(new Hero(label, value));
		return this;
	}

	/** Two-column label/value grid; pairs are {label, value}. */
	public HudBox grid(List<String[]> pairs) {
		if (!pairs.isEmpty()) rows.add(new Grid(List.copyOf(pairs)));
		return this;
	}

	public HudBox item(ItemStack stack, String text, HudColor role) {
		rows.add(new Item(stack, text, role, null));
		return this;
	}

	public HudBox item(ItemStack stack, String text, int argb) {
		rows.add(new Item(stack, text, null, argb));
		return this;
	}

	public HudBox custom(int width, int height, Painter painter) {
		rows.add(new Custom(width, height, painter));
		return this;
	}

	public boolean isEmpty() {
		return rows.isEmpty();
	}

	public int contentWidth() {
		int w = minWidth - PADDING * 2;
		for (Row r : rows) w = Math.max(w, r.width());
		return w;
	}

	public int contentHeight() {
		int h = 0;
		for (Row r : rows) h += r.height() + 2;
		return Math.max(0, h - 2);
	}

	public int width() {
		return contentWidth() + PADDING * 2;
	}

	public int height() {
		return contentHeight() + PADDING * 2;
	}

	/** Draws the rows at local (0,0); the caller has already applied translate/scale and the panel. */
	public void render(GuiGraphicsExtractor g, Palette palette) {
		int x = PADDING;
		int y = PADDING;
		int w = contentWidth();
		for (Row row : rows) {
			switch (row) {
				case Text t -> {
					int color = t.fixed() != null ? t.fixed() : palette.color(t.role());
					String str = t.caps() ? t.text().toUpperCase(Locale.ROOT) : t.text();
					if (t.caps() || t.role() == HudColor.TITLE) Ui.bold(g, str, x, y + 1, color);
					else Ui.text(g, str, x, y + 1, color);
				}
				case Bar b -> Ui.bar(g, x, y, w, 5, b.frac(), b.color());
				case Divider d -> g.fill(x, y + 2, x + w, y + 3, 0x28FFFFFF);
				case Space s -> {
				}
				case Hero h -> {
					Ui.section(g, h.label(), x, y + 1);
					Ui.title(g, h.value(), x, y + LINE + 4, palette.color(HudColor.VALUE));
				}
				case Grid gr -> {
					int colW = (w - 12) / 2;
					int gy = y;
					for (int i = 0; i < gr.cells().size(); i++) {
						String[] c = gr.cells().get(i);
						int cx = i % 2 == 0 ? x : x + colW + 12;
						Ui.bold(g, c[0].toUpperCase(Locale.ROOT), cx, gy + 1, palette.color(HudColor.LABEL));
						Ui.text(g, c[1], cx, gy + LINE + 2, palette.color(HudColor.VALUE));
						if (i % 2 == 1) gy += LINE * 2 + 3;
					}
				}
				case Item it -> {
					if (!it.stack().isEmpty()) g.item(it.stack(), x, y + 1);
					int color = it.fixed() != null ? it.fixed() : palette.color(it.role());
					Ui.text(g, it.text(), x + 20, y + 5, color);
				}
				case Custom c -> c.painter().paint(g, x, y, w, c.height());
			}
			y += row.height() + 2;
		}
	}
}
