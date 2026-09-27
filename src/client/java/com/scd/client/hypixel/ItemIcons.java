package com.scd.client.hypixel;

import com.google.common.collect.HashMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.scd.client.net.Backend;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Best-effort ItemStack for a backend icon: a textured player head when a skin is known, else the
 * modern item for the 1.8-era material name. Results are cached - building a head every frame would
 * make the skin cache miss constantly (the 1.x "flashing heads" bug), and the profile UUID is
 * derived from the skin so the same head always resolves to the same identity.
 */
public final class ItemIcons {
	private static final Map<Backend.Icon, ItemStack> CACHE = new ConcurrentHashMap<>();

	private ItemIcons() {
	}

	public static ItemStack of(Backend.Icon icon) {
		if (icon == null) return ItemStack.EMPTY;
		return CACHE.computeIfAbsent(icon, ItemIcons::build);
	}

	private static ItemStack build(Backend.Icon icon) {
		if (icon.skinValue() != null) {
			// authlib's PropertyMap is immutable once wrapped - populate the backing map first.
			HashMultimap<String, Property> backing = HashMultimap.create();
			backing.put("textures", new Property("textures", icon.skinValue(), icon.skinSignature()));
			UUID id = UUID.nameUUIDFromBytes(icon.skinValue().getBytes(StandardCharsets.UTF_8));
			ItemStack head = new ItemStack(Items.PLAYER_HEAD);
			head.set(DataComponents.PROFILE, ResolvableProfile.createResolved(new GameProfile(id, "scd_icon", new PropertyMap(backing))));
			return head;
		}
		Item item = LegacyMaterials.resolve(icon.material(), icon.durability());
		return item != null ? new ItemStack(item) : ItemStack.EMPTY;
	}
}
