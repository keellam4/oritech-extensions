package io.github.keellam4.oritechextensions.storage;

import io.github.keellam4.oritechextensions.ModContent;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A vault's upgrade screen: one slot per kind of upgrade (capacity takes up to five, the others one), the sides Pull
 * and Push work on, and the lock. Opened by sneak + right-clicking a vault with an empty hand, or right-clicking its
 * side or top.
 * <p>
 * Buttons go through vanilla's menu button packet ({@link #clickMenuButton}): ids 0-5 toggle a Pull side, 10-15 a
 * Push side ({@link VaultSide} order), 20 the lock, 21 Clear.
 */
public class VaultUpgradeMenu extends AbstractContainerMenu {

    public static final int UPGRADES_X = 44;
    public static final int UPGRADES_Y = 20;
    public static final int INVENTORY_Y = 118;
    public static final int BUTTON_PULL = 0;
    public static final int BUTTON_PUSH = 10;
    public static final int BUTTON_LOCK = 20;
    public static final int BUTTON_CLEAR = 21;

    private static final int DATA_PULL = 0;
    private static final int DATA_PUSH = 1;
    private static final int DATA_LOCKED = 2;
    private static final int DATA_CLEARABLE = 3;

    private final Container upgrades;
    private final ContainerData data;
    @Nullable
    private final CargoVaultBlockEntity vault;

    /** Client side: the slots and data are filled in by the server. */
    public VaultUpgradeMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainer(CargoVaultBlockEntity.UPGRADE_SLOTS), new SimpleContainerData(4), null);
    }

    public VaultUpgradeMenu(int id, Inventory inventory, CargoVaultBlockEntity vault) {
        this(id, inventory, vault.upgrades(), new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case DATA_PULL -> vault.pullSides();
                    case DATA_PUSH -> vault.pushSides();
                    case DATA_CLEARABLE -> vault.canClearTypes() ? 1 : 0;
                    default -> vault.isLocked() ? 1 : 0;
                };
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return 4;
            }
        }, vault);
    }

    private VaultUpgradeMenu(int id, Inventory inventory, Container upgrades, ContainerData data, @Nullable CargoVaultBlockEntity vault) {
        super(ModContent.VAULT_UPGRADE_MENU.get(), id);
        this.upgrades = upgrades;
        this.data = data;
        this.vault = vault;
        for (var kind : VaultUpgrade.Kind.values()) {
            addSlot(new UpgradeSlot(upgrades, kind, UPGRADES_X + kind.slot() * 18, UPGRADES_Y));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, INVENTORY_Y + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, INVENTORY_Y + 58));
        addDataSlots(data);
    }

    /** A slot for one kind of upgrade. */
    public final class UpgradeSlot extends Slot {

        private final VaultUpgrade.Kind kind;

        UpgradeSlot(Container container, VaultUpgrade.Kind kind, int x, int y) {
            super(container, kind.slot(), x, y);
            this.kind = kind;
        }

        public VaultUpgrade.Kind kind() {
            return kind;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.getItem() instanceof VaultUpgradeItem item && item.upgrade().kind() == kind;
        }

        @Override
        public int getMaxStackSize() {
            return kind.limit();
        }

        @Override
        public boolean mayPickup(Player player) {
            return vault == null || vault.canRemoveUpgrade(kind.slot());
        }
    }

    public Container upgrades() {
        return upgrades;
    }

    public boolean pulls(VaultSide side) {
        return (data.get(DATA_PULL) & side.bit()) != 0;
    }

    public boolean pushes(VaultSide side) {
        return (data.get(DATA_PUSH) & side.bit()) != 0;
    }

    public boolean locked() {
        return data.get(DATA_LOCKED) != 0;
    }

    /** Whether Clear would do something (locked, with an empty slot that still has a type). */
    public boolean clearable() {
        return data.get(DATA_CLEARABLE) != 0;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (vault == null) return false;
        if (id == BUTTON_LOCK) {
            CargoVault.toggleLock(vault, player);
            return true;
        }
        if (id == BUTTON_CLEAR) {
            CargoVault.clearTypes(vault, player);
            return true;
        }
        var sides = VaultSide.values();
        if (id >= BUTTON_PULL && id < BUTTON_PULL + sides.length) {
            vault.toggleSide(false, sides[id - BUTTON_PULL]);
            return true;
        }
        if (id >= BUTTON_PUSH && id < BUTTON_PUSH + sides.length) {
            vault.toggleSide(true, sides[id - BUTTON_PUSH]);
            return true;
        }
        return false;
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
        } else if (stack.getItem() instanceof VaultUpgradeItem item) {
            int target = item.upgrade().kind().slot();
            if (!moveItemStackTo(stack, target, target + 1, false)) return ItemStack.EMPTY;
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
