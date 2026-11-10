package dev.garciajg.oritechextensions.paint;

import dev.garciajg.oritechextensions.OritechExtensions;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * Server → client: pipe paint colours. On clear, colour id is ORANGE and the client drops the cache entry.
 * Chunk sync only sends currently painted positions.
 */
public record PipePaintSyncPayload(List<Entry> entries) implements CustomPacketPayload {

    public record Entry(BlockPos pos, int color) {
        static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Entry::pos,
                ByteBufCodecs.VAR_INT, Entry::color,
                Entry::new);
    }

    public static final Type<PipePaintSyncPayload> TYPE = new Type<>(OritechExtensions.id("pipe_paint_sync"));

    public static final StreamCodec<ByteBuf, PipePaintSyncPayload> STREAM_CODEC =
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list()).map(PipePaintSyncPayload::new, PipePaintSyncPayload::entries);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, PipePaintSyncPayload::handleOnClient);
    }

    private static void handleOnClient(PipePaintSyncPayload payload, IPayloadContext context) {
        dev.garciajg.oritechextensions.client.PipePaintClient.handleSync(payload);
    }
}
