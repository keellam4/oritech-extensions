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

    /**
     * Whether the tank's block is gone. (Not named isRemoved: on Fabric, Minecraft's own isRemoved has an
     * obfuscated name at runtime, so a block entity's inherited one wouldn't implement an interface method of ours.)
     */
    boolean tankRemoved();
}
