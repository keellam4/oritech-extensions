package io.github.keellam4.oritechextensions.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * What a vault holds, kept on its item when it is broken: one entry per slot (an item type and how many), and
 * whether the vault is locked to its item types.
 */
public record VaultContents(List<Slot> slots, boolean locked) {

    /** One slot: a count-1 template of the item, and how many there are. */
    public record Slot(ItemStack item, int count) {

        public static final Slot EMPTY = new Slot(ItemStack.EMPTY, 0);

        public static final Codec<Slot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ItemStack.SINGLE_ITEM_CODEC.optionalFieldOf("item", ItemStack.EMPTY).forGetter(Slot::item),
                Codec.INT.optionalFieldOf("count", 0).forGetter(Slot::count)
        ).apply(instance, Slot::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, Slot> STREAM_CODEC = StreamCodec.composite(
                ItemStack.OPTIONAL_STREAM_CODEC, Slot::item,
                ByteBufCodecs.VAR_INT, Slot::count,
                Slot::new);

        public Slot {
            item = item.isEmpty() ? ItemStack.EMPTY : item.copyWithCount(1);
            count = item.isEmpty() ? 0 : Math.max(0, count);
        }

        public boolean isEmpty() {
            return item.isEmpty();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Slot o && count == o.count && ItemStack.matches(item, o.item);
        }

        @Override
        public int hashCode() {
            return 31 * ItemStack.hashItemAndComponents(item) + count;
        }
    }

    public static final VaultContents EMPTY = new VaultContents(List.of(), false);

    public static final Codec<VaultContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Slot.CODEC.listOf().optionalFieldOf("slots", List.of()).forGetter(VaultContents::slots),
            Codec.BOOL.optionalFieldOf("locked", false).forGetter(VaultContents::locked)
    ).apply(instance, VaultContents::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, VaultContents> STREAM_CODEC = StreamCodec.composite(
            Slot.STREAM_CODEC.apply(ByteBufCodecs.list()), VaultContents::slots,
            ByteBufCodecs.BOOL, VaultContents::locked,
            VaultContents::new);

    public VaultContents {
        slots = List.copyOf(slots);
    }

    public boolean isEmpty() {
        return slots.stream().allMatch(Slot::isEmpty);
    }

    public Slot slot(int index) {
        return index < slots.size() ? slots.get(index) : Slot.EMPTY;
    }
}
