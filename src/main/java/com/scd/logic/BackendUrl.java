package com.scd.logic;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * Validates and normalizes the configurable SCD backend URL (any http/https host and port).
 *
 * Note on ports: SCD is a pure client - it only makes outbound requests and never binds or listens
 * on a port. Ports 20, 443 and 8000 are already in use on the owner's machine, so nothing in this
 * project (mod, tooling, any local test server) may ever <i>reserve</i> them; see {@link #RESERVED_PORTS}.
 */
public final class BackendUrl {
	/** Already in use on the owner's machine - never bind/listen on these. Outbound calls to them are fine. */
	public static final Set<Integer> RESERVED_PORTS = Set.of(443, 20, 8000);

	private BackendUrl() {
	}

	/** Normalized URL (no trailing slash, http:// assumed when no scheme) or an error message. */
	public record Result(String url, String error) {
		public boolean ok() {
			return error == null;
		}
	}

	public static Result validate(String raw) {
		String s = raw == null ? "" : raw.trim();
		if (s.matches("(?i)^[a-z][a-z0-9+.-]*:/*$")) return new Result(null, "server URL has no host");
		while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
		if (s.isEmpty()) return new Result(null, "server URL is empty");
		if (!s.contains("://")) s = "http://" + s;
		URI uri;
		try {
			uri = URI.create(s);
		} catch (IllegalArgumentException e) {
			return new Result(null, "invalid server URL \"" + raw + "\"");
		}
		String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
		if (!scheme.equals("http") && !scheme.equals("https")) return new Result(null, "server URL must be http:// or https://");
		if (uri.getHost() == null) return new Result(null, "server URL has no host");
		return new Result(s, null);
	}

	/** Host of the scd.wtf market API - the only host the market API key is ever sent to. */
	public static final String MARKET_HOST = "market.scd.wtf";

	/** True if {@code url} is https on exactly {@link #MARKET_HOST} (so the API key can't leak elsewhere). */
	public static boolean isAllowedMarketUrl(String url) {
		try {
			URI uri = URI.create(url);
			return "https".equalsIgnoreCase(uri.getScheme()) && MARKET_HOST.equalsIgnoreCase(uri.getHost()) && effectivePort(uri) == 443;
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	public static int effectivePort(URI uri) {
		if (uri.getPort() != -1) return uri.getPort();
		return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
	}
}
