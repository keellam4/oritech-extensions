package io.github.keellam4.oritechextensions.paint;

import io.github.keellam4.oritechextensions.ModContent;
import io.github.keellam4.oritechextensions.platform.Platform;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import rearth.oritech.block.blocks.addons.MachineAddonBlock;
import rearth.oritech.block.entity.addons.AddonBlockEntity;
import rearth.oritech.util.ColorableMachine;
import rearth.oritech.util.ColorableMachine.ColorVariant;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Paint on machine addon blocks, from Oritech and from addon mods built on Oritech's addon block (e.g. Oritech
 * Things' tiered addons). Oritech only paints blocks implementing ColorableMachine; for addons we store the
 * colour ourselves in a data attachment on the block entity, and keep it on the item when the addon is broken.
 * The cartridge click itself is handled in {@link PaintTargets}.
 */
public final class PaintEvents {

    private PaintEvents() {
    }

    // ---------------------------------------------------------------- what can be painted

    /** Machine addons (any mod) that Oritech doesn't already paint itself. */
    public static boolean isPaintable(@Nullable BlockEntity blockEntity) {
        return blockEntity instanceof AddonBlockEntity && !(blockEntity instanceof ColorableMachine);
    }

    public static ColorVariant getPaint(BlockEntity blockEntity) {
        return Platform.INSTANCE.getAddonPaint(blockEntity);
    }

    /** Sets (or with ORANGE, clears) the paint on a block and tells nearby players. Server side. */
    public static void setPaint(ServerLevel level, BlockEntity blockEntity, ColorVariant variant) {
        Platform.INSTANCE.setAddonPaint(blockEntity, variant);
        blockEntity.setChanged();

        var pos = blockEntity.getBlockPos();
        Platform.INSTANCE.sendToPlayersTrackingChunk(level, new ChunkPos(pos),
                new PaintSyncPayload(List.of(PaintSyncPayload.Entry.onBlockEntity(pos, variant))));
    }

    // ---------------------------------------------------------------- applying paint

    /** About to place a painted addon? Remember its colour for onBlockPlaced (the stack may be empty by then). */
    /** Loader hook: any right-click on a block. Remembers a painted addon item about to be placed. */
    public static void onRightClickBlock(Level level, Player player, ItemStack stack) {
        var paint = stack.get(ModContent.PAINT_COMPONENT.get());
        if (paint != null && stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof MachineAddonBlock) {
            rememberHeldPaint(player, stack, level.getGameTime());
        }
    }

    /**
     * Paints the block at {@code pos} with the cartridge in {@code stack}. Kept for callers that predate
     * {@link PaintTargets}, which now does the work for every paintable block.
     *
     * @return false if the block can't be painted or already has this colour.
     */
    public static boolean tryPaint(ServerLevel level, BlockPos pos, Player player, InteractionHand hand, ItemStack stack) {
        return PaintTargets.tryPaint(level, pos, player, hand, stack);
    }

    // ---------------------------------------------------------------- keeping paint when a block is moved

    private record PendingPlacement(ColorVariant variant, Item item, long gameTime) {
    }

    private static final Map<UUID, PendingPlacement> PENDING = new ConcurrentHashMap<>();

    public static void rememberHeldPaint(Player player, ItemStack stack, long gameTime) {
        var paint = stack.get(ModContent.PAINT_COMPONENT.get());
        if (paint != null) PENDING.put(player.getUUID(), new PendingPlacement(paint, stack.getItem(), gameTime));
    }

    /** Loader hook: a block is dropping items. The paint its own block item should carry, or null. */
    @Nullable
    public static ColorVariant paintForDrops(@Nullable BlockEntity blockEntity) {
        if (!isPaintable(blockEntity)) return null;
        var paint = getPaint(blockEntity);
        return PaintColors.isPainted(paint) ? paint : null;
    }

    /** Copy of {@code drop} carrying {@code paint} if it is {@code state}'s own block item; otherwise {@code drop}. */
    public static ItemStack paintDrop(BlockState state, ItemStack drop, ColorVariant paint) {
        if (!drop.is(state.getBlock().asItem())) return drop;
        var stack = drop.copy();
        stack.set(ModContent.PAINT_COMPONENT.get(), paint);
        return stack;
    }

    /** Loader hook: a player placed a block. */
    public static void onBlockPlaced(ServerLevel level, BlockPos pos, Player player) {
        applyRememberedPaint(level, pos, player);
    }

    /** Applies the colour of the painted item the player just placed, if any. */
    public static void applyRememberedPaint(ServerLevel level, BlockPos pos, Player player) {
        var pending = PENDING.remove(player.getUUID());
        if (pending == null || pending.gameTime() != level.getGameTime()) return;
        if (level.getBlockState(pos).getBlock().asItem() != pending.item()) return;

        var blockEntity = level.getBlockEntity(pos);
        if (isPaintable(blockEntity)) setPaint(level, blockEntity, pending.variant());
    }

    // ---------------------------------------------------------------- syncing to players who load the chunk

    /** Loader hook: {@code chunk} was just sent to {@code player}. */
    public static void onChunkSent(ServerPlayer player, LevelChunk chunk) {
        var platform = Platform.INSTANCE;
        var entries = new ArrayList<PaintSyncPayload.Entry>();
        for (var blockEntity : chunk.getBlockEntities().values()) {
            if (isPaintable(blockEntity) && platform.hasAddonPaint(blockEntity)) {
                entries.add(PaintSyncPayload.Entry.onBlockEntity(blockEntity.getBlockPos(), platform.getAddonPaint(blockEntity)));
            }
        }
        if (!entries.isEmpty()) platform.sendToPlayer(player, new PaintSyncPayload(entries));
    }
}
