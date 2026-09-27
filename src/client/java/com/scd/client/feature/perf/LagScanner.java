package com.scd.client.feature.perf;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.scd.client.Feature;
import com.scd.client.ScdMod;
import com.scd.client.config.ScdConfig;
import com.scd.client.core.Chat;
import com.scd.client.core.Events;
import com.scd.client.core.LagClock;
import com.scd.client.core.ScdLog;
import com.scd.client.storage.ScdPaths;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Toggleable lag scanner. While on it measures, every second:
 * <ul>
 *     <li>frame times (average, 1% low, worst) and client tick time</li>
 *     <li>CPU time to prepare each entity type, block entity type and particles for rendering
 *     (mixin probes), with how many there are</li>
 *     <li>garbage collections, and SCD's own cost</li>
 * </ul>
 * Frame spikes are logged as hotspots with where you stood and what was expensive at the time, and
 * frame times are aggregated per place (area + 16-block cell) so repeatable lag spots stand out.
 * Everything the probes can't see (terrain, GPU, shaders, other mods) shows as "Other".
 */
public final class LagScanner implements Feature {
	private static final int WINDOW_FRAMES = 4096;
	private static final int MAX_HOTSPOTS = 50;

	public record Line(String label, int count, double msPerFrame) {
	}

	public record Snapshot(double fps, double lowFps, double avgMs, double worstMs, double tickMs, int gcCount, long gcMs,
			double scdMs, double otherMs, List<Line> top, int entities, int particles) {
	}

	public record Hotspot(LocalDateTime at, String place, int x, int y, int z, double frameMs, String causes) {
	}

	private static final class Place {
		long frames;
		double totalMs;
		int spikes;
		double worstMs;
	}

	private ScdMod mod;
	private final double[] frameMs = new double[WINDOW_FRAMES];
	private int frames;
	private long lastFrameNanos;
	private long tickStart;
	private double tickNanos;
	private int ticks;
	private long lastSnapshot;
	private long gcCountBase = -1, gcTimeBase;
	private Snapshot latest;
	private final ArrayDeque<Hotspot> hotspots = new ArrayDeque<>();
	private final Map<String, Place> places = new HashMap<>();
	private final double[] recent = new double[120];
	private int recentPos;
	private double pendingSpikeMs;
	private long lastSpikeLog;

	@Override
	public void init(ScdMod mod) {
		this.mod = mod;
		LagClock.on = config().lagScanner;
		mod.configManager.onChange(() -> setEnabled(config().lagScanner));
		ClientTickEvents.START_CLIENT_TICK.register(mc -> {
			if (LagClock.on) tickStart = System.nanoTime();
		});
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (!LagClock.on || tickStart == 0) return;
			tickNanos += System.nanoTime() - tickStart;
			ticks++;
		});
		mod.bus.subscribe(Events.Tick.class, e -> {
			if (!LagClock.on) return;
			long now = System.nanoTime();
			if (now - lastSnapshot >= 1_000_000_000L) {
				lastSnapshot = now;
				ScdLog.guard("lag snapshot", this::snapshot);
			}
		});
		// Runs once per rendered frame: the interval between calls is the frame time.
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("scd", "lag_frame"), (g, delta) -> onFrame());
		mod.huds.add(new LagHud(mod::config, () -> latest));
	}

	private ScdConfig.Perf config() {
		return mod.config().perf;
	}

	public void setEnabled(boolean on) {
		if (on == LagClock.on) return;
		LagClock.on = on;
		frames = 0;
		lastFrameNanos = 0;
		latest = null;
		LagProbe.drain();
	}

	public Snapshot latest() {
		return latest;
	}

	private void onFrame() {
		if (!LagClock.on) return;
		long now = System.nanoTime();
		if (lastFrameNanos != 0) {
			double ms = (now - lastFrameNanos) / 1e6;
			if (frames < WINDOW_FRAMES) frameMs[frames++] = ms;
			recent[recentPos++ % recent.length] = ms;
			notePlace(ms);
			double median = median();
			if (ms >= config().spikeMs && ms > median * 2.5 && ms > pendingSpikeMs) pendingSpikeMs = ms;
		}
		lastFrameNanos = now;
	}

	private double median() {
		int n = Math.min(recentPos, recent.length);
		if (n < 10) return Double.MAX_VALUE;
		double[] copy = Arrays.copyOf(recent, n);
		Arrays.sort(copy);
		return copy[n / 2];
	}

	private void notePlace(double ms) {
		var p = Minecraft.getInstance().player;
		if (p == null) return;
		String key = placeKey(p.getBlockX(), p.getBlockZ());
		Place place = places.computeIfAbsent(key, k -> new Place());
		place.frames++;
		place.totalMs += ms;
		place.worstMs = Math.max(place.worstMs, ms);
		if (ms >= config().spikeMs) place.spikes++;
	}

	private String placeKey(int x, int z) {
		String area = mod.game.area();
		return (area != null ? area : "?") + " @" + ((x >> 4) * 16 + 8) + "," + ((z >> 4) * 16 + 8);
	}

	private void snapshot() {
		var mc = Minecraft.getInstance();
		int n = frames;
		frames = 0;
		Map<Object, LagProbe.Cost> costs = LagProbe.drain();
		double scdMs = LagClock.scdNanos / 1e6;
		LagClock.scdNanos = 0;
		if (n == 0 || mc.level == null) return;

		double[] f = Arrays.copyOf(frameMs, n);
		double sum = 0, worst = 0;
		for (double v : f) {
			sum += v;
			worst = Math.max(worst, v);
		}
		Arrays.sort(f);
		double avg = sum / n;
		double p99 = f[Math.min(n - 1, (int) Math.floor(n * 0.99))];

		// Counts of what's loaded, so a cost reads "Armor Stand x312".
		Map<Object, Integer> counts = new IdentityHashMap<>();
		int entities = 0;
		for (Entity e : mc.level.entitiesForRendering()) {
			counts.merge(e.getType(), 1, Integer::sum);
			entities++;
		}
		int particles = parseInt(mc.particleEngine.countParticles());

		List<Line> lines = new ArrayList<>();
		double measured = 0;
		for (var e : costs.entrySet()) {
			double perFrame = e.getValue().nanos / 1e6 / n;
			measured += perFrame;
			Object key = e.getKey();
			int count = key == LagKeys.PARTICLES ? particles
					: key instanceof BlockEntityType<?> ? Math.round(e.getValue().calls / (float) n)
					: counts.getOrDefault(key, 0);
			lines.add(new Line(label(key), count, perFrame));
		}
		double scdPerFrame = scdMs / n;
		lines.sort(Comparator.comparingDouble(Line::msPerFrame).reversed());
		double other = Math.max(0, avg - measured - scdPerFrame - (ticks > 0 ? tickNanos / 1e6 / n : 0));

		long gcCount = 0, gcTime = 0;
		for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
			gcCount += Math.max(0, gc.getCollectionCount());
			gcTime += Math.max(0, gc.getCollectionTime());
		}
		int gcDelta = gcCountBase < 0 ? 0 : (int) (gcCount - gcCountBase);
		long gcMs = gcCountBase < 0 ? 0 : gcTime - gcTimeBase;
		gcCountBase = gcCount;
		gcTimeBase = gcTime;

		double tickMs = ticks > 0 ? tickNanos / 1e6 / ticks : 0;
		tickNanos = 0;
		ticks = 0;
		latest = new Snapshot(1000.0 / avg, 1000.0 / p99, avg, worst, tickMs, gcDelta, gcMs, scdPerFrame, other,
				List.copyOf(lines.subList(0, Math.min(8, lines.size()))), entities, particles);

		if (pendingSpikeMs > 0) {
			recordSpike(pendingSpikeMs, gcDelta, gcMs);
			pendingSpikeMs = 0;
		}
	}

	private void recordSpike(double ms, int gcDelta, long gcMs) {
		var p = Minecraft.getInstance().player;
		if (p == null) return;
		StringBuilder causes = new StringBuilder();
		for (Line l : latest.top()) {
			if (causes.length() > 0) causes.append(", ");
			causes.append(l.label()).append(l.count() > 0 ? " x" + l.count() : "").append(String.format(Locale.ROOT, " %.1fms", l.msPerFrame()));
			if (causes.length() > 160) break;
		}
		if (gcDelta > 0) causes.append(causes.length() > 0 ? ", " : "").append("GC x").append(gcDelta).append(" (").append(gcMs).append("ms)");
		Hotspot h = new Hotspot(LocalDateTime.now(), placeKey(p.getBlockX(), p.getBlockZ()), p.getBlockX(), p.getBlockY(), p.getBlockZ(), ms, causes.toString());
		hotspots.addFirst(h);
		while (hotspots.size() > MAX_HOTSPOTS) hotspots.removeLast();
		long now = System.currentTimeMillis();
		if (now - lastSpikeLog > 2000) {
			lastSpikeLog = now;
			ScdLog.info(String.format(Locale.ROOT, "[lag] %.0fms frame at %s (%d %d %d): %s", ms, h.place(), h.x(), h.y(), h.z(), h.causes()));
		}
	}

	static String label(Object key) {
		if (key == LagKeys.PARTICLES) return "Particles";
		if (key instanceof EntityType<?> t) return t.getDescription().getString();
		if (key instanceof BlockEntityType<?> t) {
			var id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(t);
			String name = id != null ? id.getPath().replace('_', ' ') : "block entity";
			return Character.toUpperCase(name.charAt(0)) + name.substring(1) + " (block)";
		}
		return String.valueOf(key);
	}

	private static int parseInt(String s) {
		if (s == null) return 0;
		String digits = s.replaceAll("[^0-9]", "");
		try {
			return digits.isEmpty() ? 0 : Integer.parseInt(digits.length() > 9 ? digits.substring(0, 9) : digits);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	/** Places with at least 5 seconds of frames, worst average frame time first. */
	List<Map.Entry<String, double[]>> worstPlaces(int limit) {
		List<Map.Entry<String, double[]>> out = new ArrayList<>();
		for (var e : places.entrySet()) {
			Place p = e.getValue();
			if (p.frames < 300) continue;
			out.add(Map.entry(e.getKey(), new double[]{p.totalMs / p.frames, p.worstMs, p.spikes, p.frames}));
		}
		out.sort(Comparator.comparingDouble((Map.Entry<String, double[]> e) -> -e.getValue()[0]));
		return out.subList(0, Math.min(limit, out.size()));
	}

	List<Hotspot> hotspots() {
		return List.copyOf(hotspots);
	}

	Path writeReport() throws java.io.IOException {
		StringBuilder sb = new StringBuilder("SCD lag report " + LocalDateTime.now() + "\n\n");
		Snapshot s = latest;
		if (s != null) {
			sb.append(String.format(Locale.ROOT, "FPS %.0f (1%% low %.0f), frame %.1fms avg / %.1fms worst, tick %.2fms, GC %d (%dms), SCD %.2fms/frame%n",
					s.fps(), s.lowFps(), s.avgMs(), s.worstMs(), s.tickMs(), s.gcCount(), s.gcMs(), s.scdMs()));
			sb.append(String.format(Locale.ROOT, "%d entities, %d particles%n%nTop CPU costs (ms per frame):%n", s.entities(), s.particles()));
			for (Line l : s.top()) sb.append(String.format(Locale.ROOT, "  %-32s x%-6d %.2f%n", l.label(), l.count(), l.msPerFrame()));
			sb.append(String.format(Locale.ROOT, "  %-32s        %.2f%n", "Other (terrain, GPU, other mods)", s.otherMs()));
		}
		sb.append("\nLaggiest places (avg frame ms, worst, spikes, frames):\n");
		for (var e : worstPlaces(15)) {
			double[] v = e.getValue();
			sb.append(String.format(Locale.ROOT, "  %-40s %.1f  %.0f  %d  %d%n", e.getKey(), v[0], v[1], (int) v[2], (int) v[3]));
		}
		sb.append("\nSpikes (newest first):\n");
		DateTimeFormatter time = DateTimeFormatter.ofPattern("HH:mm:ss");
		for (Hotspot h : hotspots) {
			sb.append(String.format(Locale.ROOT, "  %s %.0fms %s (%d %d %d): %s%n", h.at().format(time), h.frameMs(), h.place(), h.x(), h.y(), h.z(), h.causes()));
		}
		Path dir = ScdPaths.file("lag");
		Files.createDirectories(dir);
		Path out = dir.resolve("report-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".txt");
		Files.writeString(out, sb.toString());
		return out;
	}

	@Override
	public void registerCommands(LiteralArgumentBuilder<FabricClientCommandSource> root) {
		root.then(ClientCommands.literal("lag")
				.executes(ctx -> status())
				.then(ClientCommands.literal("on").executes(ctx -> toggle(true)))
				.then(ClientCommands.literal("off").executes(ctx -> toggle(false)))
				.then(ClientCommands.literal("places").executes(ctx -> {
					var list = worstPlaces(8);
					if (list.isEmpty()) {
						Chat.info("No place has 5+ seconds of data yet" + (LagClock.on ? "." : " - turn the scanner on with /scd lag on."));
						return 0;
					}
					Chat.info("Laggiest places (average frame time):");
					for (var e : list) {
						double[] v = e.getValue();
						Chat.info(String.format(Locale.ROOT, " %s  %.1fms avg (%.0f fps), worst %.0fms, %d spikes",
								e.getKey(), v[0], 1000 / v[0], v[1], (int) v[2]));
					}
					return 1;
				}))
				.then(ClientCommands.literal("report").executes(ctx -> {
					try {
						Chat.info("Lag report written to " + ScdPaths.root().relativize(writeReport()));
					} catch (Exception e) {
						Chat.error("Could not write the report: " + e.getMessage());
					}
					return 1;
				})));
	}

	private int toggle(boolean on) {
		config().lagScanner = on;
		mod.configManager.save();
		setEnabled(on);
		Chat.info("Lag scanner " + (on ? "on - see the Lag scanner HUD; /scd lag places and /scd lag report for results." : "off."));
		return 1;
	}

	private int status() {
		Snapshot s = latest;
		if (!LagClock.on || s == null) {
			Chat.info("Lag scanner is " + (LagClock.on ? "warming up" : "off (/scd lag on)") + ". " + hotspots.size() + " spikes recorded.");
			return 1;
		}
		Chat.info(String.format(Locale.ROOT, "FPS %.0f (1%% low %.0f) · frame %.1fms · tick %.2fms · GC %d · %d entities · %d particles",
				s.fps(), s.lowFps(), s.avgMs(), s.tickMs(), s.gcCount(), s.entities(), s.particles()));
		for (Line l : s.top()) Chat.info(String.format(Locale.ROOT, " %s x%d: %.2f ms/frame", l.label(), l.count(), l.msPerFrame()));
		Chat.info(String.format(Locale.ROOT, " Other (terrain, GPU, other mods): %.2f ms/frame · SCD: %.2f", s.otherMs(), s.scdMs()));
		return 1;
	}
}
