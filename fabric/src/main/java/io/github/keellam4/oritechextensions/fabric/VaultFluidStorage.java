package io.github.keellam4.oritechextensions.fabric;

import io.github.keellam4.oritechextensions.storage.NetworkTank;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;

/**
 * Lets pipes, buckets and other mods reach a Fluid Vault or Fluid Silo through Fabric's transfer API (droplets:
 * 81 per millibucket). One view per fluid it holds, so a partitioned silo shows all of its fluids.
 */
public final class VaultFluidStorage extends SnapshotParticipant<Object> implements Storage<FluidVariant> {

    static final long DROPLETS_PER_MB = FluidConstants.BUCKET / 1000;

    private final NetworkTank vault;

    public VaultFluidStorage(NetworkTank vault) {
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
    public java.util.Iterator<StorageView<FluidVariant>> iterator() {
        var views = new java.util.ArrayList<StorageView<FluidVariant>>();
        for (var held : vault.fluids()) views.add(new View(FluidVariant.of(held.fluid()), held.amount()));
        return views.iterator();
    }

    @Override
    protected Object createSnapshot() {
        return vault.saveFluidState();
    }

    @Override
    protected void readSnapshot(Object snapshot) {
        vault.restoreFluidState(snapshot);
        vault.changed();
    }

    /** One fluid in the tank. */
    private final class View implements StorageView<FluidVariant> {

        private final FluidVariant resource;
        private final long amount;

        View(FluidVariant resource, long amount) {
            this.resource = resource;
            this.amount = amount;
        }

        @Override
        public long extract(FluidVariant wanted, long maxAmount, TransactionContext transaction) {
            return wanted.equals(resource) ? VaultFluidStorage.this.extract(wanted, maxAmount, transaction) : 0;
        }

        @Override
        public boolean isResourceBlank() {
            return resource.isBlank();
        }

        @Override
        public FluidVariant getResource() {
            return resource;
        }

        @Override
        public long getAmount() {
            return amount * DROPLETS_PER_MB;
        }

        @Override
        public long getCapacity() {
            return vault.fluidCapacity() * DROPLETS_PER_MB;
        }
    }
}
