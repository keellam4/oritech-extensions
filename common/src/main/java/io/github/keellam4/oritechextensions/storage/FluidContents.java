package io.github.keellam4.oritechextensions.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/** What a Fluid Vault holds, kept on its item: the fluid, how much (in millibuckets), and the lock. */
public record FluidContents(Fluid fluid, long amount, boolean locked) {

    public static final FluidContents EMPTY = new FluidContents(Fluids.EMPTY, 0, false);

    public static final Codec<FluidContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BuiltInRegistries.FLUID.byNameCodec().optionalFieldOf("fluid", Fluids.EMPTY).forGetter(FluidContents::fluid),
            Codec.LONG.optionalFieldOf("amount", 0L).forGetter(FluidContents::amount),
            Codec.BOOL.optionalFieldOf("locked", false).forGetter(FluidContents::locked)
    ).apply(instance, FluidContents::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, FluidContents> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.registry(Registries.FLUID), FluidContents::fluid,
            ByteBufCodecs.VAR_LONG, FluidContents::amount,
            ByteBufCodecs.BOOL, FluidContents::locked,
            FluidContents::new);

    public FluidContents {
        if (fluid == Fluids.EMPTY) amount = 0;
        amount = Math.max(0, amount);
    }

    public boolean isEmpty() {
        return fluid == Fluids.EMPTY;
    }
}
