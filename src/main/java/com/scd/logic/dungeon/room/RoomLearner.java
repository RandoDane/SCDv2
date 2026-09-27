package com.scd.logic.dungeon.room;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SCD's own room list, learned while playing: every room walked into is kept with its core(s),
 * name, type, shape and the secret total the action bar reported there. Rooms nobody has named
 * yet are kept as "Unknown &lt;core&gt;" so they can be named later. Saved in the same format as
 * rooms.json (plus "visits"), so the room database loads it like any other room file.
 */
public final class RoomLearner {
	public static final class Entry {
		public String name;
		public String type;
		public String shape;
		public final List<Integer> cores = new ArrayList<>();
		public int crypts;
		public int maxSecrets;
		public int visits;
	}

	private final Map<String, Entry> byName = new LinkedHashMap<>();
	private final Map<Integer, Entry> byCore = new HashMap<>();

	public static String unknownName(int core) {
		return "Unknown " + core;
	}

	public static boolean isUnknown(String name) {
		return name != null && name.startsWith("Unknown ");
	}

	/**
	 * Records a visit. {@code name} null means the room isn't identified; {@code secrets} null means
	 * the action bar hasn't said yet. Returns true when anything worth saving changed.
	 */
	public boolean observe(int core, String name, RoomKind kind, RoomShape shape, int crypts, Integer secrets, boolean newVisit) {
		String wanted = name != null ? name : unknownName(core);
		Entry e = byCore.get(core);
		boolean changed = false;
		if (e == null) {
			e = byName.get(wanted);
			if (e == null) {
				e = new Entry();
				e.name = wanted;
				byName.put(wanted, e);
			}
			e.cores.add(core);
			byCore.put(core, e);
			changed = true;
		} else if (name != null && isUnknown(e.name)) {
			// A room learned as unknown got its name (taught, or the database now knows it).
			byName.remove(e.name);
			Entry named = byName.get(name);
			if (named != null && named != e) {
				for (int c : e.cores) if (!named.cores.contains(c)) named.cores.add(c);
				for (int c : e.cores) byCore.put(c, named);
				named.visits += e.visits;
				named.maxSecrets = Math.max(named.maxSecrets, e.maxSecrets);
				e = named;
			} else {
				e.name = name;
				byName.put(name, e);
			}
			changed = true;
		}
		if (kind != null && !kind.name().toLowerCase(java.util.Locale.ROOT).equals(e.type)) {
			e.type = kind.name().toLowerCase(java.util.Locale.ROOT);
			changed = true;
		}
		if (shape != null && !shape.key.equals(e.shape)) {
			e.shape = shape.key;
			changed = true;
		}
		if (crypts > e.crypts) {
			e.crypts = crypts;
			changed = true;
		}
		if (secrets != null && secrets > 0 && secrets != e.maxSecrets) {
			e.maxSecrets = secrets;
			changed = true;
		}
		if (newVisit) {
			e.visits++;
			changed = true;
		}
		return changed;
	}

	public Entry byCore(int core) {
		return byCore.get(core);
	}

	public Collection<Entry> entries() {
		return byName.values();
	}

	/** Named rooms learned so far (unknown ones don't count). */
	public int namedCount() {
		int n = 0;
		for (Entry e : byName.values()) if (!isUnknown(e.name)) n++;
		return n;
	}

	public int load(Reader json) {
		int n = 0;
		for (JsonElement el : JsonParser.parseReader(json).getAsJsonArray()) {
			JsonObject o = el.getAsJsonObject();
			if (!o.has("name") || !o.has("cores")) continue;
			Entry e = new Entry();
			e.name = o.get("name").getAsString();
			e.type = o.has("type") ? o.get("type").getAsString() : null;
			e.shape = o.has("shape") ? o.get("shape").getAsString() : null;
			for (JsonElement c : o.getAsJsonArray("cores")) e.cores.add(c.getAsInt());
			e.crypts = o.has("crypts") ? o.get("crypts").getAsInt() : 0;
			e.maxSecrets = o.has("maxSecrets") ? o.get("maxSecrets").getAsInt() : 0;
			e.visits = o.has("visits") ? o.get("visits").getAsInt() : 0;
			byName.put(e.name, e);
			for (int c : e.cores) byCore.put(c, e);
			n++;
		}
		return n;
	}

	/** Rooms with a type and shape, in rooms.json format (entries still missing either are skipped by the loader). */
	public String toJson() {
		JsonArray arr = new JsonArray();
		for (Entry e : byName.values()) {
			JsonObject o = new JsonObject();
			o.addProperty("name", e.name);
			if (e.type != null) o.addProperty("type", e.type);
			if (e.shape != null) o.addProperty("shape", e.shape);
			JsonArray cores = new JsonArray();
			for (int c : e.cores) cores.add(c);
			o.add("cores", cores);
			o.addProperty("crypts", e.crypts);
			o.addProperty("maxSecrets", e.maxSecrets);
			o.addProperty("visits", e.visits);
			arr.add(o);
		}
		return new GsonBuilder().setPrettyPrinting().create().toJson(arr);
	}
}
