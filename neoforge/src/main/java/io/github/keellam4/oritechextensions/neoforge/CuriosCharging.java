package io.github.keellam4.oritechextensions.neoforge;

import net.minecraft.world.entity.player.Player;
import top.theillusivec4.curios.api.CuriosApi;

/** Charges energy items in Curios slots. Only loaded when Curios is installed. */
final class CuriosCharging {

    private CuriosCharging() {
    }

    static long charge(Player player, long max) {
        var inventory = CuriosApi.getCuriosInventory(player);
        if (inventory.isEmpty()) return 0;
        var slots = inventory.get().getEquippedCurios();
        long used = 0;
        for (int i = 0; i < slots.getSlots() && used < max; i++) {
            used += NeoForgePlatform.chargeStack(slots.getStackInSlot(i), max - used);
        }
        return used;
    }
}
