package io.github.keellam4.oritechextensions.fabric;

import io.github.keellam4.oritechextensions.storage.CargoVaultBlockEntity;
import io.github.keellam4.oritechextensions.storage.VaultContents;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;

import java.util.ArrayList;
import java.util.List;

/**
 * Lets pipes and other mods reach a vault through Fabric's transfer API: one storage slot per window. Inserting
 * goes to the window that already holds the item first, then to an empty one. A transaction that rolls back puts
 * the whole vault back as it was (a Compacting Vault's windows share one stock).
 */
public final class VaultItemStorage extends CombinedStorage<ItemVariant, VaultItemStorage.Slot> {

    private final CargoVaultBlockEntity vault;

    public VaultItemStorage(CargoVaultBlockEntity vault) {
        this(vault, new Snapshots(vault));
    }

    private VaultItemStorage(CargoVaultBlockEntity vault, Snapshots snapshots) {
        super(slots(vault, snapshots));
        this.vault = vault;
    }

    private static List<Slot> slots(CargoVaultBlockEntity vault, Snapshots snapshots) {
        var slots = new ArrayList<Slot>(vault.slots());
        for (int i = 0; i < vault.slots(); i++) slots.add(new Slot(vault, i, snapshots));
        return slots;
    }

    @Override
    public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, maxAmount);
        int holding = vault.slotOf(resource.toStack());
        if (holding >= 0) return parts.get(holding).insert(resource, maxAmount, transaction);
        for (var slot : parts) {
            long moved = slot.insert(resource, maxAmount, transaction);
            if (moved > 0) return moved;
        }
        return 0;
    }

    /** One snapshot of the whole vault per transaction. */
    static final class Snapshots extends SnapshotParticipant<VaultContents> {

        private final CargoVaultBlockEntity vault;

        Snapshots(CargoVaultBlockEntity vault) {
            this.vault = vault;
        }

        void before(TransactionContext transaction) {
            updateSnapshots(transaction);
        }

        @Override
        protected VaultContents createSnapshot() {
            return vault.saveState();
        }

        @Override
        protected void readSnapshot(VaultContents snapshot) {
            vault.restoreState(snapshot);
        }
    }

    /** One window. */
    static final class Slot implements SingleSlotStorage<ItemVariant> {

        private final CargoVaultBlockEntity vault;
        private final int index;
        private final Snapshots snapshots;

        Slot(CargoVaultBlockEntity vault, int index, Snapshots snapshots) {
            this.vault = vault;
            this.index = index;
            this.snapshots = snapshots;
        }

        @Override
        public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            var offered = resource.toStack((int) Math.min(maxAmount, Integer.MAX_VALUE));
            if (vault.insert(index, offered, true) <= 0) return 0;
            snapshots.before(transaction);
            return vault.insert(index, offered, false);
        }

        @Override
        public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            var item = vault.item(index);
            if (item.isEmpty() || !resource.matches(item)) return 0;
            long total = 0;
            snapshots.before(transaction);
            while (total < maxAmount) {
                var taken = vault.extract(index, (int) Math.min(maxAmount - total, Integer.MAX_VALUE), false);
                if (taken.isEmpty()) break;
                total += taken.getCount();
            }
            return total;
        }

        @Override
        public boolean isResourceBlank() {
            return vault.item(index).isEmpty();
        }

        @Override
        public ItemVariant getResource() {
            return ItemVariant.of(vault.item(index));
        }

        @Override
        public long getAmount() {
            return vault.count(index);
        }

        @Override
        public long getCapacity() {
            return vault.capacity(index);
        }
    }
}
