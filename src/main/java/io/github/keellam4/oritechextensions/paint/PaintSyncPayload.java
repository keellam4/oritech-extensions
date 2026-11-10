package io.github.keellam4.oritechextensions.paint;

import io.github.keellam4.oritechextensions.OritechExtensions;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * Server → client: paint colours for a set of block positions. Sent when a block is painted, and for every
 * painted block in a chunk when that chunk is sent to a player. Colour id = ColorVariant ordinal (ORANGE = unpainted).
 */
public record PaintSyncPayload(List<Entry> entries) implements CustomPacketPayload {

    public record Entry(BlockPos pos, int color) {
        static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Entry::pos,
                ByteBufCodecs.VAR_INT, Entry::color,
                Entry::new);
    }

    public static final Type<PaintSyncPayload> TYPE = new Type<>(OritechExtensions.id("paint_sync"));

    public static final StreamCodec<ByteBuf, PaintSyncPayload> STREAM_CODEC =
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list()).map(PaintSyncPayload::new, PaintSyncPayload::entries);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, PaintSyncPayload::handleOnClient);
    }

    private static void handleOnClient(PaintSyncPayload payload, IPayloadContext context) {
        // Separate class so client-only code is never loaded on a dedicated server.
        io.github.keellam4.oritechextensions.client.PaintClient.handleSync(payload);
    }
}
