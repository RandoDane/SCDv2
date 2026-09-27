package com.scd.client.feature.world;

import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Thin wrappers over vanilla's Gizmos API for world-space highlights. A gizmo only lives for the
 * frame it was submitted in, so callers re-submit every frame while a target is tracked and simply
 * stop to make it disappear - no cleanup. Colors are ARGB; "through walls" disables depth testing.
 */
public final class WorldGizmos {
	private static final float LINE_WIDTH = 3f;
	/** Tracers start this far in front of the eye - a vertex at the camera clips against the near plane. */
	private static final double LINE_START_PUSH = 1.5;

	private WorldGizmos() {
	}

	public static void line(Vec3 from, Vec3 to, int argb, boolean throughWalls) {
		var props = Gizmos.line(from, to, argb, LINE_WIDTH);
		if (throughWalls) props.setAlwaysOnTop();
	}

	public static void tracer(Vec3 eye, Entity target, int argb, boolean throughWalls) {
		Vec3 to = target.position().add(0, target.getBbHeight() / 2, 0);
		Vec3 delta = to.subtract(eye);
		Vec3 from = delta.lengthSqr() > LINE_START_PUSH * LINE_START_PUSH ? eye.add(delta.normalize().scale(LINE_START_PUSH)) : eye;
		line(from, to, argb, throughWalls);
	}

	public static void box(Entity target, int argb, boolean throughWalls) {
		var props = Gizmos.cuboid(target.getBoundingBox(), GizmoStyle.stroke(argb, LINE_WIDTH));
		if (throughWalls) props.setAlwaysOnTop();
	}

	public static void label(Vec3 pos, String text, int argb, boolean throughWalls) {
		var props = Gizmos.billboardText(text, pos, TextGizmo.Style.forColorAndCentered(argb));
		if (throughWalls) props.setAlwaysOnTop();
	}
}
