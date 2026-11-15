package io.github.keellam4.oritechextensions.storage;

import io.github.keellam4.oritechextensions.OritechExtensions;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → server: the player left-clicked a vault's front; take a stack (or one item when sneaking) from that slot. */
public record VaultTakePayload(BlockPos pos, int slot, boolean one) implements CustomPacketPayload {

    public static final Type<VaultTakePayload> TYPE = new Type<>(OritechExtensions.id("vault_take"));

    public static final StreamCodec<ByteBuf, VaultTakePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, VaultTakePayload::pos,
            ByteBufCodecs.VAR_INT, VaultTakePayload::slot,
            ByteBufCodecs.BOOL, VaultTakePayload::one,
            VaultTakePayload::new);

    public static final String VERSION = "2";

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
