package io.github.keellam4.oritechextensions.storage;

import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/** Some fluid, in millibuckets (Fabric code converts to droplets at its edge). */
public record FluidAmount(Fluid fluid, long amount) {

    public static final FluidAmount EMPTY = new FluidAmount(Fluids.EMPTY, 0);

    public boolean isEmpty() {
        return fluid == Fluids.EMPTY || amount <= 0;
    }
}
