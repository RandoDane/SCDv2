package com.scd.client.feature.dungeon.route;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.scd.client.core.ScdLog;
import com.scd.client.storage.ScdPaths;
import com.scd.logic.dungeon.route.RoutePack;
import com.scd.logic.dungeon.route.RouteStep;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Route packs in {@code config/scd/routes/*.json}. {@value #MINE} holds routes recorded or imported
 * in game and always wins; every other file is a pack (SCD or SecretRoutes format) that can be
 * switched off. Room names from other mods are mapped onto ours with a bundled alias table.
 */
public final class RouteLibrary {
	public static final String MINE = "my_routes.json";
	private static final Path DIR = ScdPaths.file("routes");

	public record Pack(String file, RoutePack pack) {
	}

	private final Map<String, String> aliases = new HashMap<>();
	private final List<Pack> packs = new ArrayList<>();
	private RoutePack mine;

	public RouteLibrary() {
		try (var in = RouteLibrary.class.getResourceAsStream("/assets/scd/dungeon/room_aliases.json")) {
			if (in != null) {
				JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
				o.entrySet().forEach(e -> aliases.put(e.getKey(), e.getValue().getAsString()));
			}
		} catch (Exception e) {
			ScdLog.warn("Room alias table failed to load", e);
		}
		reload();
	}

	public String canonical(String room) {
		return aliases.getOrDefault(room, room);
	}

	public void reload() {
		packs.clear();
		mine = null;
		try {
			Files.createDirectories(DIR);
			try (Stream<Path> files = Files.list(DIR)) {
				for (Path f : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
					try (Reader r = Files.newBufferedReader(f)) {
						RoutePack p = RoutePack.read(r, this::canonical);
						String name = f.getFileName().toString();
						if (name.equals(MINE)) mine = p;
						else packs.add(new Pack(name, p));
					} catch (Exception e) {
						ScdLog.warn("Route pack " + f.getFileName() + " is invalid: " + e.getMessage());
					}
				}
			}
		} catch (IOException e) {
			ScdLog.warn("Route folder unreadable", e);
		}
		if (mine == null) {
			mine = new RoutePack();
			mine.name = "My routes";
		}
	}

	public RoutePack mine() {
		return mine;
	}

	public List<Pack> packs() {
		return List.copyOf(packs);
	}

	/** Every route for a room: yours first, then enabled packs in file order. */
	public List<RoutePack.Route> routesFor(String room, Collection<String> disabledPacks) {
		List<RoutePack.Route> out = new ArrayList<>(mine.routesFor(room));
		for (Pack p : packs) if (!disabledPacks.contains(p.file())) out.addAll(p.pack().routesFor(room));
		out.removeIf(r -> r.steps().isEmpty());
		return out;
	}

	public void saveMine() throws IOException {
		Files.createDirectories(DIR);
		Path tmp = DIR.resolve(MINE + ".tmp");
		Files.writeString(tmp, mine.write());
		Files.move(tmp, DIR.resolve(MINE), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}

	public static Path folder() {
		return DIR;
	}
}
