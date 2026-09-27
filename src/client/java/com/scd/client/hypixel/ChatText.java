package com.scd.client.hypixel;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

/** Helpers for the parts of a chat component that getString() doesn't include. */
public final class ChatText {
	private ChatText() {
	}

	/** First "show text" hover tooltip anywhere in the component tree, or null. */
	public static String hoverText(Component c) {
		if (c.getStyle().getHoverEvent() instanceof HoverEvent.ShowText show) return show.value().getString();
		for (Component sibling : c.getSiblings()) {
			String found = hoverText(sibling);
			if (found != null) return found;
		}
		return null;
	}
}
