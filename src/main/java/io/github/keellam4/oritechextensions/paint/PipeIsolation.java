package io.github.keellam4.oritechextensions.paint;

import io.github.keellam4.oritechextensions.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import rearth.oritech.block.blocks.pipes.AbstractPipeBlock;
import rearth.oritech.block.entity.pipes.GenericPipeInterfaceEntity;
import rearth.oritech.util.ColorableMachine.ColorVariant;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Colour-isolated pipes (config {@code pipePaintIsolation}).
 * <p>
 * Two standard pipes of the same kind connect unless both are painted and their colours differ. Unpainted pipes
 * connect to every colour, so they work as a bridge. Only standard item pipes (with transparent ones) and
 * standard fluid pipes are affected; framed pipes and ducts can't be painted.
 * <p>
 * The connection check itself is a mixin into Oritech's {@code GenericPipeBlock.shouldConnect}. When paint
 * changes, {@link #onPaintChanged} updates the pipes on both sides of every colour edge that changed, and the
 * pipe networks they belong to.
 */
public final class PipeIsolation {

    private PipeIsolation() {
    }

    public static boolean enabled() {
        return Config.pipePaintIsolation();
    }

    /** Whether two pipes of the same kind with these colours may connect. */
    public static boolean compatible(ColorVariant a, ColorVariant b) {
        return !PaintColors.isPainted(a) || !PaintColors.isPainted(b) || a == b;
    }

    /** Called from the mixin: true when the pipe at {@code pos} must not connect towards {@code direction}. */
    public static boolean blocksConnection(Level level, BlockPos pos, Direction direction) {
        if (!(level instanceof ServerLevel server) || !enabled()) return false;
        var family = PipePaint.familyOf(level.getBlockState(pos).getBlock());
        if (family == null) return false;
        var neighbor = pos.relative(direction);
        if (family != PipePaint.familyOf(level.getBlockState(neighbor).getBlock())) return false;
        var data = PipePaintData.get(server);
        return !compatible(data.getOrDefault(pos), data.getOrDefault(neighbor));
    }

    /**
     * Paint at the keys of {@code before} just changed from the given colours to what is stored now. Pipes on a
     * colour edge that became incompatible disconnect; edges that became compatible reconnect (on both sides).
     * The affected pipe networks are then rebuilt, so items and fluids stop (or start) crossing over.
     */
    public static void onPaintChanged(ServerLevel level, Map<BlockPos, ColorVariant> before) {
        if (!enabled() || before.isEmpty()) return;
        var data = PipePaintData.get(level);

        var affected = new LinkedHashSet<BlockPos>();
        var reconnect = new LinkedHashMap<BlockPos, Set<Direction>>();
        for (var entry : before.entrySet()) {
            var pos = entry.getKey();
            var family = PipePaint.familyOf(level.getBlockState(pos).getBlock());
            if (family == null) continue;
            for (var direction : Direction.values()) {
                var neighbor = pos.relative(direction);
                if (!Objects.equals(family, PipePaint.familyOf(level.getBlockState(neighbor).getBlock()))) continue;
                var neighborBefore = before.containsKey(neighbor) ? before.get(neighbor) : data.getOrDefault(neighbor);
                boolean was = compatible(entry.getValue(), neighborBefore);
                boolean now = compatible(data.getOrDefault(pos), data.getOrDefault(neighbor));
                if (was == now) continue;
                affected.add(pos);
                affected.add(neighbor.immutable());
                if (now) {
                    reconnect.computeIfAbsent(pos, p -> EnumSet.noneOf(Direction.class)).add(direction);
                    reconnect.computeIfAbsent(neighbor.immutable(), p -> EnumSet.noneOf(Direction.class)).add(direction.getOpposite());
                }
            }
        }
        if (affected.isEmpty()) return;

        // New block states first (both sides), then the networks, so the flood fill sees the final connections.
        for (var pos : affected) {
            var state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof AbstractPipeBlock pipe)) continue;
            var directions = reconnect.get(pos);
            BlockState next;
            if (directions == null) {
                next = pipe.addConnectionStates(state, level, pos, false);
            } else {
                next = state;
                for (var direction : directions) next = pipe.addConnectionStates(next, level, pos, direction);
            }
            if (!next.equals(state)) level.setBlock(pos, next, Block.UPDATE_ALL);
        }
        for (var pos : affected) {
            var state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof AbstractPipeBlock pipe)) continue;
            boolean isInterface = state.is(pipe.getConnectionBlock().getBlock());
            GenericPipeInterfaceEntity.addNode(level, pos, isInterface, state, pipe.getNetworkData(level));
        }
    }
}
