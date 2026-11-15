package io.github.keellam4.oritechextensions.fabric;

import io.github.keellam4.oritechextensions.ModContent;
import io.github.keellam4.oritechextensions.wireless.CargoLinkItem;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import team.reborn.energy.api.EnergyStorage;

/** Lets chargers (Induction Pylons, Oritech's Charger, other mods) fill a Cargo Link's battery (Team Reborn Energy). */
public final class LinkEnergyStorage implements EnergyStorage {

    private final ContainerItemContext context;

    public LinkEnergyStorage(ContainerItemContext context) {
        this.context = context;
    }

    private int stored() {
        return context.getItemVariant().toStack().getOrDefault(ModContent.LINK_ENERGY.get(), 0);
    }

    @Override
    public long insert(long maxAmount, TransactionContext transaction) {
        int stored = stored();
        long moved = Math.min(maxAmount, Math.min(CargoLinkItem.MAX_INPUT, CargoLinkItem.CAPACITY - stored));
        if (moved <= 0) return 0;
        var stack = context.getItemVariant().toStack();
        stack.set(ModContent.LINK_ENERGY.get(), stored + (int) moved);
        return context.exchange(ItemVariant.of(stack), 1, transaction) == 1 ? moved : 0;
    }

    @Override
    public long extract(long maxAmount, TransactionContext transaction) {
        return 0;
    }

    @Override
    public boolean supportsExtraction() {
        return false;
    }

    @Override
    public long getAmount() {
        return stored();
    }

    @Override
    public long getCapacity() {
        return CargoLinkItem.CAPACITY;
    }
}
