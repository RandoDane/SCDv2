package com.scd.client.core;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;

/**
 * Everything SCD shows the player or sends to the server goes through here, so client-local lines
 * are consistently prefixed and the (rare) outgoing messages are easy to audit: {@link #party} and
 * {@link #command} are the only methods that actually send anything to Hypixel.
 */
public final class Chat {
	private static final Component PREFIX = Component.literal("[SCD] ").withStyle(ChatFormatting.DARK_AQUA);

	private Chat() {
	}

	/** Client-local chat line with the [SCD] prefix - never sent to the server. */
	public static void info(Component message) {
		var player = Minecraft.getInstance().player;
		ScdLog.debug("[scd-chat] " + message.getString());
		if (player != null) player.sendSystemMessage(PREFIX.copy().append(message));
	}

	public static void info(String message) {
		info(Component.literal(message).withStyle(ChatFormatting.GRAY));
	}

	public static void success(String message) {
		info(Component.literal(message).withStyle(ChatFormatting.GREEN));
	}

	public static void error(String message) {
		info(Component.literal(message).withStyle(ChatFormatting.RED));
	}

	/** Unprefixed client-local line, for multi-line reports that already have their own header. */
	public static void raw(Component message) {
		ScdLog.debug("[scd-chat] " + message.getString());
		var player = Minecraft.getInstance().player;
		if (player != null) player.sendSystemMessage(message);
	}

	/** Sends "/pc text" exactly as if typed - a real message other party members see. */
	public static void party(String text) {
		command("pc " + text);
	}

	/** Runs a server command (no leading slash) through the normal signed-command path. */
	public static void command(String commandWithoutSlash) {
		ScdLog.debug("[scd-sent] /" + commandWithoutSlash);
		var player = Minecraft.getInstance().player;
		if (player != null && player.connection != null) player.connection.sendCommand(commandWithoutSlash);
	}

	/** Clickable "[label]" chat segment: runs the command on click, or only fills the chat box when {@code run} is false. */
	public static MutableComponent button(String label, String command, boolean run, String hover) {
		ClickEvent click = run ? new ClickEvent.RunCommand(command) : new ClickEvent.SuggestCommand(command);
		Style style = Style.EMPTY.withColor(ChatFormatting.YELLOW).withUnderlined(true)
				.withClickEvent(click)
				.withHoverEvent(new HoverEvent.ShowText(Component.literal(hover)));
		return Component.literal("[" + label + "]").setStyle(style);
	}

	/** On-screen title (+ optional subtitle) with a ping - for alerts that must not be missed mid-fight. */
	public static void title(Component title, Component subtitle, boolean sound) {
		ScdLog.debug("[scd-title] " + title.getString() + (subtitle != null ? " / " + subtitle.getString() : ""));
		var mc = Minecraft.getInstance();
		mc.gui.hud.resetTitleTimes();
		mc.gui.hud.setTitle(title);
		if (subtitle != null) mc.gui.hud.setSubtitle(subtitle);
		if (sound) ping(1.5f);
	}

	public static void ping(float pitch) {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING, pitch));
	}
}
