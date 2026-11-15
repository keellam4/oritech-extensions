package io.github.keellam4.oritechextensions.storage;

import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

/** A fluid tank that can join a Cargo network: a Fluid Vault or a Fluid Silo. Amounts are millibuckets. */
public interface NetworkTank {

    Fluid fluid();

    long amount();

    long fluidCapacity();

    boolean acceptsFluid(Fluid offered);

    /** Fills up to {@code max}. Returns how much was taken. */
    long fill(Fluid offered, long max, boolean simulate);

    /** Drains up to {@code max} of {@code wanted} (or of whatever it holds, when null). */
    FluidAmount drain(@Nullable Fluid wanted, long max, boolean simulate);

    long roomFor(Fluid offered);

    FluidContents fluidContents();

    /** Restores a snapshot (Fabric transactions). */
    void loadFluid(FluidContents contents);

    /** Saves and syncs after a change. */
    void changed();

    boolean isRemoved();

    /** Every fluid it holds right now. A Fluid Vault holds one; a partitioned Fluid Silo can hold several. */
    default java.util.List<FluidAmount> fluids() {
        return amount() > 0 ? java.util.List.of(new FluidAmount(fluid(), amount())) : java.util.List.of();
    }

    default boolean holds(Fluid wanted) {
        for (var held : fluids()) if (held.fluid().isSame(wanted)) return true;
        return false;
    }

    /** A copy of everything it holds, for undoing a Fabric transaction. */
    default Object saveFluidState() {
        return fluidContents();
    }

    default void restoreFluidState(Object state) {
        loadFluid((FluidContents) state);
    }
}
