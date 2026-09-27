package com.scd.client.hypixel;

import com.scd.client.core.EventBus;
import com.scd.client.core.Events;
import com.scd.logic.Text;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One consistent, cleaned snapshot of the two UI surfaces Hypixel uses to publish game state - the
 * sidebar scoreboard and the tab list - taken once per tick and shared by every feature. Previously
 * each feature walked the scoreboard on its own (some several times per tick), each with its own
 * copy of the formatting-code/PUA/NBSP cleanup.
 *
 * Sidebar lines are the classic team trick: each line's owner is an invisible fake player and the
 * visible text is that team's prefix+suffix, so the line is resolved through the team, not the raw
 * owner name. The tab list is only read lazily (it can hold 80 entries) when something asks for it.
 */
public final class GameState {
	private static final Pattern CLOCK_LINE = Pattern.compile("^\\d{1,2}:\\d{2}\\s*(am|pm)\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern AREA_LINE = Pattern.compile("^⏣\\s*(.+)$|^ф\\s*(.+)$");

	private final EventBus bus;
	private List<String> sidebar = List.of();
	private String sidebarTitle = "";
	private List<String> tabList;
	private List<Component> sidebarRaw = List.of();
	private long tabListTick = -1;
	private long tick;
	private String area;
	private boolean inWorld;

	public GameState(EventBus bus) {
		this.bus = bus;
	}

	/** Refreshes the sidebar snapshot; called first thing every client tick. */
	public void tick(long tick) {
		this.tick = tick;
		var mc = Minecraft.getInstance();
		boolean nowInWorld = mc.level != null && mc.player != null;
		if (nowInWorld != inWorld) {
			inWorld = nowInWorld;
			bus.post(new Events.WorldChanged(nowInWorld));
		}
		if (!nowInWorld) {
			sidebar = List.of();
			sidebarTitle = "";
			setArea(null);
			return;
		}
		Scoreboard scoreboard = mc.level.getScoreboard();
		Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
		if (objective == null) {
			sidebar = List.of();
			sidebarTitle = "";
			setArea(null);
			return;
		}
		sidebarTitle = Text.clean(objective.getDisplayName().getString());
		List<PlayerScoreEntry> entries = new ArrayList<>(scoreboard.listPlayerScores(objective));
		// Highest score first = top of the sidebar, matching what the player sees.
		entries.sort(Comparator.comparingInt(PlayerScoreEntry::value).reversed());
		List<String> lines = new ArrayList<>(entries.size());
		List<Component> raw = new ArrayList<>(entries.size());
		for (PlayerScoreEntry entry : entries) {
			String owner = entry.owner();
			var team = scoreboard.getPlayersTeam(owner);
			Component text = team != null ? team.getFormattedName(Component.literal(owner)) : entry.ownerName();
			String clean = Text.clean(text.getString());
			if (!clean.isEmpty()) {
				lines.add(clean);
				raw.add(text);
			}
		}
		sidebar = List.copyOf(lines);
		sidebarRaw = List.copyOf(raw);
		setArea(findArea(lines));
	}

	private String findArea(List<String> lines) {
		for (String line : lines) {
			Matcher m = AREA_LINE.matcher(line);
			if (m.matches()) return (m.group(1) != null ? m.group(1) : m.group(2)).trim();
		}
		// Live-confirmed 2026-09-27: the area glyph is a private-use icon that Text.clean strips, so the
		// line reads just "Village". Hypixel always puts the area directly under the clock line
		// ("3:20am ☽"), which makes that the reliable anchor.
		for (int i = 0; i + 1 < lines.size(); i++) {
			if (CLOCK_LINE.matcher(lines.get(i)).find()) {
				String next = lines.get(i + 1);
				if (!next.startsWith("Purse") && !next.startsWith("Piggy") && !next.isBlank()) return next.trim();
			}
		}
		// Some resource packs replace the ⏣ glyph with a private-use icon that Text.clean strips,
		// leaving just the area name - fall back to a line that exactly matches a known area.
		for (String line : lines) {
			if (KnownAreas.isKnown(line)) return line;
		}
		return null;
	}

	private void setArea(String newArea) {
		if (java.util.Objects.equals(area, newArea)) return;
		String previous = area;
		area = newArea;
		bus.post(new Events.AreaChanged(previous, newArea));
	}

	public List<String> sidebar() {
		return sidebar;
	}

	public String sidebarTitle() {
		return sidebarTitle;
	}

	/** The SkyBlock sub-area from the sidebar's location line ("Void Sepulture"), or null. */
	public String area() {
		return area;
	}

	public boolean isInWorld() {
		return inWorld;
	}

	/** True while the sidebar objective's title is SkyBlock's own. */
	public boolean isOnSkyblock() {
		String upper = sidebarTitle.toUpperCase(Locale.ROOT);
		return upper.contains("SKYBLOCK") || upper.contains("SKIBLOCK");
	}

	public boolean sidebarContains(String fragment) {
		for (String line : sidebar) if (line.contains(fragment)) return true;
		return false;
	}

	public String sidebarFind(Predicate<String> predicate) {
		for (String line : sidebar) if (predicate.test(line)) return line;
		return null;
	}

	/** Cleaned tab-list lines in display order; computed at most once per tick, on demand. */
	/** Sidebar lines as components (same order as {@link #sidebar()}), for colour checks. */
	public List<Component> sidebarRaw() {
		return sidebarRaw;
	}

	/** Tab list footer, cleaned, one entry per line (dungeon blessings, effects...). */
	public List<String> tabFooter() {
		var hud = Minecraft.getInstance().gui.hud;
		if (hud == null) return List.of();
		Component footer = ((com.scd.client.mixin.TabOverlayAccessor) hud.getTabList()).scd$footer();
		if (footer == null) return List.of();
		List<String> out = new ArrayList<>();
		for (String l : footer.getString().split("\n")) {
			String c = Text.clean(l).trim();
			if (!c.isEmpty()) out.add(c);
		}
		return out;
	}

	public List<String> tabList() {
		if (tabList != null && tabListTick == tick) return tabList;
		tabListTick = tick;
		var player = Minecraft.getInstance().player;
		if (player == null || player.connection == null) return tabList = List.of();
		tabList = player.connection.getListedOnlinePlayers().stream()
				.sorted(Comparator.comparingInt(PlayerInfo::getTabListOrder))
				.map(GameState::tabLine)
				.filter(s -> !s.isEmpty())
				.toList();
		return tabList;
	}

	private static String tabLine(PlayerInfo info) {
		Component text = info.getTabListDisplayName();
		if (text == null) {
			var team = info.getTeam();
			String name = info.getProfile().name();
			text = team != null ? team.getFormattedName(Component.literal(name)) : Component.literal(name);
		}
		return Text.clean(text.getString());
	}
}
