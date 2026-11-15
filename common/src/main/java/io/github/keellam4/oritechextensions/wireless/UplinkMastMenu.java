package io.github.keellam4.oritechextensions.wireless;

import io.github.keellam4.oritechextensions.ModContent;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** The Uplink Mast's screen: its one upgrade slot and the player's inventory. */
public class UplinkMastMenu extends AbstractContainerMenu {

    public static final int SLOT_X = 80;
    public static final int SLOT_Y = 38;
    public static final int INVENTORY_Y = 84;

    @Nullable
    private final UplinkMastBlockEntity mast;

    public UplinkMastMenu(int id, Inventory inventory) {
        this(id, inventory, null, new SimpleContainer(1));
    }

    public UplinkMastMenu(int id, Inventory inventory, UplinkMastBlockEntity mast) {
        this(id, inventory, mast, mast.upgrade());
    }

    private UplinkMastMenu(int id, Inventory inventory, @Nullable UplinkMastBlockEntity mast, Container upgrade) {
        super(ModContent.UPLINK_MAST_MENU.get(), id);
        this.mast = mast;
        addSlot(new Slot(upgrade, 0, SLOT_X, SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof LinkUpgradeItem;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, INVENTORY_Y + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, INVENTORY_Y + 58));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        var slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        var stack = slot.getItem();
        var copy = stack.copy();
        if (index == 0) {
            if (!moveItemStackTo(stack, 1, slots.size(), true)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof LinkUpgradeItem && !slots.get(0).hasItem()) {
            if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return mast == null || !mast.isRemoved() && player.distanceToSqr(mast.getBlockPos().getCenter()) <= 64;
    }
}
