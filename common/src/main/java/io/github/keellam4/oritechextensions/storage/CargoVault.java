package io.github.keellam4.oritechextensions.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import io.github.keellam4.oritechextensions.platform.Platform;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * How players use a Cargo Vault.
 * <ul>
 *     <li>Right-click with items: put the held stack in. Right-click again quickly: put in every matching item.</li>
 *     <li>Right-click with an empty hand: put in every matching item from the inventory.</li>
 *     <li>Sneak + right-click with an empty hand, or right-click a side: the upgrade screen (with the lock).</li>
 *     <li>Right-click with an upgrade: put it in its slot.</li>
 *     <li>Left-click the front: take a stack from the window under the crosshair. Sneak + left-click: take one.
 *     Break it from another side, or from the front once it's empty.</li>
 * </ul>
 */
public final class CargoVault {

    /** Two right-clicks this close together (ticks) put in every matching item. */
    private static final int DOUBLE_CLICK_TICKS = 10;
    /** Holding left-click takes a stack this often (ticks), not every tick. */
    private static final int TAKE_COOLDOWN_TICKS = 4;

    private static final Map<UUID, Long> LAST_INSERT = new WeakHashMap<>();
    private static final Map<UUID, Long> LAST_TAKE = new WeakHashMap<>();

    private CargoVault() {
    }

    /** Client side: the slot under the crosshair on the vault at a position (set by the client entry points). */
    public static java.util.function.ToIntFunction<BlockPos> clientSlotLookup = pos -> 0;

    /** Puts the held stack into the clicked slot (or the slot that holds that item, or the first empty one). */
    public static void insertHeld(CargoVaultBlockEntity vault, Player player, InteractionHand hand, int clickedSlot) {
        var level = vault.getLevel();
        if (level == null) return;
        long now = level.getGameTime();
        Long last = LAST_INSERT.put(player.getUUID(), now);
        if (last != null && now - last <= DOUBLE_CLICK_TICKS) {
            insertAll(level, vault, player);
            return;
        }
        var stack = player.getItemInHand(hand);
        int moved = clickedSlot >= 0 && clickedSlot < vault.slots() ? vault.insert(clickedSlot, stack, false) : 0;
        if (moved == 0) moved = vault.insert(stack, false);
        if (moved > 0) {
            if (!player.getAbilities().instabuild) stack.shrink(moved);
            sound(level, vault.getBlockPos(), true);
        } else if (vault.accepts(stack)) {
            player.displayClientMessage(Component.translatable("message.oritech_extensions.cargo_vault.full"), true);
        }
    }

    /** Puts every item matching one of the vault's item types from the player's inventory in. Returns whether anything moved. */
    public static boolean insertAll(Level level, CargoVaultBlockEntity vault, Player player) {
        if (!vault.hasAnyItemType() || level.isClientSide()) return false;
        var inventory = player.getInventory();
        int total = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            var stack = inventory.getItem(i);
            if (stack.isEmpty() || vault.slotOf(stack) < 0) continue;
            int moved = vault.insert(stack, false);
            if (moved <= 0) continue;
            stack.shrink(moved);
            total += moved;
        }
        if (total > 0) {
            inventory.setChanged();
            sound(level, vault.getBlockPos(), true);
        }
        return total > 0;
    }

    public static void toggleLock(CargoVaultBlockEntity vault, Player player) {
        boolean locked = !vault.isLocked();
        if (locked && !vault.hasContentType()) {
            player.displayClientMessage(Component.translatable("message.oritech_extensions.cargo_vault.nothing_to_lock"), true);
            return;
        }
        vault.setLocked(locked);
        player.displayClientMessage(Component.translatable(locked ? "message.oritech_extensions.cargo_vault.locked"
                : "message.oritech_extensions.cargo_vault.unlocked"), true);
        var level = vault.getLevel();
        if (level != null) level.playSound(null, vault.getBlockPos(), SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.5f, locked ? 1.4f : 1.1f);
    }

    /**
     * Loader hook: a player left-clicked a block. Returns true when it was the front of a Cargo Vault with items in
     * it; breaking must then be cancelled. The client asks the server to take items ({@link VaultTakePayload}), so
     * it works the same whatever each loader does with a cancelled click.
     */
    public static boolean onLeftClick(Level level, BlockPos pos, Player player, Direction face) {
        var state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CargoVaultBlock) || player.isSpectator()) return false;
        if (face != state.getValue(BlockStateProperties.HORIZONTAL_FACING)) return false;
        if (!(level.getBlockEntity(pos) instanceof CargoVaultBlockEntity vault) || !vault.takesByHand()) return false;
        if (level.isClientSide()) {
            int slot = clientSlotLookup.applyAsInt(pos);
            Platform.INSTANCE.sendToServer(new VaultTakePayload(pos, Math.max(0, slot), player.isShiftKeyDown()));
        }
        return true;
    }

    /** Payload handler: take a stack (or one item) from a slot of the vault at {@code pos}. */
    public static void take(ServerPlayer player, BlockPos pos, int slot, boolean one) {
        var level = player.serverLevel();
        if (!player.canInteractWithBlock(pos, 1.0) || !(level.getBlockEntity(pos) instanceof CargoVaultBlockEntity vault)) return;
        if (slot < 0 || slot >= vault.slots()) return;
        long now = level.getGameTime();
        Long last = LAST_TAKE.get(player.getUUID());
        if (last != null && now >= last && now - last < TAKE_COOLDOWN_TICKS) return;
        LAST_TAKE.put(player.getUUID(), now);

        var taken = vault.extract(slot, one ? 1 : vault.item(slot).getMaxStackSize(), false);
        if (taken.isEmpty()) return;
        if (!player.getInventory().add(taken)) player.drop(taken, false);
        sound(level, pos, false);
    }

    private static void sound(Level level, BlockPos pos, boolean in) {
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.25f, in ? 0.7f : 1.2f);
    }
}
