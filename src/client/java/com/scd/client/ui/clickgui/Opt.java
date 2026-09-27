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
}
