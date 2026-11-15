package io.github.keellam4.oritechextensions.fabric;

import io.github.keellam4.oritechextensions.storage.CargoVaultBlockEntity;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.world.item.ItemStack;

/** Lets pipes and other mods reach a Cargo Vault through Fabric's transfer API: one slot with the vault's item. */
public final class VaultItemStorage extends SnapshotParticipant<VaultItemStorage.Snapshot> implements SingleSlotStorage<ItemVariant> {

    record Snapshot(ItemStack item, int count) {
    }

    private final CargoVaultBlockEntity vault;

    public VaultItemStorage(CargoVaultBlockEntity vault) {
        this.vault = vault;
    }

    @Override
    public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, maxAmount);
        var offered = resource.toStack((int) Math.min(maxAmount, Integer.MAX_VALUE));
        if (!vault.accepts(offered)) return 0;
        int room = vault.capacity(vault.item().isEmpty() ? offered : vault.item()) - vault.count();
        int moved = (int) Math.min(maxAmount, Math.max(0, room));
        if (moved <= 0) return 0;
        updateSnapshots(transaction);
        vault.setContentsQuietly(vault.item().isEmpty() ? offered : vault.item(), vault.count() + moved);
        return moved;
    }

    @Override
    public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, maxAmount);
        if (vault.item().isEmpty() || !resource.matches(vault.item())) return 0;
        int moved = (int) Math.min(maxAmount, vault.count());
        if (moved <= 0) return 0;
        updateSnapshots(transaction);
        vault.setContentsQuietly(vault.item(), vault.count() - moved);
        return moved;
    }

    @Override
    public boolean isResourceBlank() {
        return vault.item().isEmpty();
    }

    @Override
    public ItemVariant getResource() {
        return ItemVariant.of(vault.item());
    }

    @Override
    public long getAmount() {
        return vault.count();
    }

    @Override
    public long getCapacity() {
        return vault.capacity();
    }

    @Override
    protected Snapshot createSnapshot() {
        return new Snapshot(vault.item().copy(), vault.count());
    }

    @Override
    protected void readSnapshot(Snapshot snapshot) {
        vault.restoreContents(snapshot.item(), snapshot.count());
    }

    @Override
    protected void onFinalCommit() {
        vault.changed();
    }
}
