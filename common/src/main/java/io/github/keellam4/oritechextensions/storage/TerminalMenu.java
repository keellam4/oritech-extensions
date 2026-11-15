package io.github.keellam4.oritechextensions.storage;

import io.github.keellam4.oritechextensions.ModContent;
import io.github.keellam4.oritechextensions.platform.Platform;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Cargo Controller's terminal: the player's inventory, plus a list of everything in the network that the
 * server sends while the screen is open ({@link TerminalSyncPayload}). Grid clicks come back as
 * {@link TerminalActionPayload}; shift-clicking an inventory slot puts it into the network.
 */
public class TerminalMenu extends AbstractContainerMenu {

    public static final int TAKE_STACK = 0;
    public static final int TAKE_ONE = 1;
    public static final int TAKE_TO_INVENTORY = 2;
    public static final int INSERT_CARRIED = 3;
    public static final int INSERT_ONE = 4;

    public static final int INVENTORY_X = 8;
    public static final int INVENTORY_Y = 125;

    @Nullable
    private final CargoControllerBlockEntity controller;
    private final Player player;
    private List<CargoNetwork.Entry> lastSent;
    private int ticks;

    public TerminalMenu(int id, Inventory inventory) {
        this(id, inventory, null);
    }

    public TerminalMenu(int id, Inventory inventory, @Nullable CargoControllerBlockEntity controller) {
        super(ModContent.TERMINAL_MENU.get(), id);
        this.controller = controller;
        this.player = inventory.player;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col + row * 9 + 9, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, INVENTORY_X + col * 18, INVENTORY_Y + 58));
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (controller == null || !(player instanceof ServerPlayer serverPlayer)) return;
        if (lastSent != null && ticks++ % 10 != 0) return;
        var network = controller.network();
        var entries = network.contents();
        if (entries.equals(lastSent)) return;
        lastSent = entries;
        Platform.INSTANCE.sendToPlayer(serverPlayer, new TerminalSyncPayload(containerId, network.vaults().size(), entries));
    }

    /** A grid click from the client. */
    public void handle(int action, ItemStack item) {
        if (controller == null) return;
        var network = controller.network();
        var carried = getCarried();
        switch (action) {
            case TAKE_STACK, TAKE_ONE, TAKE_TO_INVENTORY -> {
                if (item.isEmpty()) return;
                if (action == TAKE_TO_INVENTORY) {
                    var taken = network.extract(item, item.getMaxStackSize(), false);
                    if (taken.isEmpty()) return;
                    player.getInventory().add(taken);
                    if (!taken.isEmpty()) network.insert(taken, false); // whatever didn't fit goes back
                    return;
                }
                if (!carried.isEmpty() && !ItemStack.isSameItemSameComponents(carried, item)) return;
                int room = carried.isEmpty() ? item.getMaxStackSize() : carried.getMaxStackSize() - carried.getCount();
                var taken = network.extract(item, action == TAKE_ONE ? Math.min(1, room) : room, false);
                if (taken.isEmpty()) return;
                if (carried.isEmpty()) setCarried(taken);
                else carried.grow(taken.getCount());
            }
            case INSERT_CARRIED, INSERT_ONE -> {
                if (carried.isEmpty()) return;
                int moved = network.insert(carried.copyWithCount(action == INSERT_ONE ? 1 : carried.getCount()), false);
                carried.shrink(moved);
            }
            default -> {
            }
        }
        lastSent = null; // send the new totals right away
        broadcastChanges();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        var slot = slots.get(index);
        if (controller == null || !slot.hasItem()) return ItemStack.EMPTY;
        var stack = slot.getItem();
        int moved = controller.network().insert(stack, false);
        if (moved <= 0) return ItemStack.EMPTY;
        stack.shrink(moved);
        slot.setChanged();
        lastSent = null;
        return ItemStack.EMPTY; // don't repeat: one stack per shift-click
    }

    @Override
    public boolean stillValid(Player player) {
        if (controller == null) return true;
        var level = controller.getLevel();
        return level != null && level.getBlockEntity(controller.getBlockPos()) == controller
                && player.distanceToSqr(controller.getBlockPos().getCenter()) <= 64;
    }
}
