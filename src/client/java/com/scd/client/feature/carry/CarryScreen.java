package com.scd.client.feature.carry;

import com.scd.client.ui.Rows;
import com.scd.client.ui.ScdScreen;
import com.scd.client.ui.Ui;
import com.scd.client.ui.widget.FlatButton;
import com.scd.logic.Numbers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;

/** All carries with progress bars; Done / extend / remove per row, plus totals. */
public final class CarryScreen extends ScdScreen {
	private final CarryService carries;
	private boolean showCompleted;
	private Long confirmRemove;

	public CarryScreen(Screen parent, CarryService carries) {
		super("Carries", parent, 340);
		this.carries = carries;
	}

	@Override
	protected String subtitle() {
		return Numbers.coins(carries.outstandingTotal()) + " owed · " + Numbers.coins(carries.earnedTotal()) + " earned";
	}

	@Override
	protected List<FooterButton> footer() {
		return List.of(new FooterButton("New carry...", () -> Minecraft.getInstance().gui.setScreen(new CarryFormScreen(this, carries))));
	}

	@Override
	protected void build(Rows rows) {
		List<Carry> list = carries.all().stream().filter(c -> showCompleted || c.isActive()).toList();
		rows.toggle("Show completed", null, () -> showCompleted, v -> {
			showCompleted = v;
			rebuild();
		});
		if (list.isEmpty()) {
			rows.note(showCompleted ? "No carries yet." : "No active carries. Use \"New carry...\" or /scd carry add.");
			return;
		}
		for (Carry c : list) row(rows, c);
	}

	private void row(Rows rows, Carry c) {
		int x0 = rows.x();
		int w = rows.width();
		List<FlatButton> buttons = new ArrayList<>();
		int bx = x0 + w;
		boolean confirming = confirmRemove != null && confirmRemove == c.id;
		bx -= confirming ? 44 : 16;
		buttons.add(new FlatButton(bx, 2, confirming ? 44 : 16, 14, confirming ? "Sure?" : "×", () -> {
			if (confirming) {
				carries.remove(c);
				confirmRemove = null;
			} else {
				confirmRemove = c.id;
			}
			rebuild();
		}).danger().tooltip("Delete this carry"));
		bx -= 18;
		buttons.add(new FlatButton(bx, 2, 16, 14, "+", () -> Minecraft.getInstance().gui.setScreen(new ExtendScreen(this, carries, c)))
				.tooltip("Add more " + c.unit() + " / adjust progress"));
		if (c.isActive()) {
			bx -= 38;
			buttons.add(new FlatButton(bx, 2, 36, 14, "Done", () -> {
				carries.finish(c);
				rebuild();
			}).tooltip("Close and post the review message in party chat"));
		}
		int textW = bx - x0 - 6;
		rows.custom(30, (g, x, y, rw, mx, my) -> {
			int color = c.isActive() ? Ui.theme().textPrimary() : Ui.theme().textMuted();
			Ui.text(g, Ui.ellipsize("#" + c.id + " " + c.customer + " · " + c.target().trim(), textW), x0, y + 2, color);
			String sub = c.unitsDone + "/" + c.unitsOwed + " " + c.unit() + " · " + Numbers.coins(c.pricePerUnit) + " each"
					+ (c.averageMs() > 0 ? " · avg " + Numbers.duration(c.averageMs()) : "");
			Ui.text(g, Ui.ellipsize(sub, textW), x0, y + 12, Ui.theme().textMuted());
			float frac = c.unitsOwed > 0 ? Math.min(1f, c.unitsDone / (float) c.unitsOwed) : 0;
			Ui.bar(g, x0, y + 23, textW, 3, frac, c.unitsDone >= c.unitsOwed ? Ui.SUCCESS : Ui.theme().accent());
		}, buttons);
	}
}
