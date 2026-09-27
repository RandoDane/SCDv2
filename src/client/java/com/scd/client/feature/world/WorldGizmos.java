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
		tracer(eye, target, 1f, argb, throughWalls);
	}

	public static void tracer(Vec3 eye, Entity target, float partialTick, int argb, boolean throughWalls) {
		Vec3 to = target.getPosition(partialTick).add(0, target.getBbHeight() / 2, 0);
		Vec3 delta = to.subtract(eye);
		Vec3 from = delta.lengthSqr() > LINE_START_PUSH * LINE_START_PUSH ? eye.add(delta.normalize().scale(LINE_START_PUSH)) : eye;
		line(from, to, argb, throughWalls);
	}

	public static void box(Entity target, int argb, boolean throughWalls) {
		box(target, 1f, argb, throughWalls);
	}

	/** Box at the entity's interpolated (rendered) position, so it doesn't trail a moving mob. */
	public static void box(Entity target, float partialTick, int argb, boolean throughWalls) {
		var box = target.getBoundingBox().move(target.getPosition(partialTick).subtract(target.position()));
		var props = Gizmos.cuboid(box, GizmoStyle.stroke(argb, LINE_WIDTH));
		if (throughWalls) props.setAlwaysOnTop();
	}

	private static final java.util.List<java.util.function.Consumer<Float>> WORLD_DRAWERS = new java.util.concurrent.CopyOnWriteArrayList<>();

	/**
	 * Runs {@code draw} every frame with the frame's partial tick during world extraction (see
	 * LevelExtractorGizmoMixin) - independent of the HUD, so hiding it (F1) doesn't hide highlights.
	 */
	public static void onWorldExtract(java.util.function.Consumer<Float> draw) {
		WORLD_DRAWERS.add(draw);
	}

	public static void fireWorldExtract() {
		float partialTick = net.minecraft.client.Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
		for (var draw : WORLD_DRAWERS) {
			try {
				draw.accept(partialTick);
			} catch (Throwable t) {
				com.scd.client.core.ScdLog.report("world gizmos", t);
			}
		}
	}

	/** Outlined block with a faint fill. */
	public static void block(net.minecraft.core.BlockPos pos, int argb, boolean throughWalls) {
		var props = Gizmos.cuboid(pos, GizmoStyle.strokeAndFill(argb, 2f, (argb & 0x00FFFFFF) | 0x30000000));
		if (throughWalls) props.setAlwaysOnTop();
	}

	public static void label(Vec3 pos, String text, int argb, boolean throughWalls) {
		var props = Gizmos.billboardText(text, pos, TextGizmo.Style.forColorAndCentered(argb));
		if (throughWalls) props.setAlwaysOnTop();
	}
}
