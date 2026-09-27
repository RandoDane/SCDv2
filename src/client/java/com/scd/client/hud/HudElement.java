package com.scd.client.hud;

import com.scd.client.config.HudLayout;

/**
 * One movable overlay. Subclasses only decide <i>what</i> to show ({@link #build}); placement,
 * scaling, background, colors, editing and error isolation are the {@link HudManager}'s job.
 */
public abstract class HudElement {
	private final String id;
	private final String name;
	private final HudLayout defaults;

	protected HudElement(String id, String name, HudLayout defaults) {
		this.id = id;
		this.name = name;
		this.defaults = defaults;
	}

	public final String id() {
		return id;
	}

	public final String name() {
		return name;
	}

	public final HudLayout defaults() {
		return defaults;
	}

	/** The feature toggle for this HUD. Disabled HUDs are skipped entirely (and hidden in the editor). */
	public abstract boolean enabled();

	/**
	 * Content for this frame, or null when there is nothing to show. With {@code preview} the element
	 * must return representative sample content (used by the HUD editor and appearance screen).
	 */
	public abstract HudBox build(boolean preview);
}
