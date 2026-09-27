package com.scd.client.feature.carry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.scd.client.core.ScdLog;
import com.scd.client.storage.ScdPaths;

import java.nio.file.Files;
import java.nio.file.Path;

/** Imports 1.x scd_carries.json (Slayer) and scd_dungeon_carries.json into the unified book. */
final class LegacyCarryImport {
	private LegacyCarryImport() {
	}

	static void into(CarryBook book) {
		int n = 0;
		for (JsonElement el : read("scd_carries.json")) {
			JsonObject o = el.getAsJsonObject();
			Carry c = base(book, o);
			c.kind = Carry.Kind.SLAYER;
			c.slayerType = str(o, "type");
			c.tier = str(o, "tier");
			c.pricePerUnit = lng(o, "pricePerKill");
			c.unitsOwed = (int) lng(o, "killsOwed");
			c.unitsDone = (int) lng(o, "killsCompleted");
			c.totalTimeMs = lng(o, "totalKillTimeMs");
			book.carries.add(c);
			n++;
		}
		for (JsonElement el : read("scd_dungeon_carries.json")) {
			JsonObject o = el.getAsJsonObject();
			Carry c = base(book, o);
			c.kind = Carry.Kind.DUNGEON;
			c.floor = str(o, "floor");
			c.pricePerUnit = lng(o, "pricePerRun");
			c.unitsOwed = (int) lng(o, "runsOwed");
			c.unitsDone = (int) lng(o, "runsCompleted");
			c.totalTimeMs = lng(o, "totalRunTimeMs");
			book.carries.add(c);
			n++;
		}
		if (n > 0) ScdLog.info("Imported " + n + " carries from 1.x");
	}

	private static Carry base(CarryBook book, JsonObject o) {
		Carry c = new Carry();
		c.id = book.nextId++;
		c.customer = str(o, "playerName");
		c.status = "COMPLETED".equals(str(o, "status")) ? Carry.Status.COMPLETED : Carry.Status.ACTIVE;
		c.createdAt = lng(o, "createdAt");
		c.completedAt = lng(o, "completedAt");
		return c;
	}

	private static JsonArray read(String name) {
		Path p = ScdPaths.legacy(name);
		if (!Files.exists(p)) return new JsonArray();
		try {
			JsonElement e = JsonParser.parseString(Files.readString(p));
			return e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
		} catch (Exception ex) {
			ScdLog.warn("Skipping unreadable legacy carries file " + p, ex);
			return new JsonArray();
		}
	}

	private static String str(JsonObject o, String k) {
		return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null;
	}

	private static long lng(JsonObject o, String k) {
		return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsLong() : 0;
	}
}
