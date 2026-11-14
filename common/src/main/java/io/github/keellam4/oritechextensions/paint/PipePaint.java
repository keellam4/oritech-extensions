package io.github.keellam4.oritechextensions.paint;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;
import rearth.oritech.init.BlockContent;
import rearth.oritech.util.ColorableMachine.ColorVariant;

import java.util.List;

/**
 * Which Oritech pipes can be painted, and helpers for their networks. The shared logic is in {@link WorldPaint}.
 * Transparent item pipes count as item pipes (Oritech joins them into the same network). Framed and duct pipes are
 * excluded (match by block identity).
 */
public final class PipePaint {

    public enum Family { ITEM, FLUID }

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
                || block == BlockContent.TRANSPARENT_ITEM_PIPE || block == BlockContent.TRANSPARENT_ITEM_PIPE_CONNECTION) {
            return Family.ITEM;
        }
        if (block == BlockContent.FLUID_PIPE || block == BlockContent.FLUID_PIPE_CONNECTION) {
            return Family.FLUID;
        }
        return null;
    }

    /** The connected standard-pipe network of the same family, starting at {@code origin}. */
    public static List<BlockPos> discoverNetwork(Level level, BlockPos origin) {
        return WorldPaint.discover(level, origin, PipePaint::familyOf, Integer.MAX_VALUE, 1);
    }

    public static ColorVariant getPaint(ServerLevel level, BlockPos pos) {
        return PipePaintData.get(level).getOrDefault(pos);
    }

    /** Colour every position in {@code network}. Orange clears. Returns positions whose stored colour changed. */
    public static List<BlockPos> applyToNetwork(ServerLevel level, List<BlockPos> network, ColorVariant variant) {
        return WorldPaint.apply(level, network, variant, Integer.MAX_VALUE);
    }

    /** Like {@link #applyToNetwork}, but at most {@code budget} changes, in network order. */
    public static List<BlockPos> applyToNetworkBudgeted(ServerLevel level, List<BlockPos> network, ColorVariant variant, int budget) {
        return WorldPaint.apply(level, network, variant, budget);
    }

    /** Inherit paint from same-family painted neighbours when they all agree on one colour. */
    public static @Nullable ColorVariant inheritanceColor(ServerLevel level, BlockPos pos) {
        return WorldPaint.inheritanceColor(level, pos, PipePaint::familyOf, 1);
    }

    public static void clearAt(ServerLevel level, BlockPos pos) {
        PipePaintData.get(level).remove(pos);
    }
}
