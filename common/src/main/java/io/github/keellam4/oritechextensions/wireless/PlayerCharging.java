package io.github.keellam4.oritechextensions.wireless;

import io.github.keellam4.oritechextensions.platform.Platform;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

/**
 * Charges the energy items a player carries, in the pylon's order: armour first (so a jetpack never dies
 * mid-flight), then accessory slots (Curios), the offhand, the held item, the hotbar, then the rest.
 */
public final class PlayerCharging {

    private static final int ARMOUR_START = 36;
    private static final int OFFHAND = 40;

    private PlayerCharging() {
    }

    /** Charges up to {@code budget} RF into the player's items. Returns how much went in. */
    public static long charge(Player player, long budget) {
        long used = 0;
        for (int slot = ARMOUR_START; slot < ARMOUR_START + 4 && used < budget; slot++) {
            used += Platform.INSTANCE.chargeInventorySlot(player, slot, budget - used);
        }
        if (used < budget) used += Platform.INSTANCE.chargeAccessories(player, budget - used);
        if (used < budget) used += Platform.INSTANCE.chargeInventorySlot(player, OFFHAND, budget - used);
        int held = player.getInventory().selected;
        if (used < budget) used += Platform.INSTANCE.chargeInventorySlot(player, held, budget - used);
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE && used < budget; slot++) {
            if (slot != held) used += Platform.INSTANCE.chargeInventorySlot(player, slot, budget - used);
        }
        return used;
    }
}
