package io.github.keellam4.oritechextensions;

/** A block entity that stores energy from cables (Induction Pylon, Crafting Terminal); the loaders wrap it for their energy API. */
public interface EnergyBuffer {

    long energy();

    long capacity();

    /** Energy in from cables. Returns how much was taken. */
    long receive(long max, boolean simulate);

    /** Puts back a saved amount (Fabric transactions that roll back). */
    void setEnergy(long energy);
}
