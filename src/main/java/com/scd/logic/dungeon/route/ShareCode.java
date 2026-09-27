package com.scd.logic.dungeon.route;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Chat-pasteable room route: {@code SCDR1:} + base64(gzip({"room": name, "steps": [...]})). The
 * step array is SecretRoutes' format, so a code decodes straight into a routes.json entry.
 */
public final class ShareCode {
	public static final String PREFIX = "SCDR1:";

	public record Decoded(String room, List<RouteStep> steps) {
	}

	private ShareCode() {
	}

	public static String encode(String room, List<RouteStep> steps) {
		JsonObject o = new JsonObject();
		o.addProperty("room", room);
		o.add("steps", RoutePack.writeSteps(steps));
		byte[] json = o.toString().getBytes(StandardCharsets.UTF_8);
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (GZIPOutputStream gz = new GZIPOutputStream(bytes)) {
			gz.write(json);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		return PREFIX + Base64.getEncoder().withoutPadding().encodeToString(bytes.toByteArray());
	}

	/** @throws IllegalArgumentException if the code is malformed */
	public static Decoded decode(String code) {
		String body = code.trim();
		if (!body.startsWith(PREFIX)) throw new IllegalArgumentException("not an SCD route code");
		try {
			byte[] gz = Base64.getDecoder().decode(body.substring(PREFIX.length()));
			byte[] json;
			try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(gz))) {
				json = in.readNBytes(1 << 20);
			}
			JsonObject o = JsonParser.parseString(new String(json, StandardCharsets.UTF_8)).getAsJsonObject();
			JsonArray steps = o.getAsJsonArray("steps");
			return new Decoded(o.get("room").getAsString(), RoutePack.readSteps(steps));
		} catch (IOException | RuntimeException e) {
			throw new IllegalArgumentException("corrupt route code: " + e.getMessage(), e);
		}
	}
}
