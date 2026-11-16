package io.github.keellam4.oritechextensions.fabric;

import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import team.reborn.energy.api.EnergyStorage;

/** Lets cables fill an energy buffer: an Induction Pylon or a Crafting Terminal (Team Reborn Energy). It only takes energy in. */
public class PylonEnergyStorage extends SnapshotParticipant<Long> implements EnergyStorage {

    private final io.github.keellam4.oritechextensions.EnergyBuffer pylon;

    public PylonEnergyStorage(io.github.keellam4.oritechextensions.EnergyBuffer pylon) {
        this.pylon = pylon;
    }

    @Override
    public long insert(long max, TransactionContext transaction) {
        long moved = pylon.receive(max, true);
        if (moved > 0) {
            updateSnapshots(transaction);
            pylon.receive(moved, false);
        }
        return moved;
    }

    @Override
    public long extract(long max, TransactionContext transaction) {
        return 0;
    }

    @Override
    public boolean supportsExtraction() {
        return false;
    }

    @Override
    public long getAmount() {
        return pylon.energy();
    }

    @Override
    public long getCapacity() {
        return pylon.capacity();
    }

    @Override
    protected Long createSnapshot() {
        return pylon.energy();
    }

    @Override
    protected void readSnapshot(Long snapshot) {
        pylon.setEnergy(snapshot);
    }
}
