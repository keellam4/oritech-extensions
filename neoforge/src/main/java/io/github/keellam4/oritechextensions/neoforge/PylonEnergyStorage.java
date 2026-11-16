package io.github.keellam4.oritechextensions.neoforge;

import net.neoforged.neoforge.energy.IEnergyStorage;

/** Lets cables fill an energy buffer: an Induction Pylon or a Crafting Terminal. It only takes energy in. */
public record PylonEnergyStorage(io.github.keellam4.oritechextensions.EnergyBuffer pylon) implements IEnergyStorage {

    @Override
    public int receiveEnergy(int max, boolean simulate) {
        return (int) pylon.receive(max, simulate);
    }

    @Override
    public int extractEnergy(int max, boolean simulate) {
        return 0;
    }

    @Override
    public int getEnergyStored() {
        return (int) Math.min(Integer.MAX_VALUE, pylon.energy());
    }

    @Override
    public int getMaxEnergyStored() {
        return (int) Math.min(Integer.MAX_VALUE, pylon.capacity());
    }

    @Override
    public boolean canExtract() {
        return false;
    }

    @Override
    public boolean canReceive() {
        return true;
    }
}
