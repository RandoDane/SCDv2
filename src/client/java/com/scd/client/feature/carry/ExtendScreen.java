package com.scd.client.feature.carry;

import com.scd.client.ui.Rows;
import com.scd.client.ui.ScdScreen;
import com.scd.client.ui.widget.TextField;
import com.scd.logic.Numbers;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;

/** Add units at the agreed price, or correct progress by hand. */
final class ExtendScreen extends ScdScreen {
	private final CarryService carries;
	private final Carry carry;
	private String amount = "";

	ExtendScreen(Screen parent, CarryService carries, Carry carry) {
		super("Add more", parent);
		this.carries = carries;
		this.carry = carry;
	}

	@Override
	protected void build(Rows rows) {
		rows.value(carry.customer, () -> carry.target().trim());
		rows.value("Progress", () -> carry.unitsDone + "/" + carry.unitsOwed + " " + carry.unit());
		rows.value("Price per " + (carry.kind == Carry.Kind.DUNGEON ? "run" : "kill"), () -> Numbers.coins(carry.pricePerUnit));
		rows.header("Extend");
		rows.buttons(List.of("+1", "+5", "+10"), List.of(() -> extend(1), () -> extend(5), () -> extend(10)));
		rows.text(null, "Custom amount", amount, TextField.digits(), s -> amount = s);
		rows.button("Add custom amount", () -> {
			Integer n = Numbers.parseIntOrNull(amount);
			if (n != null && n > 0) extend(n);
		});
		rows.header("Correct progress");
		rows.note("If a kill or run was missed or counted twice.");
		rows.buttons(List.of("-1 done", "+1 done"), List.of(() -> {
			carries.adjust(carry, -1);
			rebuild();
		}, () -> {
			carries.adjust(carry, 1);
			rebuild();
		}));
	}

	private void extend(int n) {
		carries.extend(carry, n);
		onClose();
	}
}
