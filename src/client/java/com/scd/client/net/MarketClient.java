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
import java.util.function.Supplier;

/**
 * Client for the scd.wtf market API (docs: https://api.scd.wtf/docs) - the single source of every
 * price in the mod: Bazaar quotes and history, Auction House estimates and lowest BINs.
 *
 * The base URL is a constant, not a setting, and every request URL is checked to be exactly
 * https://market.scd.wtf before the key is attached (redirects are not followed), so the key can
 * never be sent anywhere else. It goes in {@code X-API-Key} and is never logged. A 429 honours
 * retry-after by failing fast until the window passes, instead of hammering the API.
 */
public final class MarketClient {
	public static final String BASE = "https://" + BackendUrl.MARKET_HOST + "/api";

	private final HttpClient http = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.followRedirects(HttpClient.Redirect.NEVER)
			.build();
	private final Supplier<String> apiKey;
	private final String userAgent;
	private volatile long backoffUntilMs;
	private volatile BackendClient.Status status = BackendClient.Status.UNKNOWN;

	public MarketClient(Supplier<String> apiKey, String version) {
		this.apiKey = apiKey;
		this.userAgent = "SCD/" + version;
	}

	public BackendClient.Status status() {
		return status;
	}

	public boolean hasKey() {
		return !effectiveKey().isBlank();
	}

	/** The player's own key if they set one (admins), otherwise the key built into the mod. */
	private String effectiveKey() {
		String k = apiKey.get();
		if (k != null && !k.isBlank()) return k.trim();
		return BuildSecrets.marketKey();
	}

	/** True when requests use the mod's built-in key rather than a personal one. */
	public boolean usingBuiltInKey() {
		String k = apiKey.get();
		return (k == null || k.isBlank()) && !BuildSecrets.marketKey().isBlank();
	}

	/** Every Bazaar product with its instant buy/sell price. */
	public CompletableFuture<List<Backend.Product>> bazaarProducts() {
		return get("/bazaar/products?limit=5000", 15, MarketClient::parseProducts);
	}

	public CompletableFuture<List<Backend.Product>> bazaarSearch(String query, int limit) {
		return get("/bazaar/products?q=" + enc(query) + "&limit=" + limit, 10, MarketClient::parseProducts);
	}

	/**
	 * Mid-price candles for a chart. range is "1d" (5 minute candles), "7d" (hourly) or "30d"
	 * (hourly); timestamps come back in seconds and are converted to ms.
	 */
	public CompletableFuture<List<Backend.HistoryPoint>> bazaarHistory(String product, String range) {
		String res = switch (range) {
			case "1d" -> "5m";
			case "30d", "7d" -> "1h";
			default -> "1d";
		};
		return get("/bazaar/product/" + enc(product) + "/history?res=" + res + "&start=" + enc(range), 10, body -> {
			List<Backend.HistoryPoint> out = new ArrayList<>();
			JsonArray candles = arr(body.getAsJsonObject(), "candles");
			if (candles == null) return out;
			for (var el : candles) {
				JsonObject c = el.getAsJsonObject();
				Double close = dbl(c, "close");
				Double bucket = dbl(c, "bucket");
				if (close == null || bucket == null) continue;
				Double sell = dbl(c, "sell_close");
				Double buy = dbl(c, "buy_close");
				out.add(new Backend.HistoryPoint(Math.round(bucket * 1000), sell != null ? sell : close, buy != null ? buy : close));
			}
			return out;
		});
	}

	/** Auction estimates for up to a few hundred comparable keys in one request. Unknown keys are absent. */
	public CompletableFuture<Map<String, Backend.AuctionPrice>> auctionPrices(List<String> keys) {
		return get("/auction/prices?keys=" + enc(String.join(",", keys)) + "&limit=" + Math.max(1, keys.size()), 15, body -> {
			Map<String, Backend.AuctionPrice> out = new HashMap<>();
			for (var e : body.getAsJsonObject().entrySet()) {
				if (!e.getValue().isJsonObject()) continue;
				out.put(e.getKey(), auctionPrice(e.getKey(), e.getValue().getAsJsonObject()));
			}
			return out;
		});
	}

	public CompletableFuture<Backend.AuctionPrice> auctionPrice(String key) {
		return get("/auction/price?key=" + enc(key), 10, body -> auctionPrice(key, body.getAsJsonObject()));
	}

	public CompletableFuture<List<Backend.AuctionKey>> auctionSearch(String query, int limit) {
		return get("/auction/search?q=" + enc(query) + "&limit=" + limit, 10, body -> {
			List<Backend.AuctionKey> out = new ArrayList<>();
			if (!body.isJsonArray()) return out;
			for (var el : body.getAsJsonArray()) {
				JsonObject o = el.getAsJsonObject();
				Double sales = dbl(o, "sales_14d");
				out.add(new Backend.AuctionKey(str(o, "key"), str(o, "name"), str(o, "item_id"), sales != null ? (int) (double) sales : 0));
			}
			return out;
		});
	}

	/** Values one concrete item from its Hypixel item_bytes (base64 gzip NBT) - read-only, allowed for community keys. */
	public CompletableFuture<Backend.ItemValue> itemValue(String itemBytesBase64) {
		JsonObject body = new JsonObject();
		body.addProperty("item_bytes", itemBytesBase64);
		return send("/auction/value", 15, body.toString(), json -> {
			JsonObject o = json.getAsJsonObject();
			JsonObject base = o.has("base") && o.get("base").isJsonObject() ? o.getAsJsonObject("base") : null;
			JsonObject item = o.has("item") && o.get("item").isJsonObject() ? o.getAsJsonObject("item") : null;
			Double addons = dbl(o, "addons_value");
			return new Backend.ItemValue(dbl(o, "estimated_value"), base != null ? dbl(base, "price") : null,
					addons != null ? addons : 0, item != null ? str(item, "key") : null);
		});
	}

	/** In-game date plus the next scheduled events (Dark Auction, Jacob's, elections, ...). */
	public CompletableFuture<List<Backend.CalendarEvent>> calendar() {
		return get("/calendar", 10, body -> {
			List<Backend.CalendarEvent> out = new ArrayList<>();
			JsonArray arr = arr(body.getAsJsonObject(), "upcoming");
			if (arr == null) return out;
			for (var el : arr) {
				JsonObject o = el.getAsJsonObject();
				Double start = dbl(o, "start"), end = dbl(o, "end");
				out.add(new Backend.CalendarEvent(str(o, "key"), str(o, "name"), start != null ? (long) (double) start : 0,
						end != null ? (long) (double) end : 0, o.has("active") && o.get("active").getAsBoolean()));
			}
			return out;
		});
	}

	/** Current mayor + minister and their perks. */
	public CompletableFuture<Backend.MayorInfo> mayor() {
		return get("/mayor", 10, body -> {
			JsonObject cur = body.getAsJsonObject().has("current") && body.getAsJsonObject().get("current").isJsonObject()
					? body.getAsJsonObject().getAsJsonObject("current") : null;
			if (cur == null) return Backend.MayorInfo.NONE;
			List<Backend.MayorPerk> perks = new ArrayList<>();
			JsonArray arr = arr(cur, "perks");
			if (arr != null) {
				for (var el : arr) {
					JsonObject p = el.getAsJsonObject();
					perks.add(new Backend.MayorPerk(str(p, "name"), str(p, "description")));
				}
			}
			String minister = str(cur, "minister_name");
			String name = str(cur, "mayor_name");
			return new Backend.MayorInfo(minister != null ? name + " + " + minister : name, List.copyOf(perks));
		});
	}

	public CompletableFuture<List<Backend.BazaarFlip>> bazaarFlips(int limit) {
		return get("/bazaar/flips?limit=" + limit, 10, body -> {
			List<Backend.BazaarFlip> out = new ArrayList<>();
			if (!body.isJsonArray()) return out;
			for (var el : body.getAsJsonArray()) {
				JsonObject o = el.getAsJsonObject();
				out.add(new Backend.BazaarFlip(str(o, "product"), str(o, "name"), num(o, "buy_order_at"), num(o, "sell_offer_at"),
						num(o, "margin_pct"), num(o, "hourly_volume"), num(o, "est_hourly_profit")));
			}
			return out;
		});
	}

	private static double num(JsonObject o, String key) {
		Double d = dbl(o, key);
		return d != null ? d : 0;
	}

	private static Backend.AuctionPrice auctionPrice(String key, JsonObject o) {
		JsonObject lbin = o.has("lbin") && o.get("lbin").isJsonObject() ? o.getAsJsonObject("lbin") : null;
		return new Backend.AuctionPrice(key, dbl(o, "price"), lbin != null ? dbl(lbin, "lbin") : null,
				str(o, "confidence"), dbl(o, "volume_per_day_7d"));
	}

	private <T> CompletableFuture<T> get(String path, int timeoutSeconds, Function<JsonElement, T> parser) {
		return send(path, timeoutSeconds, null, parser);
	}

	private <T> CompletableFuture<T> send(String path, int timeoutSeconds, String postBody, Function<JsonElement, T> parser) {
		String url = BASE + path;
		if (!BackendUrl.isAllowedMarketUrl(url)) {
			return CompletableFuture.failedFuture(new BackendClient.BackendException("refusing non-market URL"));
		}
		if (!hasKey()) {
			status = new BackendClient.Status(false, "no scd.wtf API key set", System.currentTimeMillis());
			return CompletableFuture.failedFuture(new BackendClient.BackendException("no scd.wtf API key set (/scd market key <key>)"));
		}
		if (System.currentTimeMillis() < backoffUntilMs) {
			return CompletableFuture.failedFuture(new BackendClient.BackendException("rate limited - retrying later"));
		}
		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(timeoutSeconds))
				.header("X-API-Key", effectiveKey())
				.header("User-Agent", userAgent)
				.header("Accept", "application/json");
		HttpRequest request = postBody == null ? builder.GET().build()
				: builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(postBody)).build();
		return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenApply(res -> {
					if (res.statusCode() == 429) {
						long retry = res.headers().firstValueAsLong("retry-after").orElse(60);
						backoffUntilMs = System.currentTimeMillis() + retry * 1000;
						ScdLog.warn("scd.wtf rate limit hit - pausing market requests for " + retry + "s");
					}
					if (res.statusCode() != 200) throw new BackendClient.BackendException(detail(res.body(), res.statusCode()));
					T parsed = parser.apply(JsonParser.parseString(res.body()));
					status = new BackendClient.Status(true, "OK", System.currentTimeMillis());
					return parsed;
				})
				.whenComplete((v, err) -> {
					if (err != null) {
						Throwable c = err.getCause() != null ? err.getCause() : err;
						status = new BackendClient.Status(false, c.getMessage() != null ? c.getMessage() : c.getClass().getSimpleName(), System.currentTimeMillis());
					}
				});
	}

	/** Errors are {"detail": "..."}; 401 gets a friendlier hint since it almost always means a bad key. */
	private static String detail(String body, int status) {
		String msg = "HTTP " + status;
		try {
			JsonObject o = JsonParser.parseString(body).getAsJsonObject();
			if (o.has("detail")) msg = o.get("detail").getAsString();
		} catch (RuntimeException ignored) {
			// not JSON
		}
		return status == 401 ? "scd.wtf rejected the API key (" + msg + ")" : msg;
	}

	private static List<Backend.Product> parseProducts(JsonElement body) {
		List<Backend.Product> out = new ArrayList<>();
		if (!body.isJsonArray()) return out;
		for (var el : body.getAsJsonArray()) {
			JsonObject o = el.getAsJsonObject();
			String id = str(o, "product");
			if (id == null) continue;
			Double buy = dbl(o, "instabuy");
			Double sell = dbl(o, "instasell");
			String name = str(o, "name");
			out.add(new Backend.Product(id, name != null ? name : id, buy != null ? buy : 0, sell != null ? sell : 0, null));
		}
		return out;
	}

	private static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
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
		return e != null && !e.isJsonNull() && e.isJsonPrimitive() ? e.getAsDouble() : null;
	}
}
