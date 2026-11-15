package io.github.keellam4.oritechextensions.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/** What a Cargo Vault holds, kept on its item when it is broken: one item type (count 1 template), how many, and the lock. */
public record VaultContents(ItemStack item, int count, boolean locked) {

    public static final VaultContents EMPTY = new VaultContents(ItemStack.EMPTY, 0, false);

    public static final Codec<VaultContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ItemStack.SINGLE_ITEM_CODEC.optionalFieldOf("item", ItemStack.EMPTY).forGetter(VaultContents::item),
            Codec.INT.optionalFieldOf("count", 0).forGetter(VaultContents::count),
            Codec.BOOL.optionalFieldOf("locked", false).forGetter(VaultContents::locked)
    ).apply(instance, VaultContents::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, VaultContents> STREAM_CODEC = StreamCodec.composite(
            ItemStack.OPTIONAL_STREAM_CODEC, VaultContents::item,
            ByteBufCodecs.VAR_INT, VaultContents::count,
            ByteBufCodecs.BOOL, VaultContents::locked,
            VaultContents::new);

    public VaultContents {
        item = item.isEmpty() ? ItemStack.EMPTY : item.copyWithCount(1);
        count = item.isEmpty() ? 0 : Math.max(0, count);
    }

    public boolean isEmpty() {
        return item.isEmpty();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof VaultContents o && count == o.count && locked == o.locked && ItemStack.matches(item, o.item);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * ItemStack.hashItemAndComponents(item) + count) + (locked ? 1 : 0);
    }
}
