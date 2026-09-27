package com.scd.client.feature.carry;

import com.scd.client.hypixel.Players;
import com.scd.client.ui.Rows;
import com.scd.client.ui.ScdScreen;
import com.scd.client.ui.widget.TextField;
import net.minecraft.client.gui.screens.Screen;

import java.util.Locale;
import java.util.function.Consumer;

/** Online players from the tab list, filterable, so names don't have to be typed exactly. */
final class PlayerPickerScreen extends ScdScreen {
	private final Consumer<String> onPick;
	private String filter = "";

	PlayerPickerScreen(Screen parent, Consumer<String> onPick) {
		super("Choose player", parent, 240);
		this.onPick = onPick;
	}

	@Override
	protected void build(Rows rows) {
		rows.text(null, "Filter...", filter, TextField.ign(), s -> {
			filter = s;
			rebuild();
		});
		var names = Players.others().stream().filter(n -> n.toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT))).toList();
		if (names.isEmpty()) rows.note("No matching players on the tab list.");
		for (String name : names) {
			rows.button(name, () -> {
				onPick.accept(name);
				onClose();
			});
		}
	}
}
