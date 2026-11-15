package io.github.keellam4.oritechextensions.storage;

import io.github.keellam4.oritechextensions.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * The shape of a Fluid Silo: a solid 3 × 3 column of Silo Casing and Silo Glass, 3 to 9 blocks tall, with its
 * Silo Controller somewhere in the bottom layer. {@code origin} is the bottom north-west corner.
 */
public record SiloStructure(BlockPos origin, int height) {

    public static final int WIDTH = 3;
    public static final int MIN_HEIGHT = 3;
    public static final int MAX_HEIGHT = 9;

    public int volume() {
        return WIDTH * WIDTH * height;
    }

    public boolean contains(BlockPos pos) {
        return pos.getX() >= origin.getX() && pos.getX() < origin.getX() + WIDTH
                && pos.getZ() >= origin.getZ() && pos.getZ() < origin.getZ() + WIDTH
                && pos.getY() >= origin.getY() && pos.getY() < origin.getY() + height;
    }

    public AABB bounds() {
        return new AABB(origin.getX(), origin.getY(), origin.getZ(),
                origin.getX() + WIDTH, origin.getY() + height, origin.getZ() + WIDTH);
    }

    /** The tallest silo the controller at {@code controller} completes, or null. */
    @Nullable
    public static SiloStructure find(Level level, BlockPos controller) {
        SiloStructure best = null;
        for (int dx = 0; dx < WIDTH; dx++) {
            for (int dz = 0; dz < WIDTH; dz++) {
                var origin = controller.offset(-dx, 0, -dz);
                int height = 0;
                while (height < MAX_HEIGHT && layerComplete(level, origin.above(height), controller)) height++;
                if (height >= MIN_HEIGHT && (best == null || height > best.height)) best = new SiloStructure(origin, height);
            }
        }
        return best;
    }

    private static boolean layerComplete(Level level, BlockPos corner, BlockPos controller) {
        for (int x = 0; x < WIDTH; x++) {
            for (int z = 0; z < WIDTH; z++) {
                var pos = corner.offset(x, 0, z);
                if (pos.equals(controller)) continue;
                if (!level.isLoaded(pos)) return false;
                var state = level.getBlockState(pos);
                if (!state.is(ModContent.SILO_CASING.get()) && !state.is(ModContent.SILO_GLASS.get())) return false;
            }
        }
        return true;
    }
}
