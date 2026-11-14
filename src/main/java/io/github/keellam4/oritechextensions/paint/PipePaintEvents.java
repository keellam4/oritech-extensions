package io.github.keellam4.oritechextensions.paint;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Kept for callers that predate {@link PaintTargets}. Pipe clicks, placement, breaking and syncing are all
 * handled there now, together with every other paintable block.
 */
public final class PipePaintEvents {

    private PipePaintEvents() {
    }

    /**
     * Paints (or clears) the pipe network at {@code pos}.
     *
     * @return false if the block can't be painted, or nothing would change and no cartridges would be used.
     */
    public static boolean tryPaint(ServerLevel level, BlockPos pos, Player player, InteractionHand hand, ItemStack stack) {
        return PaintTargets.tryPaint(level, pos, player, hand, stack);
    }
}
