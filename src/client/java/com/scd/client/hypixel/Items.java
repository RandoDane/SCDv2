package com.scd.client.hypixel;

import com.scd.logic.Text;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

/**
 * SkyBlock item identity. Every SkyBlock item carries its internal id in custom_data
 * ({id:"..."}, or the older nested ExtraAttributes.id). Two families share one id and need a second
 * lookup to become a real Bazaar product id:
 * <ul>
 *   <li>ENCHANTED_BOOK -> ENCHANTMENT_&lt;NAME&gt;_&lt;LEVEL&gt; from its single stored enchantment;</li>
 *   <li>ATTRIBUTE_SHARD -> the shard product, via the backend's attribute->shard table (the two
 *       names are often unrelated words).</li>
 * </ul>
 */
public final class Items {
	private Items() {
	}

	/** Bazaar/auction product id, or null for a non-SkyBlock item. {@code shardLookup} may return null. */
	public static String skyblockId(ItemStack stack, Function<String, String> shardLookup) {
		if (stack == null || stack.isEmpty()) return null;
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) return null;
		CompoundTag tag = data.copyTag();
		String id = tag.getString("id")
				.or(() -> tag.getCompound("ExtraAttributes").flatMap(extra -> extra.getString("id")))
				.orElse(null);
		if ("ENCHANTED_BOOK".equals(id)) return enchantedBookId(tag).orElse(id);
		if ("ATTRIBUTE_SHARD".equals(id)) return shardId(tag, shardLookup).orElse(id);
		return id;
	}

	private static Optional<String> enchantedBookId(CompoundTag tag) {
		return tag.getCompound("enchantments").flatMap(enchants -> {
			if (enchants.keySet().size() != 1) return Optional.<String>empty();
			String name = enchants.keySet().iterator().next();
			return enchants.getInt(name).map(level -> "ENCHANTMENT_" + name.toUpperCase(Locale.ROOT) + "_" + level);
		});
	}

	private static Optional<String> shardId(CompoundTag tag, Function<String, String> shardLookup) {
		return tag.getCompound("attributes").flatMap(attrs -> {
			if (attrs.keySet().size() != 1 || shardLookup == null) return Optional.<String>empty();
			return Optional.ofNullable(shardLookup.apply(attrs.keySet().iterator().next()));
		});
	}

	/** Full custom_data as SNBT text, for diagnostics. */
	public static String rawCustomData(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null ? data.copyTag().toString() : null;
	}

	/** Cleaned lore lines (formatting codes, icon glyphs and NBSPs removed). */
	public static List<String> lore(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore == null) return List.of();
		return lore.lines().stream().map(c -> Text.clean(c.getString())).toList();
	}

	public static String name(ItemStack stack) {
		return Text.clean(stack.getHoverName().getString());
	}
}
