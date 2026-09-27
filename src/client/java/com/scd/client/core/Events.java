package com.scd.client.core;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Every event type published on the {@link EventBus}. */
public final class Events {
	private Events() {
	}

	/** End of every client tick, after {@code GameState} has refreshed its sidebar/tab-list snapshot. */
	public record Tick(long tick) {
	}

	/**
	 * A chat line straight from the network, captured at the head of the packet handler - before
	 * Fabric's message events and before any other mod rewrites or suppresses it. {@code text} is
	 * {@code component.getString()}, {@code clean} additionally has formatting/icons stripped.
	 */
	public record ChatReceived(Component component, String text, String clean, Channel channel) {
		public enum Channel { SYSTEM, ACTION_BAR, PLAYER, DISGUISED }

		public boolean isSystem() {
			return channel == Channel.SYSTEM;
		}
	}

	/** Fires once when a screen is initialized, and on every page Hypixel sends for the same menu. */
	public record ScreenOpened(Screen screen) {
	}

	/** Fires every tick while a container screen stays open - Hypixel streams item stacks in after opening. */
	public record ScreenTick(Screen screen) {
	}

	/** Player joined/left a world or server - the moment to drop world-scoped state. */
	public record WorldChanged(boolean joined) {
	}

	/** SkyBlock area line on the sidebar changed (e.g. "Void Sepulture" -> "Dragon's Nest"). */
	public record AreaChanged(String previous, String current) {
	}

	/** The server reported a living entity's death (entity event 3), before it is removed. */
	public record EntityDied(LivingEntity entity) {
	}

	/** The local player picked up an item entity that was at {@code pos}. */
	public record ItemPickedUp(Vec3 pos, ItemStack stack) {
	}
}
