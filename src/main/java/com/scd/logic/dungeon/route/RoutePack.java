package com.scd.logic.dungeon.route;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * A set of room routes, read and written in SecretRoutes' routes.json layout so packs move freely
 * between the two mods:
 * <pre>{"#origin": "...", "#copyright": "...", "Version": "1.0.0",
 *  "Altar-6": [ {"locations":[[x,y,z]...], "etherwarps":[...], "mines":[...], "interacts":[...],
 *               "tnts":[...], "enderpearls":[...], "enderpearlangles":[[yaw,pitch]...],
 *               "secret":{"type":"interact|item|bat|exit|exitroute", "location":[x,y,z]}}, ...]}</pre>
 * Keys may carry an alternative-route suffix ("Altar:2"). SCD adds optional "#name"/"#author".
 * Room names are normalized to the room database's names on load (e.g. "Altar-6" -> "Altar").
 */
public final class RoutePack {
	public String name = "Routes";
	public String author = "";
	/** Extra "#..." and "Version" header entries, kept verbatim for round-trips. */
	public final Map<String, String> header = new LinkedHashMap<>();
	/** Room name (optionally ":n" for alternatives) -> steps. */
	public final Map<String, List<RouteStep>> rooms = new LinkedHashMap<>();
	/** One route of a room: its pack key and steps. */
	public record Route(String key, List<RouteStep> steps) {
	}

	/** Routes for a room: the main one first, then alternatives ":2", ":3" ... */
	public List<Route> routesFor(String room) {
		List<Route> out = new ArrayList<>();
		for (var e : rooms.entrySet()) {
			String k = e.getKey();
			if (!baseName(k).equals(room)) continue;
			Route r = new Route(k, e.getValue());
			if (k.indexOf(':') < 0) out.addFirst(r);
			else out.add(r);
		}
		return out;
	}

	public static String baseName(String key) {
		int colon = key.indexOf(':');
		return colon >= 0 ? key.substring(0, colon) : key;
	}

	/** Adds a route for {@code room} as a new alternative ("Room", then "Room:2", ...); returns its key. */
	public String add(String room, List<RouteStep> steps) {
		String key = room;
		for (int n = 2; rooms.containsKey(key); n++) key = room + ":" + n;
		rooms.put(key, steps);
		return key;
	}

	/** Room-relative {x, z} of the block a route starts on, or null for an empty route. */
	public static int[] start(List<RouteStep> steps) {
		for (RouteStep s : steps) {
			if (!s.locations.isEmpty()) return new int[]{s.locations.getFirst()[0], s.locations.getFirst()[2]};
			if (s.secret != null) return new int[]{s.secret[0], s.secret[2]};
		}
		return null;
	}

	/** Removes every route of a room; returns how many. */
	public int removeRoom(String room) {
		List<String> keys = new ArrayList<>();
		for (String k : rooms.keySet()) if (baseName(k).equals(room)) keys.add(k);
		keys.forEach(rooms::remove);
		return keys.size();
	}

	/**
	 * @param rename maps a pack's room name to ours (identity if already ours); keeps ":n" suffixes
	 */
	public static RoutePack read(Reader reader, UnaryOperator<String> rename) {
		JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
		RoutePack pack = new RoutePack();
		for (var e : root.entrySet()) {
			String key = e.getKey();
			JsonElement v = e.getValue();
			if (key.startsWith("#") || !v.isJsonArray()) {
				if (v.isJsonPrimitive()) {
					if (key.equals("#name")) pack.name = v.getAsString();
					else if (key.equals("#author")) pack.author = v.getAsString();
					else pack.header.put(key, v.getAsString());
				}
				continue;
			}
			int colon = key.indexOf(':');
			String base = colon >= 0 ? key.substring(0, colon) : key;
			String suffix = colon >= 0 ? key.substring(colon) : "";
			List<RouteStep> steps = new ArrayList<>();
			for (JsonElement s : v.getAsJsonArray()) if (s.isJsonObject()) steps.add(readStep(s.getAsJsonObject()));
			pack.rooms.put(rename.apply(base) + suffix, steps);
		}
		return pack;
	}

	public String write() {
		JsonObject root = new JsonObject();
		root.addProperty("#name", name);
		if (!author.isEmpty()) root.addProperty("#author", author);
		header.forEach(root::addProperty);
		if (!header.containsKey("Version")) root.addProperty("Version", "1.0.0");
		rooms.forEach((room, steps) -> {
			JsonArray arr = new JsonArray();
			for (RouteStep s : steps) arr.add(writeStep(s));
			root.add(room, arr);
		});
		return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root);
	}

	/** One room's route as a standalone JSON array (for share codes). */
	public static JsonArray writeSteps(List<RouteStep> steps) {
		JsonArray arr = new JsonArray();
		for (RouteStep s : steps) arr.add(writeStep(s));
		return arr;
	}

	public static List<RouteStep> readSteps(JsonArray arr) {
		List<RouteStep> steps = new ArrayList<>();
		for (JsonElement s : arr) if (s.isJsonObject()) steps.add(readStep(s.getAsJsonObject()));
		return steps;
	}

	static RouteStep readStep(JsonObject o) {
		RouteStep s = new RouteStep();
		readPositions(o, "locations", s.locations);
		s.manual = o.has("scd_nodes") && o.get("scd_nodes").getAsBoolean();
		readPositions(o, "etherwarps", s.etherwarps);
		readPositions(o, "mines", s.mines);
		readPositions(o, "interacts", s.interacts);
		readPositions(o, "tnts", s.tnts);
		readPositions(o, "enderpearls", s.pearls);
		readPositions(o, "scd_pearl_landings", s.pearlLandings);
		if (o.has("enderpearlangles") && o.get("enderpearlangles").isJsonArray()) {
			for (JsonElement a : o.getAsJsonArray("enderpearlangles")) {
				JsonArray p = a.getAsJsonArray();
				s.pearlAngles.add(new float[]{p.get(0).getAsFloat(), p.get(1).getAsFloat()});
			}
		}
		if (o.has("secret") && o.get("secret").isJsonObject()) {
			JsonObject sec = o.getAsJsonObject("secret");
			s.secretType = RouteStep.SecretType.fromKey(sec.has("type") ? sec.get("type").getAsString() : "exit");
			if (sec.has("location") && sec.get("location").isJsonArray()) s.secret = pos(sec.getAsJsonArray("location"));
		}
		return s;
	}

	static JsonObject writeStep(RouteStep s) {
		JsonObject o = new JsonObject();
		o.add("locations", positions(s.locations));
		if (s.manual) o.addProperty("scd_nodes", true);
		o.add("etherwarps", positions(s.etherwarps));
		o.add("mines", positions(s.mines));
		o.add("interacts", positions(s.interacts));
		o.add("tnts", positions(s.tnts));
		o.add("enderpearls", positions(s.pearls));
		if (!s.pearlLandings.isEmpty()) o.add("scd_pearl_landings", positions(s.pearlLandings));
		JsonArray angles = new JsonArray();
		for (float[] a : s.pearlAngles) {
			JsonArray p = new JsonArray();
			p.add(a[0]);
			p.add(a[1]);
			angles.add(p);
		}
		o.add("enderpearlangles", angles);
		JsonObject sec = new JsonObject();
		sec.addProperty("type", s.secretType.key);
		int[] loc = s.secret != null ? s.secret : !s.locations.isEmpty() ? s.locations.getLast() : new int[]{0, 0, 0};
		JsonArray l = new JsonArray();
		for (int v : loc) l.add(v);
		sec.add("location", l);
		o.add("secret", sec);
		return o;
	}

	private static void readPositions(JsonObject o, String key, List<int[]> out) {
		if (!o.has(key) || !o.get(key).isJsonArray()) return;
		for (JsonElement p : o.getAsJsonArray(key)) if (p.isJsonArray() && p.getAsJsonArray().size() >= 3) out.add(pos(p.getAsJsonArray()));
	}

	private static int[] pos(JsonArray a) {
		return new int[]{(int) Math.floor(a.get(0).getAsDouble()), (int) Math.floor(a.get(1).getAsDouble()), (int) Math.floor(a.get(2).getAsDouble())};
	}

	private static JsonArray positions(List<int[]> list) {
		JsonArray arr = new JsonArray();
		for (int[] p : list) {
			JsonArray a = new JsonArray();
			for (int v : p) a.add(v);
			arr.add(a);
		}
		return arr;
	}
}
