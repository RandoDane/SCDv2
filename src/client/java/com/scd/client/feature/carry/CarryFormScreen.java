package com.scd.client.feature.carry;

import com.scd.client.feature.slayer.SlayerType;
import com.scd.client.hypixel.Players;
import com.scd.client.ui.Rows;
import com.scd.client.ui.ScdScreen;
import com.scd.client.ui.Ui;
import com.scd.client.ui.widget.TextField;
import com.scd.logic.Numbers;
import com.scd.logic.dungeon.Floor;
import com.scd.logic.slayer.SlayerTier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.Arrays;
import java.util.List;
import java.util.OptionalLong;

/**
 * New carry: kind, customer, target, price per unit and count - the total is derived and shown live
 * (a deal is "N at X each"). Target defaults to the last carry of that kind. Customers must be
 * online, so a typo can never create a carry that silently never tracks.
 */
public final class CarryFormScreen extends ScdScreen {
	private final CarryService carries;
	private Carry.Kind kind = Carry.Kind.SLAYER;
	private String player = "";
	private SlayerType type = SlayerType.ZOMBIE;
	private String tier = "III";
	private String floor = "F7";
	private String price = "";
	private String count = "";
	private String error;

	public CarryFormScreen(Screen parent, CarryService carries) {
		super("New carry", parent);
		this.carries = carries;
		Carry s = carries.latest(Carry.Kind.SLAYER);
		if (s != null && s.type() != null) {
			type = s.type();
			tier = s.tier != null ? s.tier : tier;
		}
		Carry d = carries.latest(Carry.Kind.DUNGEON);
		if (d != null && d.floor != null) floor = d.floor;
	}

	@Override
	protected void build(Rows rows) {
		rows.cycle("Kind", List.of(Carry.Kind.values()), () -> kind, k -> {
			kind = k;
			rebuild();
		}, k -> k == Carry.Kind.SLAYER ? "Slayer" : "Dungeon");
		rows.text("Player", "Exact name", player, TextField.ign(), s -> player = s);
		rows.button("Choose online player...", () -> Minecraft.getInstance().gui.setScreen(new PlayerPickerScreen(this, name -> player = name)));
		if (kind == Carry.Kind.SLAYER) {
			rows.cycle("Boss", Arrays.asList(SlayerType.values()), () -> type, t -> type = t, SlayerType::bossName);
			rows.cycle("Tier", SlayerTier.ALL, () -> tier, t -> tier = t, t -> "Tier " + t);
		} else {
			rows.cycle("Floor", Floor.CARRYABLE, () -> floor, f -> floor = f, f -> f);
		}
		rows.text(kind == Carry.Kind.SLAYER ? "Price per kill" : "Price per run", "e.g. 1.3m, 800k", price, TextField.compactNumber(), s -> price = s);
		rows.text(kind == Carry.Kind.SLAYER ? "Kills" : "Runs", "How many were paid for", count, TextField.digits(), s -> count = s);
		rows.line(() -> {
			OptionalLong p = Numbers.parseCompactLong(price);
			Integer n = Numbers.parseIntOrNull(count);
			return p.isPresent() && n != null && n > 0 ? "Total: " + Numbers.coins(p.getAsLong() * n) + " coins" : "Total: -";
		}, () -> Ui.theme().textSecondary());
		rows.line(() -> error != null ? error : "", () -> Ui.DANGER);
		rows.button("Add carry", this::submit);
	}

	private void submit() {
		String name = Players.resolveOnline(player);
		OptionalLong p = Numbers.parseCompactLong(price);
		Integer n = Numbers.parseIntOrNull(count);
		if (player.isBlank()) error = "Enter a player name.";
		else if (name == null) error = "\"" + player + "\" isn't on this server - pick from the list.";
		else if (p.isEmpty() || p.getAsLong() <= 0) error = "Enter a valid price (e.g. 1.3m, 800k).";
		else if (n == null || n <= 0) error = "Enter how many " + (kind == Carry.Kind.SLAYER ? "kills" : "runs") + ".";
		else {
			if (kind == Carry.Kind.SLAYER) carries.addSlayer(name, type, tier, p.getAsLong(), n);
			else carries.addDungeon(name, floor, p.getAsLong(), n);
			onClose();
			return;
		}
		rebuild();
	}
}
