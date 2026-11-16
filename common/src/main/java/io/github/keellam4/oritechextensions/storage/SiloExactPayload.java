package io.github.keellam4.oritechextensions.storage;

import io.github.keellam4.oritechextensions.OritechExtensions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → server: an exact limit or reserve, in millibuckets, typed into the Silo Controller screen. */
public record SiloExactPayload(int containerId, int row, boolean reserve, long millibuckets) implements CustomPacketPayload {

    public static final Type<SiloExactPayload> TYPE = new Type<>(OritechExtensions.id("silo_exact"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SiloExactPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SiloExactPayload::containerId,
            ByteBufCodecs.VAR_INT, SiloExactPayload::row,
            ByteBufCodecs.BOOL, SiloExactPayload::reserve,
            ByteBufCodecs.VAR_LONG, SiloExactPayload::millibuckets,
            SiloExactPayload::new);

    public static final String VERSION = "1";

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Server side: applies it to the player's open Silo Controller screen. */
    public static void handle(SiloExactPayload payload, net.minecraft.world.entity.player.Player player) {
        if (player.containerMenu instanceof SiloMenu menu && menu.containerId == payload.containerId()) {
            menu.setExact(payload.row(), payload.reserve(), payload.millibuckets());
        }
    }
}
