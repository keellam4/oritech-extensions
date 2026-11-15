package io.github.keellam4.oritechextensions.storage;

import io.github.keellam4.oritechextensions.OritechExtensions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Server → client: what's in the network of the terminal the player has open. */
public record TerminalSyncPayload(int containerId, int vaults, List<CargoNetwork.Entry> entries) implements CustomPacketPayload {

    private static final StreamCodec<RegistryFriendlyByteBuf, CargoNetwork.Entry> ENTRY = StreamCodec.composite(
            ItemStack.STREAM_CODEC, CargoNetwork.Entry::item,
            ByteBufCodecs.VAR_LONG, CargoNetwork.Entry::count,
            CargoNetwork.Entry::new);

    public static final Type<TerminalSyncPayload> TYPE = new Type<>(OritechExtensions.id("terminal_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TerminalSyncPayload::containerId,
            ByteBufCodecs.VAR_INT, TerminalSyncPayload::vaults,
            ENTRY.apply(ByteBufCodecs.list()), TerminalSyncPayload::entries,
            TerminalSyncPayload::new);

    public static final String VERSION = "1";

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
