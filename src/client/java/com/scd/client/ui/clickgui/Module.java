package com.scd.client.ui.clickgui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * A row in a click-GUI column: left-click toggles it (when it has an on/off state), right-click
 * shows or hides its options underneath.
 */
public final class Module {
	final String name;
	final String description;
	final BooleanSupplier on;
	final Consumer<Boolean> set;
	final List<Opt> options = new ArrayList<>();

	public Module(String name, String description, BooleanSupplier on, Consumer<Boolean> set) {
		this.name = name;
		this.description = description;
		this.on = on;
		this.set = set;
	}

	/** A row that only holds options (no on/off of its own). */
	public static Module group(String name, String description) {
		return new Module(name, description, null, null);
	}

	public Module opt(Opt o) {
		options.add(o);
		return this;
	}

	public Module toggle(String label, BooleanSupplier get, Consumer<Boolean> set) {
		return opt(new Opt.Toggle(label, get, set));
	}
}
