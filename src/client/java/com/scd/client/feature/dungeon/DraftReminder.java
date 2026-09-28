package com.scd.client.feature.dungeon;

import com.scd.client.ScdMod;
import com.scd.client.core.Chat;
import com.scd.client.core.Events;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A failed puzzle can be reset with an Architect's First Draft. When one fails ("PUZZLE FAIL! ..."
 * or Oruo's "chose the wrong answer"), a title says so and chat shows how many drafts you carry,
 * or a button that takes one from your sacks (/gfs, only when you click it).
 */
final class DraftReminder {
	private static final Pattern FAIL = Pattern.compile("^PUZZLE FAIL! (\\w{1,16}) (.+)$");
	private static final Pattern QUIZ_FAIL = Pattern.compile("^\\[STATUE] Oruo the Omniscient: (\\w{1,16}) chose the wrong answer!.*$");

	DraftReminder(ScdMod mod, DungeonFeature dungeon) {
		mod.bus.subscribe(Events.ChatReceived.class, e -> {
			if (!e.isSystem() || !dungeon.state().inDungeon() || !mod.config().dungeon.draftReminder) return;
			String t = e.clean().trim();
			Matcher m = FAIL.matcher(t), q = QUIZ_FAIL.matcher(t);
			String who, what;
			if (m.matches()) {
				who = m.group(1);
				what = m.group(2).replaceFirst(" Yikes!$", "");
			} else if (q.matches()) {
				who = q.group(1);
				what = "got the Quiz wrong";
			} else {
				return;
			}
			int drafts = drafts();
			Chat.title(Component.literal("Puzzle failed").withStyle(ChatFormatting.RED),
					Component.literal(drafts > 0 ? "Use an Architect's First Draft" : "No Architect's First Draft on you").withStyle(ChatFormatting.GOLD), true);
			boolean ends = what.endsWith("!") || what.endsWith(".");
			var line = Component.literal(who + " " + what + (ends ? " " : ". ")).withStyle(ChatFormatting.RED);
			if (drafts > 0) line.append(Component.literal("You have " + drafts + " Architect's First Draft" + (drafts > 1 ? "s" : "") + ".").withStyle(ChatFormatting.GOLD));
			else line.append(Chat.button("Get one from sacks", "/gfs ARCHITECT_FIRST_DRAFT 1", true, "Takes an Architect's First Draft from your sacks"));
			Chat.info(line);
		});
	}

	/** Architect's First Drafts in your inventory. */
	private static int drafts() {
		var p = Minecraft.getInstance().player;
		if (p == null) return 0;
		int n = 0;
		var inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			var s = inv.getItem(i);
			if (!s.isEmpty() && s.getHoverName().getString().contains("Architect's First Draft")) n += s.getCount();
		}
		return n;
	}
}
