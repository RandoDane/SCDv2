package com.scd.client.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.scd.client.core.ScdLog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Supplier;

/**
 * One JSON file holding one data object, with the durability the original per-class
 * load/save copies lacked:
 * <ul>
 *   <li><b>Atomic writes</b> - written to a temp file then moved over the original, so a crash
 *       mid-save can never leave a truncated file behind;</li>
 *   <li><b>Debounced saves</b> - {@link #markDirty()} is cheap; the actual write happens at most once
 *       per flush interval on a background thread (and on shutdown), instead of rewriting the whole
 *       file on every single kill or drop;</li>
 *   <li><b>Corruption quarantine</b> - an unreadable file is renamed aside (never silently
 *       overwritten) and defaults are used, so the data can still be recovered by hand;</li>
 *   <li><b>Schema versions</b> - the stored {@code "schema"} number is handed to a migrator before
 *       deserializing, so formats can evolve without breaking old saves.</li>
 * </ul>
 */
public final class JsonStore<T> {
	public interface Migrator {
		/** Upgrades {@code root} in place from {@code fromVersion} to the current version. */
		void migrate(JsonObject root, int fromVersion);
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private final Path path;
	private final Class<T> type;
	private final int schemaVersion;
	private final Migrator migrator;
	private T value;
	private volatile boolean dirty;

	private JsonStore(Path path, Class<T> type, int schemaVersion, Migrator migrator) {
		this.path = path;
		this.type = type;
		this.schemaVersion = schemaVersion;
		this.migrator = migrator;
	}

	public static <T> JsonStore<T> open(Path path, Class<T> type, int schemaVersion, Supplier<T> defaults, Migrator migrator) {
		JsonStore<T> store = new JsonStore<>(path, type, schemaVersion, migrator);
		store.value = store.readOrNull();
		if (store.value == null) {
			store.value = defaults.get();
			store.dirty = true;
		}
		StoreRegistry.register(store);
		return store;
	}

	public static <T> JsonStore<T> open(Path path, Class<T> type, Supplier<T> defaults) {
		return open(path, type, 1, defaults, (root, from) -> {
		});
	}

	public T get() {
		return value;
	}

	/** Replaces the whole object (e.g. after importing legacy data) and schedules a save. */
	public void set(T newValue) {
		value = newValue;
		markDirty();
	}

	public void markDirty() {
		dirty = true;
	}

	public Path path() {
		return path;
	}

	/** Writes now if anything changed. Safe to call from any thread; serializes on the store. */
	public synchronized void flush() {
		if (!dirty) return;
		dirty = false;
		try {
			JsonElement tree = GSON.toJsonTree(value);
			if (tree.isJsonObject()) tree.getAsJsonObject().addProperty("schema", schemaVersion);
			Files.createDirectories(path.getParent());
			Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
			Files.writeString(tmp, GSON.toJson(tree));
			try {
				Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (IOException atomicUnsupported) {
				Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException | RuntimeException e) {
			dirty = true;
			ScdLog.error("Failed to save " + path, e);
		}
	}

	private T readOrNull() {
		if (!Files.exists(path)) return null;
		try {
			JsonElement tree = JsonParser.parseString(Files.readString(path));
			if (tree.isJsonObject()) {
				JsonObject root = tree.getAsJsonObject();
				int from = root.has("schema") ? root.get("schema").getAsInt() : 1;
				if (from < schemaVersion) {
					migrator.migrate(root, from);
					dirty = true;
				}
				root.remove("schema");
			}
			T loaded = GSON.fromJson(tree, type);
			if (loaded == null) throw new IOException("empty document");
			return loaded;
		} catch (IOException | RuntimeException e) {
			quarantine(e);
			return null;
		}
	}

	private void quarantine(Exception cause) {
		Path aside = path.resolveSibling(path.getFileName() + ".corrupt-" + System.currentTimeMillis());
		try {
			Files.move(path, aside, StandardCopyOption.REPLACE_EXISTING);
			ScdLog.error("Could not read " + path + " - moved it to " + aside.getFileName() + " and started fresh", cause);
		} catch (IOException moveFailed) {
			ScdLog.error("Could not read " + path + " (and could not move it aside) - starting fresh", cause);
		}
	}

	/** Shared Gson for code that needs to parse ad-hoc JSON the same way (legacy import). */
	public static Gson gson() {
		return GSON;
	}
}
