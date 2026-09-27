package com.scd.logic.dungeon.room;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Core hash -> room. Loaded from Odin's rooms.json format:
 * {@code [{"name","type","shape","cores":[int...],"crypts","maxSecrets","trappedChests"}]}.
 * Later sources override earlier ones per core, so a user file can add or fix rooms.
 */
public final class RoomDatabase {
	private final Map<Integer, RoomInfo> byCore = new HashMap<>();
	private final Map<String, RoomInfo> byName = new HashMap<>();

	public int load(Reader json) {
		JsonArray arr = JsonParser.parseReader(json).getAsJsonArray();
		int n = 0;
		for (JsonElement el : arr) {
			JsonObject o = el.getAsJsonObject();
			String name = str(o, "name");
			RoomKind kind = RoomKind.fromKey(str(o, "type"));
			RoomShape shape = RoomShape.fromKey(str(o, "shape"));
			if (name == null || kind == null || shape == null || !o.has("cores")) continue;
			List<Integer> cores = new ArrayList<>();
			for (JsonElement c : o.getAsJsonArray("cores")) cores.add(c.getAsInt());
			RoomInfo info = new RoomInfo(name, kind, shape, List.copyOf(cores), num(o, "crypts"), num(o, "maxSecrets"), num(o, "trappedChests"));
			for (int c : cores) byCore.put(c, info);
			byName.put(name, info);
			n++;
		}
		return n;
	}

	public RoomInfo byCore(int core) {
		return byCore.get(core);
	}

	public RoomInfo byName(String name) {
		return byName.get(name);
	}

	public Collection<RoomInfo> rooms() {
		return byName.values();
	}

	public int size() {
		return byName.size();
	}

	private static String str(JsonObject o, String k) {
		return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null;
	}

	private static int num(JsonObject o, String k) {
		return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsInt() : 0;
	}
}
