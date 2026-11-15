package io.github.keellam4.oritechextensions.storage;

import io.github.keellam4.oritechextensions.ModContent;
import net.minecraft.core.BlockPos;
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
 * The Silo Controller's screen: the partition upgrade slot and the player's inventory. The fluid list comes from
 * the client's copy of the controller (synced as it changes); its buttons arrive as menu button clicks
 * ({@code row * 8 + action}, see {@link SiloControllerBlockEntity#configure}).
 */
public class SiloMenu extends AbstractContainerMenu {

    public static final int ROWS = 5;
    public static final int UPGRADE_X = 152;
    public static final int UPGRADE_Y = 130;
    public static final int INVENTORY_Y = 166;

    @Nullable
    private final SiloControllerBlockEntity silo;
    private final ContainerData position;

    public SiloMenu(int id, Inventory inventory) {
        this(id, inventory, null, new SimpleContainer(1), new SimpleContainerData(6));
    }

    public SiloMenu(int id, Inventory inventory, SiloControllerBlockEntity silo) {
        this(id, inventory, silo, silo.upgrade(), positionData(silo.getBlockPos()));
    }

    private SiloMenu(int id, Inventory inventory, @Nullable SiloControllerBlockEntity silo, Container upgrade, ContainerData position) {
        super(ModContent.SILO_MENU.get(), id);
        this.silo = silo;
        this.position = position;
        addDataSlots(position);
        addSlot(new Slot(upgrade, 0, UPGRADE_X, UPGRADE_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof PartitionUpgradeItem;
            }

            @Override
            public boolean mayPickup(Player player) {
                return SiloMenu.this.silo == null || SiloMenu.this.silo.canRemoveUpgrade();
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

    /** The controller's position, as six 16-bit halves (menu data is sent as shorts). */
    private static ContainerData positionData(BlockPos pos) {
        var data = new SimpleContainerData(6);
        int[] coords = {pos.getX(), pos.getY(), pos.getZ()};
        for (int i = 0; i < 3; i++) {
            data.set(i * 2, coords[i] & 0xFFFF);
            data.set(i * 2 + 1, (coords[i] >>> 16) & 0xFFFF);
        }
        return data;
    }

    public BlockPos pos() {
        int[] coords = new int[3];
        for (int i = 0; i < 3; i++) coords[i] = (position.get(i * 2) & 0xFFFF) | (position.get(i * 2 + 1) << 16);
        return new BlockPos(coords[0], coords[1], coords[2]);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        return silo != null && silo.configure(id / 8, id % 8);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        var slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        var stack = slot.getItem();
        var copy = stack.copy();
        if (index == 0) {
            if (!slot.mayPickup(player) || !moveItemStackTo(stack, 1, slots.size(), true)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof PartitionUpgradeItem && !slots.get(0).hasItem()) {
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
        return silo == null || !silo.isRemoved() && player.distanceToSqr(silo.getBlockPos().getCenter()) <= 64;
    }
}
