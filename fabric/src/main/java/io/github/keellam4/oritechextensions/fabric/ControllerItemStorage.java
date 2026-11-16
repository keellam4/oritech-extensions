package io.github.keellam4.oritechextensions.fabric;

import io.github.keellam4.oritechextensions.storage.CargoControllerBlockEntity;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;

import java.util.ArrayList;

/**
 * The whole Cargo Wall as one item storage. Inserting goes to vaults already holding the item first, then to one
 * with room for a new item type. Its slots are every vault window on the wall, in order (Oritech's item pipes on
 * Fabric only see slotted storages).
 */
public final class ControllerItemStorage extends CombinedStorage<ItemVariant, VaultItemStorage>
        implements net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage<ItemVariant> {

    public ControllerItemStorage(CargoControllerBlockEntity controller) {
        super(parts(controller));
    }

    private static java.util.List<VaultItemStorage> parts(CargoControllerBlockEntity controller) {
        var parts = new ArrayList<VaultItemStorage>();
        for (var vault : controller.network().vaults()) parts.add(new VaultItemStorage(vault));
        return parts;
    }

    @Override
    public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, maxAmount);
        var stack = resource.toStack();
        long moved = 0;
        for (var part : parts) {
            if (moved >= maxAmount) break;
            if (part.vault().slotOf(stack) >= 0) moved += part.insert(resource, maxAmount - moved, transaction);
        }
        for (var part : parts) {
            if (moved >= maxAmount) break;
            if (part.vault().slotOf(stack) < 0) moved += part.insert(resource, maxAmount - moved, transaction);
        }
        return moved;
    }

    @Override
    public int getSlotCount() {
        int count = 0;
        for (var part : parts) count += part.getSlotCount();
        return count;
    }

    @Override
    public net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage<ItemVariant> getSlot(int slot) {
        for (var part : parts) {
            if (slot < part.getSlotCount()) return part.getSlot(slot);
            slot -= part.getSlotCount();
        }
        throw new IndexOutOfBoundsException("No slot " + slot + " on this Cargo Wall");
    }
}
