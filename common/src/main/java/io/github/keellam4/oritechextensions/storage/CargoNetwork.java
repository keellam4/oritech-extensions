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
 * The vaults a Cargo Controller reaches: every vault connected to it through touching vaults (a Cargo Wall).
 * Item vaults and Fluid Vaults are kept apart: items go to item vaults, fluids to Fluid Vaults. Other controllers
 * don't join.
 * <p>
 * Items go to the vault that already holds them first, then to a vault with room for a new item type, so one full
 * window never jams a pipe.
 */
public final class CargoNetwork {

    /** The most vaults one controller links, so a giant wall can't stall the server. */
    public static final int MAX_VAULTS = 1024;

    public static final CargoNetwork EMPTY = new CargoNetwork(List.of(), List.of());

    private final List<CargoVaultBlockEntity> vaults;
    private final List<NetworkTank> tanks;

    private CargoNetwork(List<CargoVaultBlockEntity> vaults, List<NetworkTank> tanks) {
        this.vaults = vaults;
        this.tanks = tanks;
    }

    public static CargoNetwork discover(Level level, BlockPos controller) {
        var found = new ArrayList<CargoVaultBlockEntity>();
        var tanks = new ArrayList<NetworkTank>();
        var seen = new HashSet<BlockPos>();
        var queue = new ArrayDeque<BlockPos>();
        seen.add(controller);
        queue.add(controller);
        while (!queue.isEmpty() && found.size() + tanks.size() < MAX_VAULTS) {
            var pos = queue.poll();
            for (var direction : Direction.values()) {
                var next = pos.relative(direction);
                if (!seen.add(next) || !level.isLoaded(next)) continue;
                if (level.getBlockEntity(next) instanceof FluidVaultBlockEntity tank) {
                    tanks.add(tank);
                    queue.add(next);
                } else if (level.getBlockEntity(next) instanceof SiloControllerBlockEntity silo) {
                    // A Fluid Silo joins as one big tank; the network doesn't spread through it.
                    tanks.add(silo);
                } else if (level.getBlockEntity(next) instanceof CargoVaultBlockEntity vault) {
                    found.add(vault);
                    queue.add(next);
                }
            }
        }
        return new CargoNetwork(List.copyOf(found), List.copyOf(tanks));
    }

    public List<CargoVaultBlockEntity> vaults() {
        return vaults;
    }

    public List<NetworkTank> tanks() {
        return tanks;
    }

    public int size() {
        return vaults.size() + tanks.size();
    }

    public boolean isValid() {
        for (var vault : vaults) {
            if (vault.isRemoved()) return false;
        }
        for (var tank : tanks) {
            if (tank.isRemoved()) return false;
        }
        return true;
    }

    // ---------------------------------------------------------------- fluids

    /** Fills the network's Fluid Vaults and Silos: the one holding this fluid first, then an empty one. Returns millibuckets taken. */
    public long fill(net.minecraft.world.level.material.Fluid fluid, long max, boolean simulate) {
        long remaining = max;
        for (var tank : tanks) {
            if (remaining <= 0) break;
            if (tank.holds(fluid)) remaining -= tank.fill(fluid, remaining, simulate);
        }
        for (var tank : tanks) {
            if (remaining <= 0) break;
            if (!tank.holds(fluid)) remaining -= tank.fill(fluid, remaining, simulate);
        }
        return max - remaining;
    }

    /** Drains up to {@code max} millibuckets of {@code fluid} (or of the first fluid found, when null) from the network. */
    public FluidAmount drain(@org.jetbrains.annotations.Nullable net.minecraft.world.level.material.Fluid fluid, long max, boolean simulate) {
        net.minecraft.world.level.material.Fluid wanted = fluid;
        long taken = 0;
        for (var tank : tanks) {
            if (taken >= max) break;
            var drained = tank.drain(wanted, max - taken, simulate);
            if (drained.isEmpty()) continue;
            wanted = drained.fluid();
            taken += drained.amount();
        }
        return taken <= 0 ? FluidAmount.EMPTY : new FluidAmount(wanted, taken);
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
        totals.forEach((key, total) -> entries.add(new Entry(key.item(), total[0], net.minecraft.world.level.material.Fluids.EMPTY)));
        var fluids = new java.util.LinkedHashMap<net.minecraft.world.level.material.Fluid, long[]>();
        for (var tank : tanks) {
            for (var held : tank.fluids()) fluids.computeIfAbsent(held.fluid(), f -> new long[1])[0] += held.amount();
        }
        fluids.forEach((fluid, total) -> entries.add(new Entry(ItemStack.EMPTY, total[0], fluid)));
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

    /**
     * One item type in the network and how many there are in total; or, when {@code fluid} isn't empty, one fluid and
     * how many millibuckets.
     */
    public record Entry(ItemStack item, long count, net.minecraft.world.level.material.Fluid fluid) {

        public boolean isFluid() {
            return fluid != net.minecraft.world.level.material.Fluids.EMPTY;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Entry entry && count == entry.count && fluid == entry.fluid
                    && ItemStack.isSameItemSameComponents(item, entry.item);
        }

        @Override
        public int hashCode() {
            return 31 * (31 * ItemStack.hashItemAndComponents(item) + Long.hashCode(count)) + fluid.hashCode();
        }
    }
}
