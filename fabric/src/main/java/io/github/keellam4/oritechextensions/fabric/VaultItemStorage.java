package io.github.keellam4.oritechextensions.fabric;

import io.github.keellam4.oritechextensions.storage.CargoVaultBlockEntity;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Lets pipes and other mods reach a vault through Fabric's transfer API: one storage slot per vault slot. Inserting
 * goes to the slot that already holds the item first, then to an empty one.
 */
public final class VaultItemStorage extends CombinedStorage<ItemVariant, VaultItemStorage.Slot> {

    private final CargoVaultBlockEntity vault;

    public VaultItemStorage(CargoVaultBlockEntity vault) {
        super(slots(vault));
        this.vault = vault;
    }

    private static List<Slot> slots(CargoVaultBlockEntity vault) {
        var slots = new ArrayList<Slot>(vault.slots());
        for (int i = 0; i < vault.slots(); i++) slots.add(new Slot(vault, i));
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

    /** One vault slot, with rollback for transactions. */
    static final class Slot extends SnapshotParticipant<Slot.Snapshot> implements SingleSlotStorage<ItemVariant> {

        record Snapshot(ItemStack item, int count) {
        }

        private final CargoVaultBlockEntity vault;
        private final int index;

        Slot(CargoVaultBlockEntity vault, int index) {
            this.vault = vault;
            this.index = index;
        }

        @Override
        public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            var offered = resource.toStack();
            if (!vault.accepts(index, offered)) return 0;
            int room = vault.capacity(index, offered) - vault.count(index);
            int moved = (int) Math.min(maxAmount, Math.max(0, room));
            if (moved <= 0) return 0;
            updateSnapshots(transaction);
            vault.setSlotQuietly(index, vault.item(index).isEmpty() ? offered : vault.item(index), vault.count(index) + moved);
            return moved;
        }

        @Override
        public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            var item = vault.item(index);
            if (item.isEmpty() || !resource.matches(item)) return 0;
            int moved = (int) Math.min(maxAmount, vault.count(index));
            if (moved <= 0) return 0;
            updateSnapshots(transaction);
            vault.setSlotQuietly(index, item, vault.count(index) - moved);
            return moved;
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

        @Override
        protected Snapshot createSnapshot() {
            return new Snapshot(vault.item(index).copy(), vault.count(index));
        }

        @Override
        protected void readSnapshot(Snapshot snapshot) {
            vault.restoreSlot(index, snapshot.item(), snapshot.count());
        }

        @Override
        protected void onFinalCommit() {
            vault.changed();
        }
    }
}
