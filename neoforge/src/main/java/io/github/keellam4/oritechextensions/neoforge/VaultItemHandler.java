package io.github.keellam4.oritechextensions.neoforge;

import io.github.keellam4.oritechextensions.storage.CargoVaultBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/** Lets pipes and other mods reach a Cargo Vault: one slot holding the vault's item, as many as it has. */
public record VaultItemHandler(CargoVaultBlockEntity vault) implements IItemHandler {

    @Override
    public int getSlots() {
        return 1;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return vault.item().isEmpty() || vault.count() == 0 ? ItemStack.EMPTY : vault.item().copyWithCount(vault.count());
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        int moved = vault.insert(stack, simulate);
        return moved >= stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - moved);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return vault.extract(amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        return vault.capacity();
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return vault.accepts(stack);
    }
}
