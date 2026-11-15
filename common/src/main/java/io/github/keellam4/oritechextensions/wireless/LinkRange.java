package io.github.keellam4.oritechextensions.wireless;

import io.github.keellam4.oritechextensions.Config;
import io.github.keellam4.oritechextensions.storage.CargoControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import java.util.ArrayList;

/**
 * How far a Cargo Link reaches from its Cargo Controller: {@code linkRange} with no mast; with an Uplink Mast
 * touching any block of the Cargo Wall, {@code linkMastRange} plus {@code linkSegmentRange} for each Antenna
 * Segment stacked on top of it (up to 8). The best mast counts.
 */
public final class LinkRange {

    public static final int MAX_SEGMENTS = 8;

    private LinkRange() {
    }

    public static int range(Level level, CargoControllerBlockEntity controller) {
        int best = Config.linkRange();
        var wall = new ArrayList<BlockPos>();
        wall.add(controller.getBlockPos());
        for (var vault : controller.network().vaults()) wall.add(vault.getBlockPos());
        for (var pos : wall) {
            for (var direction : Direction.values()) {
                var next = pos.relative(direction);
                if (level.getBlockState(next).getBlock() instanceof UplinkMastBlock) best = Math.max(best, mastRange(level, next));
            }
        }
        return best;
    }

    public static int mastRange(Level level, BlockPos mast) {
        return Config.linkMastRange() + Config.linkSegmentRange() * segments(level, mast);
    }

    /** Antenna Segments stacked directly on top of the mast. */
    public static int segments(Level level, BlockPos mast) {
        int count = 0;
        while (count < MAX_SEGMENTS && level.getBlockState(mast.above(count + 1)).getBlock() instanceof AntennaSegmentBlock) count++;
        return count;
    }

    /** Signal bars, 0 to 4, at {@code distance} from a network that reaches {@code range}. */
    public static int bars(double distance, int range) {
        if (distance > range) return 0;
        return Math.max(1, (int) Math.ceil(4 * (1 - distance / range)));
    }
}
