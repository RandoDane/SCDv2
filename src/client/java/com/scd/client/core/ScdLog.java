package com.scd.client.core;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/**
 * Central "SCD"-tagged logger, plus {@link #guard}: every per-tick/per-frame callback SCD registers
 * runs through it so one bug (ours, or a bad interaction with another mod on the same hook) is
 * logged with context instead of breaking rendering or other mods' listeners. The first failure per
 * context in a 10s window is also surfaced in chat, since "paste your latest.log" is real friction.
 */
public final class ScdLog {
	private static final Logger LOGGER = LoggerFactory.getLogger("SCD");
	private static final long CHAT_ALERT_COOLDOWN_MS = 10_000;
	private static final Map<String, Long> LAST_ALERT = new ConcurrentHashMap<>();
	private static volatile boolean debug;
	/** Receives every SCD log line (level, message) - the session recorder subscribes while recording. */
	private static volatile java.util.function.BiConsumer<String, String> tap;

	public static void setTap(java.util.function.BiConsumer<String, String> newTap) {
		tap = newTap;
	}

	private static void tap(String level, String message) {
		var t = tap;
		if (t != null) {
			try {
				t.accept(level, message);
			} catch (RuntimeException ignored) {
				// never let the recorder break logging
			}
		}
	}

	private ScdLog() {
	}

	public static void setDebug(boolean enabled) {
		debug = enabled;
	}

	public static void info(String message) {
		LOGGER.info(message);
		tap("info", message);
	}

	/** Only logged while developer mode is on - for high-volume diagnostics (every drop decision, etc). */
	public static void debug(String message) {
		if (debug) LOGGER.info("[debug] " + message);
		tap("debug", message);
	}

	public static void warn(String message) {
		LOGGER.warn(message);
		tap("warn", message);
	}

	public static void warn(String message, Throwable t) {
		LOGGER.warn(message, t);
		tap("warn", message + " :: " + t);
	}

	public static void error(String message, Throwable t) {
		LOGGER.error(message, t);
		tap("error", message + " :: " + t);
	}

	public static void guard(String context, Runnable action) {
		try {
			action.run();
		} catch (Throwable t) {
			report(context, t);
		}
	}

	public static boolean guard(String context, BooleanSupplier action, boolean fallback) {
		try {
			return action.getAsBoolean();
		} catch (Throwable t) {
			report(context, t);
			return fallback;
		}
	}

	private static void report(String context, Throwable t) {
		LOGGER.error("[{}] threw", context, t);
		StringBuilder trace = new StringBuilder(context + " threw " + t);
		for (StackTraceElement el : t.getStackTrace()) {
			if (trace.length() > 1500) break;
			trace.append("\n  at ").append(el);
		}
		tap("error", trace.toString());
		long now = System.currentTimeMillis();
		Long last = LAST_ALERT.put(context, now);
		if (last != null && now - last < CHAT_ALERT_COOLDOWN_MS) return;
		var player = Minecraft.getInstance().player;
		if (player == null) return;
		String summary = t.getClass().getSimpleName() + (t.getMessage() != null ? ": " + t.getMessage() : "");
		player.sendSystemMessage(Component.literal("[SCD] " + context + " failed: " + summary + " (details in logs/latest.log)")
				.withStyle(ChatFormatting.RED));
	}
}
