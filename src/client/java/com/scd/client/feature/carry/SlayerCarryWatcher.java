package com.scd.client.feature.carry;

import com.scd.client.feature.slayer.BossLocator;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Watches each active Slayer carry's own boss - the one tagged "Spawned by: &lt;customer&gt;" -
 * independent of the local player's quest. There is no scoreboard view into someone else's quest,
 * so a kill is inferred from the entity lifecycle: once found, losing it for more than 3s counts
 * as a kill; a different entity appearing under the same tag first means a fast respawn, so the
 * old one is credited immediately.
 */
final class SlayerCarryWatcher {
	private static final long LOST_GRACE_MS = 3_000;

	private record Tracked(int entityId, long startMs, long lastSeenMs) {
	}

	private final Map<Long, Tracked> tracked = new HashMap<>();
	private final BiConsumer<Carry, Long> onKill;

	SlayerCarryWatcher(BiConsumer<Carry, Long> onKill) {
		this.onKill = onKill;
	}

	void tick(List<Carry> active) {
		if (Minecraft.getInstance().level == null) {
			tracked.clear();
			return;
		}
		long now = System.currentTimeMillis();
		Set<Long> ids = new HashSet<>();
		for (Carry c : active) {
			if (c.kind != Carry.Kind.SLAYER || c.type() == null) continue;
			ids.add(c.id);
			LivingEntity boss = BossLocator.owned(c.type(), c.customer);
			Tracked t = tracked.get(c.id);
			if (boss != null && boss.isAlive()) {
				if (t == null) {
					tracked.put(c.id, new Tracked(boss.getId(), now, now));
				} else if (t.entityId() == boss.getId()) {
					tracked.put(c.id, new Tracked(t.entityId(), t.startMs(), now));
				} else {
					onKill.accept(c, now - t.startMs());
					tracked.put(c.id, new Tracked(boss.getId(), now, now));
				}
			} else if (t != null && now - t.lastSeenMs() > LOST_GRACE_MS) {
				tracked.remove(c.id);
				onKill.accept(c, t.lastSeenMs() - t.startMs());
			}
		}
		tracked.keySet().retainAll(ids);
	}

	String describe(Carry c) {
		LivingEntity boss = c.type() != null ? BossLocator.owned(c.type(), c.customer) : null;
		Tracked t = tracked.get(c.id);
		return (boss != null ? "boss #" + boss.getId() + " \"" + BossLocator.name(boss) + "\"" : "no \"Spawned by: " + c.customer + "\" boss nearby")
				+ " | " + (t != null ? "tracking #" + t.entityId() + " for " + (System.currentTimeMillis() - t.startMs()) + "ms" : "not tracking");
	}
}
