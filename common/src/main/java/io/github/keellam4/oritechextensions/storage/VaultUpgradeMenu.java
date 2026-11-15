package io.github.keellam4.oritechextensions.storage;

import io.github.keellam4.oritechextensions.ModContent;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A vault's upgrade screen: four upgrade slots above the player's inventory. Opened by right-clicking a vault's side
 * or top. A capacity upgrade can't be taken out while the vault holds more than it could without it.
 */
public class VaultUpgradeMenu extends AbstractContainerMenu {

    public static final int UPGRADES_X = 53;
    public static final int UPGRADES_Y = 20;

    private final Container upgrades;
    @Nullable
    private final CargoVaultBlockEntity vault;

    /** Client side: the slots are filled in by the server. */
    public VaultUpgradeMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainer(CargoVaultBlockEntity.UPGRADE_SLOTS), null);
    }

    public VaultUpgradeMenu(int id, Inventory inventory, CargoVaultBlockEntity vault) {
        this(id, inventory, vault.upgrades(), vault);
    }

    private VaultUpgradeMenu(int id, Inventory inventory, Container upgrades, @Nullable CargoVaultBlockEntity vault) {
        super(ModContent.VAULT_UPGRADE_MENU.get(), id);
        this.upgrades = upgrades;
        this.vault = vault;
        for (int i = 0; i < upgrades.getContainerSize(); i++) {
            int index = i;
            addSlot(new Slot(upgrades, i, UPGRADES_X + i * 18, UPGRADES_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.getItem() instanceof VaultUpgradeItem;
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }

                @Override
                public boolean mayPickup(Player player) {
                    return VaultUpgradeMenu.this.vault == null || VaultUpgradeMenu.this.vault.canRemoveUpgrade(index);
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 142));
    }

    /** The upgrades shown in the screen (synced from the server). */
    public Container upgrades() {
        return upgrades;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        var slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        var stack = slot.getItem();
        var copy = stack.copy();
        int upgradeSlots = upgrades.getContainerSize();
        if (index < upgradeSlots) {
            if (!slot.mayPickup(player) || !moveItemStackTo(stack, upgradeSlots, slots.size(), true)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof VaultUpgradeItem) {
            if (!moveItemStackTo(stack, 0, upgradeSlots, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        if (vault == null) return true;
        var level = vault.getLevel();
        return level != null && level.getBlockEntity(vault.getBlockPos()) == vault
                && player.distanceToSqr(vault.getBlockPos().getCenter()) <= 64;
    }
}
