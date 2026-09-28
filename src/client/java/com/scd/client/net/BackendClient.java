package com.scd.client.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.scd.client.core.ScdLog;
import com.scd.logic.BackendUrl;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * HTTP client for the user's own SCD backend (non-price data only - every price comes from
 * {@link MarketClient}). Never talks to Hypixel directly. Every call is async and
 * completes on the HTTP client's thread; callers hop back to the client thread themselves.
 * Tracks the last success/failure so the settings screen and /scd debug can show connection health.
 */
public final class BackendClient {
	public record Status(boolean ok, String message, long atMs) {
		public static final Status UNKNOWN = new Status(false, "not contacted yet", 0);
	}

	private final HttpClient http = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();
	private final String userAgent;
	private volatile String baseUrl;
	private volatile Status status = Status.UNKNOWN;

	public BackendClient(String baseUrl, String version) {
		this.userAgent = "SCD/" + version;
		setBaseUrl(baseUrl);
	}

	/**
	 * Applies a new base URL. Returns null on success, or why it was refused (malformed URL). A
	 * refused URL leaves the client disabled until a valid one is set.
	 */
	public String setBaseUrl(String url) {
		BackendUrl.Result result = BackendUrl.validate(url);
		if (!result.ok()) {
			this.baseUrl = null;
			status = new Status(false, result.error(), System.currentTimeMillis());
			ScdLog.warn("Backend disabled: " + result.error());
			return result.error();
		}
		this.baseUrl = result.url();
		return null;
	}

	public String baseUrl() {
		return baseUrl;
	}

	public Status status() {
		return status;
	}

	/** Attribute NBT key ("undead_resistance") -> Bazaar shard product id. Static wiki-derived data. */
	public CompletableFuture<Map<String, String>> attributeShards() {
		return get("/api/attribute-shards", 10, body -> {
			Map<String, String> out = new HashMap<>();
			for (var e : body.getAsJsonObject().entrySet()) out.put(e.getKey(), e.getValue().getAsString());
			return out;
		});
	}

	public CompletableFuture<Backend.MayorInfo> mayor() {
		return get("/api/mayor", 10, body -> {
			JsonObject root = body.getAsJsonObject();
			JsonObject mayor = obj(root, "mayor");
			if (mayor == null) return Backend.MayorInfo.NONE;
			List<Backend.MayorPerk> perks = new ArrayList<>();
			JsonArray arr = arr(mayor, "perks");
			if (arr != null) for (var el : arr) perks.add(perk(el.getAsJsonObject()));
			JsonObject minister = obj(mayor, "minister_perk");
			if (minister != null) perks.add(perk(minister));
			return new Backend.MayorInfo(str(mayor, "mayor_name"), List.copyOf(perks));
		});
	}

	/** Live per-player lookup (needs the backend's own Hypixel API key) - slower and can fail in more ways. */
	public CompletableFuture<Backend.AccessorySummary> accessories(String username) {
		return get("/api/profile/" + enc(username) + "/accessories", 20, body -> {
			JsonObject root = body.getAsJsonObject();
			List<Backend.Accessory> owned = new ArrayList<>();
			JsonArray ownedArr = arr(root, "accessories");
			if (ownedArr != null) {
				for (var el : ownedArr) {
					JsonObject o = el.getAsJsonObject();
					owned.add(new Backend.Accessory(str(o, "id"), str(o, "name"), str(o, "rarity"), intOr(o, "magicalPower", 0), intOr(o, "count", 1)));
				}
			}
			List<Backend.MissingAccessory> missing = new ArrayList<>();
			JsonArray missingArr = arr(root, "missingAccessories");
			if (missingArr != null) {
				for (var el : missingArr) {
					JsonObject o = el.getAsJsonObject();
					missing.add(new Backend.MissingAccessory(str(o, "id"), str(o, "name"), str(o, "tier"), str(o, "requirement"),
							dbl(o, "price"), icon(obj(o, "icon")), str(o, "obtainMethod"), intOr(o, "magicalPowerGain", 0),
							o.has("upgrade") && o.get("upgrade").getAsBoolean()));
				}
			}
			return new Backend.AccessorySummary(str(root, "username"), intOr(root, "accessoryCount", owned.size()),
					intOrNull(root, "accessoryPower"), intOrNull(root, "peakMagicalPower"), List.copyOf(owned), List.copyOf(missing));
		});
	}

	/**
	 * Uploads one batch of a debug recording session (see {@code SessionRecorder}). Completes with the
	 * HTTP status, or exceptionally on network failure - the caller keeps the batch and retries.
	 */
	public CompletableFuture<Integer> postDebugEvents(String sessionId, String token, JsonArray events) {
		if (baseUrl == null) return CompletableFuture.failedFuture(new BackendException(status.message()));
		JsonObject payload = new JsonObject();
		payload.addProperty("token", token);
		payload.add("events", events);
		String body = payload.toString();
		HttpRequest request = request("/api/debug/sessions/" + enc(sessionId) + "/events", 15)
				.header("Content-Type", "application/json")
				.header("X-SCD-Debug-Token", token)
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.build();
		return http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(HttpResponse::statusCode);
	}

	/** A room copy on the server: {@code id} is its file ("Boulder (2)" for a puzzle variation). */
	public record CapturedRoom(String id, String name, int signature, boolean puzzle) {
	}

	/** Publishes one room's route to the community pack; completes true when it was new. */
	public CompletableFuture<Boolean> publishRoute(String room, JsonArray steps, String author) {
		if (baseUrl == null) return CompletableFuture.failedFuture(new BackendException(status.message()));
		JsonObject body = new JsonObject();
		body.addProperty("room", room);
		body.add("steps", steps);
		body.addProperty("author", author);
		HttpRequest request = request("/api/routes", 15)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body.toString()))
				.build();
		return http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(res -> {
			if (res.statusCode() != 200) throw new BackendException(errorMessage(res.body(), res.statusCode()));
			return JsonParser.parseString(res.body()).getAsJsonObject().get("stored").getAsBoolean();
		});
	}

	/** The community route pack (route pack JSON). */
	public CompletableFuture<String> communityRoutes() {
		if (baseUrl == null) return CompletableFuture.failedFuture(new BackendException(status.message()));
		HttpRequest request = request("/api/routes/pack", 20).GET().build();
		return http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(res -> {
			if (res.statusCode() != 200) throw new BackendException("HTTP " + res.statusCode());
			return res.body();
		});
	}

	/** Rooms anyone has captured so far (see backend/roomCaptures.js). */
	public CompletableFuture<List<CapturedRoom>> capturedRooms() {
		return get("/api/rooms/captures", 10, body -> {
			List<CapturedRoom> out = new ArrayList<>();
			JsonArray rooms = arr(body.getAsJsonObject(), "rooms");
			if (rooms != null) for (var el : rooms) {
				JsonObject o = el.getAsJsonObject();
				out.add(new CapturedRoom(str(o, "id"), str(o, "name"), o.has("signature") ? o.get("signature").getAsInt() : 0,
						o.has("puzzle") && o.get("puzzle").getAsBoolean()));
			}
			return out;
		});
	}

	/** Shares a captured room (gzip NBT); completes true when the server kept it. */
	public CompletableFuture<Boolean> uploadRoom(String name, int signature, boolean puzzle, byte[] file) {
		if (baseUrl == null) return CompletableFuture.failedFuture(new BackendException(status.message()));
		HttpRequest request = request("/api/rooms/captures", 30)
				.header("Content-Type", "application/octet-stream")
				.header("X-SCD-Room", encPart(name))
				.header("X-SCD-Signature", String.valueOf(signature))
				.header("X-SCD-Puzzle", puzzle ? "1" : "0")
				.POST(HttpRequest.BodyPublishers.ofByteArray(file))
				.build();
		return http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(res -> {
			if (res.statusCode() != 200) throw new BackendException(errorMessage(res.body(), res.statusCode()));
			return JsonParser.parseString(res.body()).getAsJsonObject().get("stored").getAsBoolean();
		});
	}

	public CompletableFuture<byte[]> downloadRoom(String id) {
		if (baseUrl == null) return CompletableFuture.failedFuture(new BackendException(status.message()));
		HttpRequest request = request("/api/rooms/captures/" + encPart(id), 30).GET().build();
		return http.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenApply(res -> {
			if (res.statusCode() != 200) throw new BackendException("HTTP " + res.statusCode());
			return res.body();
		});
	}

	private <T> CompletableFuture<T> get(String path, int timeoutSeconds, Function<JsonElement, T> parser) {
		if (baseUrl == null) return CompletableFuture.failedFuture(new BackendException(status.message()));
		HttpRequest request;
		try {
			request = request(path, timeoutSeconds).GET().build();
		} catch (IllegalArgumentException badUrl) {
			status = new Status(false, "invalid server URL \"" + baseUrl + "\"", System.currentTimeMillis());
			return CompletableFuture.failedFuture(badUrl);
		}
		return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenApply(res -> {
					if (res.statusCode() != 200) throw new BackendException(errorMessage(res.body(), res.statusCode()));
					T parsed = parser.apply(JsonParser.parseString(res.body()));
					status = new Status(true, "OK", System.currentTimeMillis());
					return parsed;
				})
				.whenComplete((v, err) -> {
					if (err != null) {
						Throwable cause = err.getCause() != null ? err.getCause() : err;
						status = new Status(false, cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName(), System.currentTimeMillis());
					}
				});
	}

	private HttpRequest.Builder request(String path, int timeoutSeconds) {
		return HttpRequest.newBuilder(URI.create(baseUrl + path))
				.timeout(Duration.ofSeconds(timeoutSeconds))
				.header("User-Agent", userAgent)
				.header("Accept", "application/json");
	}

	/** Server errors look like {"error": "human message", "code": "..."}. */
	private static String errorMessage(String body, int status) {
		try {
			JsonObject err = JsonParser.parseString(body).getAsJsonObject();
			if (err.has("error")) return err.get("error").getAsString();
		} catch (RuntimeException ignored) {
			// not JSON
		}
		return "HTTP " + status;
	}

	private static Backend.MayorPerk perk(JsonObject o) {
		return new Backend.MayorPerk(str(o, "name"), str(o, "description"));
	}

	private static Backend.Icon icon(JsonObject icon) {
		if (icon == null) return null;
		JsonObject skin = obj(icon, "skin");
		return new Backend.Icon(str(icon, "material"), intOrNull(icon, "durability"),
				skin != null ? str(skin, "value") : null, skin != null ? str(skin, "signature") : null);
	}

	private static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}

	/** Like {@link #enc} but spaces as %20, for paths and headers (the server decodes those as URI parts). */
	private static String encPart(String s) {
		return enc(s).replace("+", "%20");
	}

	private static JsonObject obj(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
	}

	private static JsonArray arr(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonArray() ? e.getAsJsonArray() : null;
	}

	private static String str(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && !e.isJsonNull() ? e.getAsString() : null;
	}

	private static Double dbl(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && !e.isJsonNull() ? e.getAsDouble() : null;
	}

	private static Integer intOrNull(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && !e.isJsonNull() ? e.getAsInt() : null;
	}

	private static int intOr(JsonObject o, String key, int fallback) {
		Integer v = intOrNull(o, key);
		return v != null ? v : fallback;
	}

	public static final class BackendException extends RuntimeException {
		public BackendException(String message) {
			super(message);
		}
	}
}
