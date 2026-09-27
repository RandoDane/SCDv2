package com.scd.client.feature.slayer;

import com.scd.client.core.EventBus;
import com.scd.client.hypixel.GameState;
import com.scd.client.hypixel.Players;
import com.scd.logic.Text;
import com.scd.logic.slayer.Nameplate;
import com.scd.logic.slayer.SlayerTier;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The local player's Slayer quest as a state machine driven by the sidebar (authoritative, and
 * available before any boss exists), with the boss entity re-located every tick once spawned.
 *
 * Behaviours carried over from live testing of 1.x:
 * <ul>
 *   <li>Area-restricted types (Enderman/Blaze/Spider) are dormant outside their areas.</li>
 *   <li>Tier V Tarantula: the sidebar briefly drops "Slay the boss!" when Broodfather dies and
 *       Conjoined Brood takes over - not a kill. The fight only ends once Conjoined Brood has been
 *       seen (SkyHanni's approach), however long the transition takes.</li>
 *   <li>Boss lookup prefers the "Spawned by: you" tag, then the entity already tracked (so a
 *       stranger's boss can't take over during a teleport), then the closest match.</li>
 *   <li>The hunt timer pauses after N seconds without any nearby mob losing health, so AFK time
 *       doesn't inflate spawn-time stats.</li>
 *   <li>HP threshold crossings raise short-lived alerts that ability cues read.</li>
 * </ul>
 */
public final class SlayerTracker {
	private static final Pattern TIER_SUFFIX = Pattern.compile("\\b(V|IV|III|II|I)\\s*$");
	private static final String BOSS_SPAWNED_LINE = "Slay the boss!";
	private static final long COCOON_RESPAWN_MS = 6_000;
	private static final long ALERT_MS = 6_000;
	private static final long KILLED_FLASH_MS = 5_000;
	private static final long LOOT_WINDOW_MS = 20_000;

	/** HP-fraction thresholds that trigger a one-shot alert id when crossed downward. */
	private static final Map<String, Float> THRESHOLDS = Map.of(
			"wolf_pups", 0.5f,
			"spider_egg_66", 0.66f, "spider_egg_33", 0.33f,
			"vamp_mania_75", 0.75f, "vamp_mania_50", 0.5f, "vamp_mania_40", 0.4f,
			"ender_beam_5_6", 5f / 6f, "ender_beam_1_2", 0.5f, "ender_beam_1_6", 1f / 6f);

	private final EventBus bus;
	private final GameState game;

	private SlayerQuest quest;
	private LivingEntity boss;
	private long fightStartMs;
	private double maxHpSeen;
	private Float lastHpFrac;
	private boolean seenConjoinedBrood;
	private boolean lastWasConjoined;
	private long cocoonUntilMs;
	private SlayerType lastEndedType;
	private SlayerType lastActiveType;
	private long killedFlashUntilMs;
	private long lootUntilMs;
	/** Adaptive hunt timer (see HuntClock): quest XP progress and your own hits/item uses. */
	private final com.scd.logic.slayer.HuntClock hunt = new com.scd.logic.slayer.HuntClock();
	private long lastQuestXp = -1;
	private static final Pattern QUEST_XP = Pattern.compile("\\(([\\d,]+)/([\\d,.kKmM]+)\\) Combat XP");
	private final Map<String, Long> alerts = new HashMap<>();
	private final Set<UUID> knownMinibosses = new HashSet<>();

	SlayerTracker(EventBus bus, GameState game) {
		this.bus = bus;
		this.game = game;
	}

	/** You hit a mob or used an item: keeps the hunt clock running through long fights. */
	void onPlayerAction() {
		hunt.action(System.currentTimeMillis());
	}

	/** Quest progress from the sidebar ("(1,240/2,400) Combat XP"), or -1. */
	private long questXp() {
		for (String line : game.sidebar()) {
			Matcher m = QUEST_XP.matcher(line);
			if (m.find()) {
				try {
					return Long.parseLong(m.group(1).replace(",", ""));
				} catch (NumberFormatException e) {
					return -1;
				}
			}
		}
		return -1;
	}

	/** Reads the quest off the current sidebar snapshot, or null. */
	SlayerQuest readQuest() {
		SlayerType type = null;
		String tier = null;
		boolean spawned = false;
		for (String line : game.sidebar()) {
			if (line.contains(BOSS_SPAWNED_LINE)) {
				spawned = true;
				continue;
			}
			SlayerType t = SlayerType.fromBossText(line);
			if (t != null) {
				type = t;
				Matcher m = TIER_SUFFIX.matcher(line);
				tier = m.find() ? SlayerTier.normalize(m.group(1)) : null;
			}
		}
		return type != null ? new SlayerQuest(type, tier, spawned) : null;
	}

	public boolean inAllowedArea(SlayerType type) {
		if (!type.isAreaRestricted()) return true;
		for (String line : game.sidebar()) {
			for (String area : type.areas()) if (line.equalsIgnoreCase(area)) return true;
		}
		String area = game.area();
		if (area != null) for (String a : type.areas()) if (area.equalsIgnoreCase(a)) return true;
		return false;
	}

	void tick(boolean active) {
		var mc = Minecraft.getInstance();
		SlayerQuest previous = quest;
		quest = active && mc.player != null ? readQuest() : null;
		if (quest != null && !inAllowedArea(quest.type())) quest = null;

		boolean broodExpected = previous != null && previous.type() == SlayerType.SPIDER && "V".equals(previous.tier())
				&& previous.bossSpawned() && !seenConjoinedBrood;
		if (broodExpected && (quest == null || !quest.bossSpawned())) {
			quest = new SlayerQuest(previous.type(), previous.tier(), true);
		}
		if (quest != null) lastActiveType = quest.type();

		if (previous == null && quest != null) {
			hunt.start(quest.type().name(), System.currentTimeMillis());
			lastQuestXp = -1;
			bus.post(new SlayerEvents.QuestStarted(quest));
		}

		boolean wasSpawned = previous != null && previous.bossSpawned();
		boolean isSpawned = quest != null && quest.bossSpawned();
		if (isSpawned && !wasSpawned) {
			hunt.tick(System.currentTimeMillis());
			long huntMs = hunt.activeMs();
			hunt.stop();
			fightStartMs = System.currentTimeMillis();
			maxHpSeen = 0;
			lastHpFrac = null;
			seenConjoinedBrood = false;
			lastWasConjoined = false;
			alerts.clear();
			bus.post(new SlayerEvents.BossSpawned(quest, huntMs));
		}
		if (wasSpawned && !isSpawned) {
			long fightMs = System.currentTimeMillis() - fightStartMs;
			lastEndedType = previous.type();
			killedFlashUntilMs = System.currentTimeMillis() + KILLED_FLASH_MS;
			lootUntilMs = System.currentTimeMillis() + LOOT_WINDOW_MS;
			boss = null;
			bus.post(new SlayerEvents.BossKilled(previous, fightMs));
		}

		if (quest == null || mc.level == null || mc.player == null) {
			boss = null;
			knownMinibosses.clear();
			return;
		}
		scanMinibosses(mc);
		if (!isSpawned) {
			boss = null;
			updateHunt();
			return;
		}
		trackBoss(mc);
	}

	private void trackBoss(Minecraft mc) {
		LivingEntity found = BossLocator.owned(quest.type(), Players.selfName());
		if (found == null && boss != null && boss.isAlive() && BossLocator.isBossNameplate(boss, quest.type())
				&& boss.distanceTo(mc.player) <= BossLocator.SCAN_RADIUS * 2) {
			found = boss;
		}
		if (found == null) found = BossLocator.closest(quest.type());
		if (found != null) {
			boss = found;
		} else if (boss != null && (!boss.isAlive() || boss.distanceTo(mc.player) > BossLocator.SCAN_RADIUS * 2)) {
			boss = null;
		}
		if (boss == null) return;

		String name = BossLocator.name(boss);
		Nameplate.Health hp = Nameplate.health(name);
		if (hp != null) maxHpSeen = Math.max(maxHpSeen, hp.max() != null ? hp.max() : hp.current());
		boolean conjoined = name != null && name.contains("Conjoined Brood");
		if (conjoined && !lastWasConjoined) alert("spider_conjoined_transition");
		lastWasConjoined = conjoined;
		if (conjoined) seenConjoinedBrood = true;

		Float frac = hpFraction();
		if (frac != null) {
			if (lastHpFrac != null) {
				for (var e : THRESHOLDS.entrySet()) {
					if (lastHpFrac > e.getValue() && frac <= e.getValue()) alert(e.getKey());
				}
			}
			lastHpFrac = frac;
		}
	}

	private void scanMinibosses(Minecraft mc) {
		Set<UUID> alive = new HashSet<>();
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof LivingEntity l) || e instanceof ArmorStand || !l.isAlive()) continue;
			if (l.distanceTo(mc.player) > BossLocator.SCAN_RADIUS) continue;
			String name = BossLocator.name(e);
			if (name == null || Nameplate.isDead(name)) continue;
			Minibosses.Entry match = Minibosses.match(name);
			if (match == null || match.type() != quest.type()) continue;
			alive.add(l.getUUID());
			if (!knownMinibosses.contains(l.getUUID())) bus.post(new SlayerEvents.MinibossSpawned(match, l));
		}
		knownMinibosses.retainAll(alive);
		knownMinibosses.addAll(alive);
	}

	/** Quest XP going up is your kill; the clock counts active time only (see HuntClock). */
	private void updateHunt() {
		long now = System.currentTimeMillis();
		long xp = questXp();
		if (xp >= 0) {
			if (lastQuestXp >= 0 && xp > lastQuestXp) hunt.progress(now);
			lastQuestXp = xp;
		}
		hunt.tick(now);
	}

	private void alert(String id) {
		alerts.put(id, System.currentTimeMillis() + ALERT_MS);
	}

	void onCocoon() {
		cocoonUntilMs = System.currentTimeMillis() + COCOON_RESPAWN_MS;
	}

	void reset() {
		quest = null;
		boss = null;
		knownMinibosses.clear();
		alerts.clear();
	}

	// ---- queries -------------------------------------------------------------------------------

	public SlayerQuest quest() {
		return quest;
	}

	public LivingEntity boss() {
		return boss;
	}

	public boolean isAlertActive(String id) {
		Long until = alerts.get(id);
		return until != null && System.currentTimeMillis() < until;
	}

	public long cocoonRemainingMs() {
		return Math.max(0, cocoonUntilMs - System.currentTimeMillis());
	}

	public long fightElapsedMs() {
		return quest != null && quest.bossSpawned() ? System.currentTimeMillis() - fightStartMs : 0;
	}

	public long huntElapsedMs() {
		return quest == null ? 0 : hunt.activeMs();
	}

	public boolean isHuntPaused() {
		return quest != null && !quest.bossSpawned() && hunt.paused(System.currentTimeMillis());
	}

	/** Current idle cut-off learned from your pace, for display. */
	public long huntWindowMs() {
		return hunt.window();
	}

	public Nameplate.Health health() {
		return boss != null ? Nameplate.health(BossLocator.name(boss)) : null;
	}

	/** Current HP / best known max (the nameplate's own max, else the highest HP seen this fight). */
	public Float hpFraction() {
		Nameplate.Health hp = health();
		if (hp == null) return null;
		double max = hp.max() != null ? hp.max() : maxHpSeen;
		return max > 0 ? (float) Math.max(0, Math.min(1, hp.current() / max)) : null;
	}

	public Double maxHp() {
		Nameplate.Health hp = health();
		if (hp != null && hp.max() != null) return hp.max();
		return maxHpSeen > 0 ? maxHpSeen : null;
	}

	public Nameplate.Shield shield() {
		return boss != null ? Nameplate.shield(BossLocator.name(boss)) : Nameplate.Shield.NONE;
	}

	/** Type of the fight that just ended, during the short post-kill "Killed" flash. */
	public SlayerType justKilled() {
		return System.currentTimeMillis() < killedFlashUntilMs ? lastEndedType : null;
	}

	/** Active quest type, or the just-finished one during the loot pickup window. */
	public SlayerType typeForLoot() {
		if (quest != null) return quest.type();
		return System.currentTimeMillis() < lootUntilMs ? lastEndedType : null;
	}

	public SlayerType lastActiveType() {
		return lastActiveType;
	}

	/** Short phase badge for a type in the settings list. */
	public String phase(SlayerType type) {
		if (quest != null && quest.type() == type) return quest.bossSpawned() ? "Fighting" : "Hunting";
		if (type == justKilled()) return "Killed";
		return null;
	}

	/** One-shot snapshot for /scd debug slayer. */
	public List<String> describe() {
		List<String> out = new ArrayList<>();
		out.add("quest: " + (quest != null ? quest.label() + " spawned=" + quest.bossSpawned() : "none"));
		out.add("boss: " + (boss != null ? "\"" + BossLocator.name(boss) + "\" hp=" + health() + " frac=" + hpFraction() : "none"));
		out.add("fight=" + fightElapsedMs() + "ms hunt=" + huntElapsedMs() + "ms paused=" + isHuntPaused()
				+ " cocoon=" + cocoonRemainingMs() + "ms minibosses=" + knownMinibosses.size());
		for (SlayerType t : SlayerType.values()) {
			if (t.isAreaRestricted()) out.add(t.displayName() + " area: " + (inAllowedArea(t) ? "PASS" : "FAIL") + " (area=" + game.area() + ")");
		}
		return out;
	}

	static String clean(String s) {
		return Text.clean(s);
	}
}
