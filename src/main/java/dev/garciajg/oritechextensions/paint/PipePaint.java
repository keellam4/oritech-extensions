package dev.garciajg.oritechextensions.paint;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;
import rearth.oritech.init.BlockContent;
import rearth.oritech.util.ColorableMachine.ColorVariant;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Helpers for discovering and colouring standard Oritech item/fluid pipe networks.
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

    /** BFS over the connected standard-pipe network of the same family, starting at {@code origin}. */
    public static List<BlockPos> discoverNetwork(Level level, BlockPos origin) {
        var originBlock = level.getBlockState(origin).getBlock();
        var family = familyOf(originBlock);
        if (family == null) return List.of();

        var ordered = new LinkedHashSet<BlockPos>();
        var queue = new ArrayDeque<BlockPos>();
        queue.add(origin.immutable());
        ordered.add(origin.immutable());

        while (!queue.isEmpty()) {
            var pos = queue.removeFirst();
            for (var direction : Direction.values()) {
                var neighbor = pos.relative(direction);
                if (ordered.contains(neighbor)) continue;
                if (familyOf(level.getBlockState(neighbor).getBlock()) != family) continue;
                var frozen = neighbor.immutable();
                ordered.add(frozen);
                queue.add(frozen);
            }
        }
        return new ArrayList<>(ordered);
    }

    public static ColorVariant getPaint(ServerLevel level, BlockPos pos) {
        return PipePaintData.get(level).getOrDefault(pos);
    }

    /**
     * Colour every position in {@code network}. Orange clears. Returns positions whose stored colour changed.
     */
    public static List<BlockPos> applyToNetwork(ServerLevel level, List<BlockPos> network, ColorVariant variant) {
        var data = PipePaintData.get(level);
        var changed = new ArrayList<BlockPos>();
        for (var pos : network) {
            var before = data.getOrDefault(pos);
            if (before == variant) continue;
            data.set(pos, variant);
            changed.add(pos);
        }
        return changed;
    }

    /**
     * Apply colour to a prefix of {@code network} (BFS order), at most {@code budget} changes.
     * Returns the changed positions.
     */
    public static List<BlockPos> applyToNetworkBudgeted(ServerLevel level, List<BlockPos> network, ColorVariant variant, int budget) {
        if (budget <= 0) return List.of();
        var data = PipePaintData.get(level);
        var changed = new ArrayList<BlockPos>();
        for (var pos : network) {
            if (changed.size() >= budget) break;
            var before = data.getOrDefault(pos);
            if (before == variant) continue;
            data.set(pos, variant);
            changed.add(pos);
        }
        return changed;
    }

    /** Inherit paint from same-family painted neighbours when they all agree on one colour. */
    public static @Nullable ColorVariant inheritanceColor(ServerLevel level, BlockPos pos) {
        var family = familyOf(level.getBlockState(pos).getBlock());
        if (family == null) return null;

        var data = PipePaintData.get(level);
        ColorVariant agreed = null;
        for (var direction : Direction.values()) {
            var neighbor = pos.relative(direction);
            if (familyOf(level.getBlockState(neighbor).getBlock()) != family) continue;
            var paint = data.get(neighbor);
            if (!PaintColors.isPainted(paint)) continue;
            if (agreed == null) {
                agreed = paint;
            } else if (agreed != paint) {
                return null; // conflicting neighbours
            }
        }
        return agreed;
    }

    public static void clearAt(ServerLevel level, BlockPos pos) {
        PipePaintData.get(level).remove(pos);
    }
}
