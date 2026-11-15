package io.github.keellam4.oritechextensions.storage;

import io.github.keellam4.oritechextensions.OritechExtensions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/** Client → server: a click in the terminal's item grid (see {@link TerminalMenu} for the actions). */
public record TerminalActionPayload(int containerId, int action, ItemStack item, net.minecraft.world.level.material.Fluid fluid) implements CustomPacketPayload {

    public static final Type<TerminalActionPayload> TYPE = new Type<>(OritechExtensions.id("terminal_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TerminalActionPayload::containerId,
            ByteBufCodecs.VAR_INT, TerminalActionPayload::action,
            ItemStack.OPTIONAL_STREAM_CODEC, TerminalActionPayload::item,
            ByteBufCodecs.registry(net.minecraft.core.registries.Registries.FLUID), TerminalActionPayload::fluid,
            TerminalActionPayload::new);

    public static final String VERSION = "2";

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
