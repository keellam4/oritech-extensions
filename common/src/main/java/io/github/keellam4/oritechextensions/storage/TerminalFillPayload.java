package io.github.keellam4.oritechextensions.storage;

import io.github.keellam4.oritechextensions.OritechExtensions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client → server: JEI's "+" on a crafting recipe, to fill the Crafting Terminal's grid (see {@link TerminalMenu#fill}). */
public record TerminalFillPayload(int containerId, ResourceLocation recipe) implements CustomPacketPayload {

    public static final Type<TerminalFillPayload> TYPE = new Type<>(OritechExtensions.id("terminal_fill"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalFillPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TerminalFillPayload::containerId,
            ResourceLocation.STREAM_CODEC, TerminalFillPayload::recipe,
            TerminalFillPayload::new);

    public static final String VERSION = "1";

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
