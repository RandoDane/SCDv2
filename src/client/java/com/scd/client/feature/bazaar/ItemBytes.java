package com.scd.client.feature.bazaar;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

/**
 * Rebuilds Hypixel's API "item_bytes" (base64 of gzipped NBT {i:[{id,Count,Damage,tag:{ExtraAttributes,display}}]})
 * from a client ItemStack. The client's custom_data component <i>is</i> the old ExtraAttributes
 * compound - stars, scrolls, enchants, potato books, recombobulator, gems, reforge, pet info - so the
 * scd.wtf valuation endpoint can price the exact item, add-ons included. Verified against the live
 * endpoint with a 5-star, 15-book Hyperion.
 */
final class ItemBytes {
	private ItemBytes() {
	}

	static String encode(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) return null;
		CompoundTag display = new CompoundTag();
		display.putString("Name", stack.getHoverName().getString());
		CompoundTag tag = new CompoundTag();
		tag.put("ExtraAttributes", data.copyTag());
		tag.put("display", display);
		CompoundTag item = new CompoundTag();
		item.putShort("id", (short) 1);
		item.putByte("Count", (byte) Math.max(1, Math.min(127, stack.getCount())));
		item.putShort("Damage", (short) 0);
		item.put("tag", tag);
		ListTag list = new ListTag();
		list.add(item);
		CompoundTag root = new CompoundTag();
		root.put("i", list);
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			NbtIo.writeCompressed(root, out);
			return Base64.getEncoder().encodeToString(out.toByteArray());
		} catch (IOException e) {
			return null;
		}
	}
}
