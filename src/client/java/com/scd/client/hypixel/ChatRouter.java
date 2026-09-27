package com.scd.client.hypixel;

import com.scd.client.core.EventBus;
import com.scd.client.core.Events;
import com.scd.client.core.ScdLog;
import com.scd.logic.Text;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Entry point for every incoming chat packet, called from {@code ChatPacketMixin} at the HEAD of
 * the vanilla handlers - i.e. before Fabric's ClientReceiveMessageEvents and before other mods
 * (Odin, Skyblocker, ...) rewrite or suppress lines. The dungeon completion report in particular
 * never reaches the Fabric events on a typical Hypixel mod setup; this path sees Hypixel's raw text.
 *
 * Lines are dispatched on the client thread, so features never need to think about thread safety.
 */
public final class ChatRouter {
	private static volatile ChatRouter instance;
	/** When true, every line is also written to the log (/scd debug capture). */
	public static volatile boolean captureToLog;

	private final EventBus bus;

	public ChatRouter(EventBus bus) {
		this.bus = bus;
		instance = this;
	}

	/**
	 * Called by the mixin. Vanilla's handlers run twice per packet: first on the network thread,
	 * where ensureRunningOnSameThread re-queues the packet and aborts, then again on the client
	 * thread. A HEAD injection sees both, so only the client-thread call is forwarded - otherwise
	 * every line would be processed twice (1.x counted every dungeon death double this way).
	 */
	public static void onPacket(Component component, Events.ChatReceived.Channel channel) {
		ChatRouter router = instance;
		if (router == null || component == null || !Minecraft.getInstance().isSameThread()) return;
		ScdLog.guard("chat routing", () -> router.dispatch(component, channel));
	}

	private void dispatch(Component component, Events.ChatReceived.Channel channel) {
		String text = component.getString();
		if (text.isBlank()) return;
		if (captureToLog && channel != Events.ChatReceived.Channel.ACTION_BAR) {
			ScdLog.info("[capture:" + channel + "] " + text);
		}
		bus.post(new Events.ChatReceived(component, text, Text.clean(text), channel));
	}
}
