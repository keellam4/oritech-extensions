package io.github.keellam4.oritechextensions.client;

import io.github.keellam4.oritechextensions.paint.PaintColors;
import io.github.keellam4.oritechextensions.paint.PipePaintSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;
import rearth.oritech.util.ColorableMachine.ColorVariant;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Client-side cache of pipe paint colours (world-only; not on block entities). */
public final class PipePaintClient {

    private static final Map<BlockPos, ColorVariant> CACHE = new ConcurrentHashMap<>();

    private PipePaintClient() {
    }

    @Nullable
    public static ColorVariant get(BlockPos pos) {
        return CACHE.get(pos);
    }

    public static void handleSync(PipePaintSyncPayload payload) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;

        for (var entry : payload.entries()) {
            var variant = PaintColors.fromId(entry.color());
            var pos = entry.pos();
            if (PaintColors.isPainted(variant)) {
                CACHE.put(pos.immutable(), variant);
            } else {
                CACHE.remove(pos);
            }
            var state = level.getBlockState(pos);
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_IMMEDIATE);
        }
    }

    /** Clears cache when leaving a world (optional hygiene). */
    public static void clear() {
        CACHE.clear();
    }
}
