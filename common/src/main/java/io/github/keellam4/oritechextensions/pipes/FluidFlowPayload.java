package io.github.keellam4.oritechextensions.pipes;

import io.github.keellam4.oritechextensions.OritechExtensions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server → client: the fluid pipe end at {@code pos} is moving {@code fluid} (see {@link FluidFlows}). */
public record FluidFlowPayload(BlockPos pos, ResourceLocation fluid) implements CustomPacketPayload {

    public static final Type<FluidFlowPayload> TYPE = new Type<>(OritechExtensions.id("fluid_flow"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FluidFlowPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, FluidFlowPayload::pos,
            ResourceLocation.STREAM_CODEC, FluidFlowPayload::fluid,
            FluidFlowPayload::new);

    public static final String VERSION = "1";

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
