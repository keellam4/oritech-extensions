package io.github.keellam4.oritechextensions.fabric;

import io.github.keellam4.oritechextensions.storage.CargoControllerBlockEntity;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;

import java.util.ArrayList;

/** The Cargo Wall's Fluid Vaults as one fluid storage; filling goes to the vault holding the fluid first. */
public final class ControllerFluidStorage extends CombinedStorage<FluidVariant, VaultFluidStorage> {

    private final java.util.List<io.github.keellam4.oritechextensions.storage.FluidVaultBlockEntity> tanks;

    public ControllerFluidStorage(CargoControllerBlockEntity controller) {
        this(controller.network().tanks());
    }

    private ControllerFluidStorage(java.util.List<io.github.keellam4.oritechextensions.storage.FluidVaultBlockEntity> tanks) {
        super(parts(tanks));
        this.tanks = tanks;
    }

    private static java.util.List<VaultFluidStorage> parts(java.util.List<io.github.keellam4.oritechextensions.storage.FluidVaultBlockEntity> tanks) {
        var parts = new ArrayList<VaultFluidStorage>();
        for (var tank : tanks) parts.add(new VaultFluidStorage(tank));
        return parts;
    }

    @Override
    public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, maxAmount);
        long moved = 0;
        for (int i = 0; i < parts.size() && moved < maxAmount; i++) {
            var tank = tanks.get(i);
            if (tank.amount() > 0 && tank.fluid().isSame(resource.getFluid())) moved += parts.get(i).insert(resource, maxAmount - moved, transaction);
        }
        for (int i = 0; i < parts.size() && moved < maxAmount; i++) {
            if (tanks.get(i).amount() <= 0) moved += parts.get(i).insert(resource, maxAmount - moved, transaction);
        }
        return moved;
    }
}
