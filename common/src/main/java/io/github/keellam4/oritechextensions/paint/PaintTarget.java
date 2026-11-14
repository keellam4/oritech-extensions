package io.github.keellam4.oritechextensions.paint;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import rearth.oritech.util.ColorableMachine.ColorVariant;

import java.util.List;

/**
 * One kind of thing this mod can paint: machine addons, pipe networks, Industrial Glass, reactors, hangar doors.
 * <p>
 * Color Cartridges (and later the Detailing Tool) only talk to this interface, through {@link PaintTargets}.
 * To make a new block paintable, write a target and add it to the list there.
 */
public interface PaintTarget {

    /** Quick check by block type. Also decides which block models are made paintable on the client. */
    boolean matchesBlock(Block block);

    /** Whether the block at {@code pos} can be painted through this target. Safe on both sides. */
    default boolean matches(Level level, BlockPos pos) {
        return matchesBlock(level.getBlockState(pos).getBlock());
    }

    /**
     * Everything that is painted together with {@code pos}: the block alone, a pipe network, a reactor,
     * a door. In the order it should be painted when the paint runs out part-way.
     */
    List<BlockPos> group(Level level, BlockPos pos);

    /** Current colour at {@code pos}; ORANGE means unpainted. */
    ColorVariant get(ServerLevel level, BlockPos pos);

    /**
     * Paints {@code group} (ORANGE clears), stopping after {@code budget} blocks have changed, and tells nearby
     * players.
     *
     * @return the positions whose colour changed.
     */
    List<BlockPos> apply(ServerLevel level, List<BlockPos> group, ColorVariant variant, int budget);

    /**
     * Client-side shortcut: true when a click with {@code variant} would change nothing, so the click is left
     * alone. Targets whose colours the client can't see return false; the server decides for them.
     */
    default boolean alreadyPainted(Level level, BlockPos pos, ColorVariant variant) {
        return false;
    }

    /** False for blocks that aren't drawn with a normal block model (the hangar door extension). */
    default boolean wrapsModels() {
        return true;
    }
}
