package io.github.keellam4.oritechextensions.neoforge;

import io.github.keellam4.oritechextensions.storage.CargoControllerBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * The whole Cargo Wall as one item handler: one slot per vault window. Inserting into any slot routes through the
 * network (vault holding the item first), so pipes never jam on one full window.
 */
public record ControllerItemHandler(CargoControllerBlockEntity controller) implements IItemHandler {

    private record At(io.github.keellam4.oritechextensions.storage.CargoVaultBlockEntity vault, int slot) {
    }

    private At at(int slot) {
        int index = slot;
        for (var vault : controller.network().vaults()) {
            if (index < vault.slots()) return new At(vault, index);
            index -= vault.slots();
        }
        return null;
    }

    @Override
    public int getSlots() {
        int total = 0;
        for (var vault : controller.network().vaults()) total += vault.slots();
        return Math.max(1, total);
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        var at = at(slot);
        if (at == null || at.vault().count(at.slot()) <= 0) return ItemStack.EMPTY;
        return at.vault().item(at.slot()).copyWithCount(at.vault().count(at.slot()));
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        int moved = controller.network().insert(stack, simulate);
        return moved >= stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - moved);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        var at = at(slot);
        return at == null ? ItemStack.EMPTY : at.vault().extract(at.slot(), amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        var at = at(slot);
        return at == null ? 64 : at.vault().capacity(at.slot());
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return controller.network().insert(stack.copyWithCount(1), true) > 0;
    }
}
