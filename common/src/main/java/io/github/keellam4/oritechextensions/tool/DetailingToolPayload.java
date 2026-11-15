package io.github.keellam4.oritechextensions.tool;

import io.github.keellam4.oritechextensions.OritechExtensions;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → server: the player scrolled while sneaking with a Detailing Tool; switch to the next (+1) or previous (-1) colour. */
public record DetailingToolPayload(int direction) implements CustomPacketPayload {

    public static final Type<DetailingToolPayload> TYPE = new Type<>(OritechExtensions.id("detailing_tool"));

    public static final StreamCodec<ByteBuf, DetailingToolPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(DetailingToolPayload::new, DetailingToolPayload::direction);

    public static final String VERSION = "1";

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
