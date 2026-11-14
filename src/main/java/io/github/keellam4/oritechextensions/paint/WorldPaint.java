package io.github.keellam4.oritechextensions.paint;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;
import rearth.oritech.util.ColorableMachine.ColorVariant;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Shared helpers for blocks whose paint is stored by position in {@link PipePaintData} rather than on a block
 * entity: pipes, Industrial Glass, reactor blocks and particle accelerator blocks.
 * <p>
 * A "family" function says which blocks belong together: it returns the same non-null value for blocks that
 * paint as one group, and null for everything else.
 */
public final class WorldPaint {

    private WorldPaint() {
    }

    /**
     * Connected blocks of the same family as {@code origin}, nearest first, at most {@code max} of them.
     * <p>
     * {@code reach} is how far apart two blocks may be, in a straight line, and still count as connected:
     * 1 means touching. Particle accelerator rings are built with gaps, so they use a longer reach.
     */
    public static List<BlockPos> discover(Level level, BlockPos origin, Function<Block, Object> family, int max, int reach) {
        var originFamily = family.apply(level.getBlockState(origin).getBlock());
        if (originFamily == null || max <= 0) return List.of();

        var ordered = new LinkedHashSet<BlockPos>();
        var queue = new ArrayDeque<BlockPos>();
        queue.add(origin.immutable());
        ordered.add(origin.immutable());

        while (!queue.isEmpty() && ordered.size() < max) {
            var pos = queue.removeFirst();
            for (var direction : Direction.values()) {
                for (int distance = 1; distance <= reach && ordered.size() < max; distance++) {
                    var neighbor = pos.relative(direction, distance);
                    if (ordered.contains(neighbor)) continue;
                    if (!Objects.equals(family.apply(level.getBlockState(neighbor).getBlock()), originFamily)) continue;
                    var frozen = neighbor.immutable();
                    ordered.add(frozen);
                    queue.add(frozen);
                }
            }
        }
        return new ArrayList<>(ordered);
    }

    /**
     * Colours {@code group} in order (ORANGE clears), stopping after {@code budget} changes.
     *
     * @return the positions whose stored colour changed.
     */
    public static List<BlockPos> apply(ServerLevel level, List<BlockPos> group, ColorVariant variant, int budget) {
        if (budget <= 0) return List.of();
        var data = PipePaintData.get(level);
        var changed = new ArrayList<BlockPos>();
        for (var pos : group) {
            if (changed.size() >= budget) break;
            if (data.getOrDefault(pos) == variant) continue;
            data.set(pos, variant);
            changed.add(pos);
        }
        return changed;
    }

    /** The colour a newly placed block takes over: its painted same-family neighbours' colour, if they all agree. */
    @Nullable
    public static ColorVariant inheritanceColor(ServerLevel level, BlockPos pos, Function<Block, Object> family, int reach) {
        var ownFamily = family.apply(level.getBlockState(pos).getBlock());
        if (ownFamily == null) return null;

        var data = PipePaintData.get(level);
        ColorVariant agreed = null;
        for (var direction : Direction.values()) {
            for (int distance = 1; distance <= reach; distance++) {
                var neighbor = pos.relative(direction, distance);
                if (!Objects.equals(family.apply(level.getBlockState(neighbor).getBlock()), ownFamily)) continue;
                var paint = data.get(neighbor);
                if (!PaintColors.isPainted(paint)) continue;
                if (agreed == null) {
                    agreed = paint;
                } else if (agreed != paint) {
                    return null; // conflicting neighbours
                }
            }
        }
        return agreed;
    }
}
