package io.github.keellam4.oritechextensions.paint;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;
import rearth.oritech.init.BlockContent;

import java.util.List;

/**
 * Which Oritech pipes can be painted, and helpers for their networks. The shared logic is in {@link WorldPaint}.
 * Transparent, framed and duct versions count as their pipe type, because Oritech joins them into the same network.
 * Energy pipes and superconductors are separate families (separate networks in Oritech too).
 */
public final class PipePaint {

    public enum Family { ITEM, FLUID, ENERGY, SUPERCONDUCTOR }

    private PipePaint() {
    }

    public static boolean isPaintablePipe(Block block) {
        return familyOf(block) != null;
    }

    public static boolean isPaintablePipe(Level level, BlockPos pos) {
        return isPaintablePipe(level.getBlockState(pos).getBlock());
    }

    @Nullable
    public static Family familyOf(Block block) {
        if (block == BlockContent.ITEM_PIPE || block == BlockContent.ITEM_PIPE_CONNECTION
                || block == BlockContent.TRANSPARENT_ITEM_PIPE || block == BlockContent.TRANSPARENT_ITEM_PIPE_CONNECTION
                || block == BlockContent.FRAMED_ITEM_PIPE || block == BlockContent.FRAMED_ITEM_PIPE_CONNECTION
                || block == BlockContent.ITEM_PIPE_DUCT_BLOCK) {
            return Family.ITEM;
        }
        if (block == BlockContent.FLUID_PIPE || block == BlockContent.FLUID_PIPE_CONNECTION
                || block == BlockContent.FRAMED_FLUID_PIPE || block == BlockContent.FRAMED_FLUID_PIPE_CONNECTION
                || block == BlockContent.FLUID_PIPE_DUCT_BLOCK
                || block instanceof io.github.keellam4.oritechextensions.pipes.TransparentFluidPipeBlock
                || block instanceof io.github.keellam4.oritechextensions.pipes.TransparentFluidPipeConnectionBlock) {
            return Family.FLUID;
        }
        if (block == BlockContent.ENERGY_PIPE || block == BlockContent.ENERGY_PIPE_CONNECTION
                || block == BlockContent.FRAMED_ENERGY_PIPE || block == BlockContent.FRAMED_ENERGY_PIPE_CONNECTION
                || block == BlockContent.ENERGY_PIPE_DUCT_BLOCK) {
            return Family.ENERGY;
        }
        if (block == BlockContent.SUPERCONDUCTOR || block == BlockContent.SUPERCONDUCTOR_CONNECTION
                || block == BlockContent.FRAMED_SUPERCONDUCTOR || block == BlockContent.FRAMED_SUPERCONDUCTOR_CONNECTION
                || block == BlockContent.SUPERCONDUCTOR_DUCT_BLOCK) {
            return Family.SUPERCONDUCTOR;
        }
        return null;
    }

    /** The connected standard-pipe network of the same family, starting at {@code origin}. */
    public static List<BlockPos> discoverNetwork(Level level, BlockPos origin) {
        return WorldPaint.discover(level, origin, PipePaint::familyOf, Integer.MAX_VALUE, 1);
    }

    public static Paint getPaint(ServerLevel level, BlockPos pos) {
        return PipePaintData.get(level).getOrDefault(pos);
    }

    /** Colour every position in {@code network}. Orange clears. Returns positions whose stored colour changed. */
    public static List<BlockPos> applyToNetwork(ServerLevel level, List<BlockPos> network, Paint variant) {
        return WorldPaint.apply(level, network, variant, Integer.MAX_VALUE);
    }

    /** Like {@link #applyToNetwork}, but at most {@code budget} changes, in network order. */
    public static List<BlockPos> applyToNetworkBudgeted(ServerLevel level, List<BlockPos> network, Paint variant, int budget) {
        return WorldPaint.apply(level, network, variant, budget);
    }

    /** Inherit paint from same-family painted neighbours when they all agree on one colour. */
    public static @Nullable Paint inheritanceColor(ServerLevel level, BlockPos pos) {
        return WorldPaint.inheritanceColor(level, pos, PipePaint::familyOf, 1);
    }

    public static void clearAt(ServerLevel level, BlockPos pos) {
        PipePaintData.get(level).remove(pos);
    }
}
