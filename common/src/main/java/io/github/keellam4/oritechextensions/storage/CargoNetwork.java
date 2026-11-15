package io.github.keellam4.oritechextensions.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * The item vaults a Cargo Controller reaches: every item vault connected to it through touching vaults (a Cargo
 * Wall). Fluid Vaults and other controllers don't join.
 * <p>
 * Items go to the vault that already holds them first, then to a vault with room for a new item type, so one full
 * window never jams a pipe.
 */
public final class CargoNetwork {

    /** The most vaults one controller links, so a giant wall can't stall the server. */
    public static final int MAX_VAULTS = 1024;

    public static final CargoNetwork EMPTY = new CargoNetwork(List.of());

    private final List<CargoVaultBlockEntity> vaults;

    private CargoNetwork(List<CargoVaultBlockEntity> vaults) {
        this.vaults = vaults;
    }

    public static CargoNetwork discover(Level level, BlockPos controller) {
        var found = new ArrayList<CargoVaultBlockEntity>();
        var seen = new HashSet<BlockPos>();
        var queue = new ArrayDeque<BlockPos>();
        seen.add(controller);
        queue.add(controller);
        while (!queue.isEmpty() && found.size() < MAX_VAULTS) {
            var pos = queue.poll();
            for (var direction : Direction.values()) {
                var next = pos.relative(direction);
                if (!seen.add(next) || !level.isLoaded(next)) continue;
                if (level.getBlockEntity(next) instanceof CargoVaultBlockEntity vault && !(vault instanceof FluidVaultBlockEntity)) {
                    found.add(vault);
                    queue.add(next);
                }
            }
        }
        return new CargoNetwork(List.copyOf(found));
    }

    public List<CargoVaultBlockEntity> vaults() {
        return vaults;
    }

    public boolean isValid() {
        for (var vault : vaults) {
            if (vault.isRemoved()) return false;
        }
        return true;
    }

    /** Puts {@code stack} into the network. Returns how many went in. */
    public int insert(ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return 0;
        int remaining = stack.getCount();
        // Vaults that already hold this item (or are locked to it) first...
        for (var vault : vaults) {
            if (remaining <= 0) break;
            if (vault.slotOf(stack) < 0) continue;
            remaining -= vault.insert(stack.copyWithCount(remaining), simulate);
        }
        // ...then any vault with room for a new item type.
        for (var vault : vaults) {
            if (remaining <= 0) break;
            if (vault.slotOf(stack) >= 0 || !vault.accepts(stack)) continue;
            int moved = vault.insert(stack.copyWithCount(remaining), simulate);
            remaining -= moved;
            if (simulate && moved > 0) break; // a simulated new type would otherwise land in every empty vault
        }
        return stack.getCount() - remaining;
    }

    /** Takes up to {@code amount} of {@code template}'s item out of the network (from as many vaults as needed). */
    public ItemStack extract(ItemStack template, int amount, boolean simulate) {
        if (template.isEmpty() || amount <= 0) return ItemStack.EMPTY;
        int wanted = Math.min(amount, template.getMaxStackSize());
        int taken = 0;
        for (var vault : vaults) {
            if (taken >= wanted) break;
            int slot = vault.slotOf(template);
            if (slot < 0) continue;
            taken += vault.extract(slot, wanted - taken, simulate).getCount();
        }
        return taken <= 0 ? ItemStack.EMPTY : template.copyWithCount(taken);
    }

    /** Everything in the network: one entry per item type with its total, biggest first. */
    public List<Entry> contents() {
        var totals = new java.util.LinkedHashMap<Key, long[]>();
        for (var vault : vaults) {
            for (int i = 0; i < vault.slots(); i++) {
                var item = vault.item(i);
                int count = vault.count(i);
                if (item.isEmpty() || count <= 0) continue;
                totals.computeIfAbsent(new Key(item.copyWithCount(1)), key -> new long[1])[0] += count;
            }
        }
        var entries = new ArrayList<Entry>(totals.size());
        totals.forEach((key, total) -> entries.add(new Entry(key.item(), total[0])));
        entries.sort((a, b) -> Long.compare(b.count(), a.count()));
        return entries;
    }

    /** An item type as a map key: same item and components. */
    private record Key(ItemStack item) {
        @Override
        public boolean equals(Object other) {
            return other instanceof Key key && ItemStack.isSameItemSameComponents(item, key.item);
        }

        @Override
        public int hashCode() {
            return ItemStack.hashItemAndComponents(item);
        }
    }

    /** One item type in the network and how many there are in total. */
    public record Entry(ItemStack item, long count) {
        @Override
        public boolean equals(Object other) {
            return other instanceof Entry entry && count == entry.count && ItemStack.isSameItemSameComponents(item, entry.item);
        }

        @Override
        public int hashCode() {
            return 31 * ItemStack.hashItemAndComponents(item) + Long.hashCode(count);
        }
    }
}
