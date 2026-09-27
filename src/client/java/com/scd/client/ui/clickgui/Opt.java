package com.scd.client.ui.clickgui;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

/** One line of options under an expanded module (or in the side panel). */
public sealed interface Opt {
	String label();

	record Toggle(String label, BooleanSupplier get, Consumer<Boolean> set) implements Opt {
	}

	record Slider(String label, double min, double max, double step, DoubleSupplier get, Consumer<Double> set, DoubleFunction<String> fmt) implements Opt {
	}

	/** Click for the next value, right-click for the previous one. */
	record Cycle(String label, List<String> values, Supplier<String> get, Consumer<String> set, Function<String, String> fmt) implements Opt {
	}

	record Action(String label, Runnable run) implements Opt {
	}

	record Info(String label, Supplier<String> value) implements Opt {
	}

	/** Inline text: click to edit, Enter to save, Esc to cancel; Tab completes when {@code complete} is set. */
	record Text(String label, Supplier<String> get, Consumer<String> set, Function<String, List<String>> complete) implements Opt {
		public Text(String label, Supplier<String> get, Consumer<String> set) {
			this(label, get, set, null);
		}
	}

	/** Colour from a palette (click forward, right-click back); null = follow the theme. */
	record Color(String label, Supplier<Integer> get, Consumer<Integer> set) implements Opt {
	}

	/** A row split into equal click zones ("+1", "-1", "Finish"...), with a caption on the left. */
	record Buttons(String label, List<String> names, List<Runnable> actions) implements Opt {
	}

	/** Pick one of several: the chosen chip is filled with the accent colour. */
	record Chips(String label, List<String> names, Supplier<String> selected, Consumer<String> pick) implements Opt {
	}

	/** A progress bar with a value on the right. */
	record Progress(String label, DoubleSupplier fraction, Supplier<String> value) implements Opt {
	}

	/** A coloured one-line message (form feedback); hidden when empty. */
	record Note(Supplier<String> text, int color) implements Opt {
		@Override
		public String label() {
			return "";
		}
	}

	/** A button that opens a scrollable list; picking an entry closes it. */
	record Dropdown(String label, Supplier<List<String>> items, Consumer<String> pick) implements Opt {
	}
}
