package io.github.keellam4.oritechextensions.pipes;

import io.github.keellam4.oritechextensions.platform.Platform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.material.Fluid;

import java.util.HashMap;
import java.util.Map;

/**
 * Server side of the fluid shown in Transparent Fluid Pipes (JNI-98). Oritech's fluid pipes keep no fluid in the
 * pipe blocks: an extracting pipe end moves it straight to its targets. A hook on that move
 * ({@code FluidPipeInterfaceEntityMixin}) reports it here, and nearby players hear which fluid each end is moving,
 * at most once a second per end (or at once when the fluid changes). The client draws it ({@code FluidFlowRenderer}).
 */
public final class FluidFlows {

    /** How often (ticks) a still-flowing pipe end is reported again. */
    static final int REPEAT_TICKS = 20;

    private record Sent(Fluid fluid, long at) {
    }

    private static final Map<ServerLevel, Map<Long, Sent>> SENT = new java.util.WeakHashMap<>();

    private FluidFlows() {
    }

    /** A fluid pipe end at {@code pos} just moved some {@code fluid}. */
    public static void moved(ServerLevel level, BlockPos pos, Fluid fluid) {
        long now = level.getGameTime();
        var sent = SENT.computeIfAbsent(level, l -> new HashMap<>());
        var last = sent.get(pos.asLong());
        if (last != null && last.fluid == fluid && now - last.at < REPEAT_TICKS && now >= last.at) return;
        sent.put(pos.asLong(), new Sent(fluid, now));
        if (sent.size() > 4096) sent.entrySet().removeIf(entry -> now - entry.getValue().at > 200);
        Platform.INSTANCE.sendToPlayersTrackingChunk(level, new ChunkPos(pos),
                new FluidFlowPayload(pos.immutable(), BuiltInRegistries.FLUID.getKey(fluid)));
    }
}
