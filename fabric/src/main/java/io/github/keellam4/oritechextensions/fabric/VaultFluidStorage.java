package io.github.keellam4.oritechextensions.fabric;

import io.github.keellam4.oritechextensions.storage.FluidContents;
import io.github.keellam4.oritechextensions.storage.FluidVaultBlockEntity;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;

/** Lets pipes, buckets and other mods reach a Fluid Vault through Fabric's transfer API (droplets: 81 per millibucket). */
public final class VaultFluidStorage extends SnapshotParticipant<FluidContents> implements SingleSlotStorage<FluidVariant> {

    static final long DROPLETS_PER_MB = FluidConstants.BUCKET / 1000;

    private final FluidVaultBlockEntity vault;

    public VaultFluidStorage(FluidVaultBlockEntity vault) {
        this.vault = vault;
    }

    @Override
    public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, maxAmount);
        long mb = maxAmount / DROPLETS_PER_MB;
        if (mb <= 0 || vault.fill(resource.getFluid(), mb, true) <= 0) return 0;
        updateSnapshots(transaction);
        return vault.fill(resource.getFluid(), mb, false) * DROPLETS_PER_MB;
    }

    @Override
    public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, maxAmount);
        long mb = maxAmount / DROPLETS_PER_MB;
        if (mb <= 0 || vault.drain(resource.getFluid(), mb, true).isEmpty()) return 0;
        updateSnapshots(transaction);
        return vault.drain(resource.getFluid(), mb, false).amount() * DROPLETS_PER_MB;
    }

    @Override
    public boolean isResourceBlank() {
        return vault.amount() <= 0;
    }

    @Override
    public FluidVariant getResource() {
        return vault.amount() <= 0 ? FluidVariant.blank() : FluidVariant.of(vault.fluid());
    }

    @Override
    public long getAmount() {
        return vault.amount() * DROPLETS_PER_MB;
    }

    @Override
    public long getCapacity() {
        return vault.fluidCapacity() * DROPLETS_PER_MB;
    }

    @Override
    protected FluidContents createSnapshot() {
        return vault.fluidContents();
    }

    @Override
    protected void readSnapshot(FluidContents snapshot) {
        vault.loadFluid(snapshot);
        vault.changed();
    }
}
