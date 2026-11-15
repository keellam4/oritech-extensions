package io.github.keellam4.oritechextensions.neoforge;

import io.github.keellam4.oritechextensions.storage.CargoVaultBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Lets pipes and other mods reach a vault: one slot per vault slot, holding as many as it has. An empty slot
 * refuses items another slot already holds, so an item type never spreads over two slots.
 */
public record VaultItemHandler(CargoVaultBlockEntity vault) implements IItemHandler {

    @Override
    public int getSlots() {
        return vault.slots();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        var item = vault.item(slot);
        return item.isEmpty() || vault.count(slot) == 0 ? ItemStack.EMPTY : item.copyWithCount(vault.count(slot));
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        int moved = vault.insert(slot, stack, simulate);
        return moved >= stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - moved);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return vault.extract(slot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        return vault.capacity(slot);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return vault.accepts(slot, stack);
    }
}
