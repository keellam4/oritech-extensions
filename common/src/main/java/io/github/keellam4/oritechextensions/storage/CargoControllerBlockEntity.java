package io.github.keellam4.oritechextensions.storage;

import io.github.keellam4.oritechextensions.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The Cargo Controller: finds its network of touching vaults (refreshed once a second, or sooner when it breaks). */
public class CargoControllerBlockEntity extends BlockEntity implements io.github.keellam4.oritechextensions.EnergyBuffer {

    private CargoNetwork network;
    private long discoveredAt = Long.MIN_VALUE;
    /** A Network Anchor is installed: the chunks of every block in the network stay loaded. */
    private boolean anchored;
    /** The chunks this controller is keeping loaded right now. */
    private final java.util.Set<Long> forced = new java.util.HashSet<>();
    private int nextAnchorCheck;

    public CargoControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.CARGO_CONTROLLER_ENTITY.get(), pos, state);
    }

    public CargoNetwork network() {
        long now = level == null ? 0 : level.getGameTime();
        if (network == null || now - discoveredAt >= 20 || now < discoveredAt || !network.isValid()) {
            network = level == null ? CargoNetwork.EMPTY : CargoNetwork.discover(level, worldPosition);
            discoveredAt = now;
        }
        return network;
    }

    /** Forget the network so the next use looks again (a vault next to it was placed or broken). */
    public void invalidate() {
        network = null;
    }

    // ---------------------------------------------------------------- energy (Crafting Terminal only)

    private long energy;

    /** Whether this is a Crafting Terminal that needs power to craft (the Cargo Controller never does). */
    public boolean needsPower() {
        return getBlockState().getBlock() instanceof CraftingTerminalBlock && io.github.keellam4.oritechextensions.Config.terminalEnergyPerCraft() > 0;
    }

    @Override
    public long energy() {
        return energy;
    }

    @Override
    public long capacity() {
        return needsPower() ? io.github.keellam4.oritechextensions.Config.terminalEnergyBuffer() : 0;
    }

    @Override
    public long receive(long max, boolean simulate) {
        long moved = Math.max(0, Math.min(max, capacity() - energy));
        if (moved > 0 && !simulate) {
            energy += moved;
            setChanged();
        }
        return moved;
    }

    @Override
    public void setEnergy(long energy) {
        this.energy = Math.max(0, energy);
        setChanged();
    }

    /** Whether there's power for {@code crafts} crafts (always, when it needs none). */
    public boolean canCraft(int crafts) {
        return !needsPower() || energy >= (long) io.github.keellam4.oritechextensions.Config.terminalEnergyPerCraft() * crafts;
    }

    /** Uses the power for one craft. */
    public void spendCraft() {
        if (!needsPower()) return;
        energy = Math.max(0, energy - io.github.keellam4.oritechextensions.Config.terminalEnergyPerCraft());
        setChanged();
    }

    // ---------------------------------------------------------------- Network Anchor (chunk loading)

    public boolean anchored() {
        return anchored;
    }

    public void setAnchored(boolean anchored) {
        this.anchored = anchored;
        nextAnchorCheck = 0;
        if (!anchored) releaseChunks();
        setChanged();
    }

    public static void serverTick(net.minecraft.world.level.Level level, BlockPos pos, net.minecraft.world.level.block.state.BlockState state,
                                  CargoControllerBlockEntity controller) {
        if (!controller.anchored || --controller.nextAnchorCheck > 0) return;
        controller.nextAnchorCheck = 100;
        controller.forceNetworkChunks();
    }

    /** Keeps the chunks of the controller and every vault, tank and silo in its network loaded. */
    private void forceNetworkChunks() {
        if (!(level instanceof net.minecraft.server.level.ServerLevel server)) return;
        if (!io.github.keellam4.oritechextensions.Config.allowNetworkChunkLoading()) {
            releaseChunks();
            return;
        }
        var wanted = new java.util.HashSet<Long>();
        wanted.add(net.minecraft.world.level.ChunkPos.asLong(worldPosition));
        var cargo = network();
        for (var vault : cargo.vaults()) wanted.add(net.minecraft.world.level.ChunkPos.asLong(vault.getBlockPos()));
        for (var tank : cargo.tanks()) {
            if (tank instanceof net.minecraft.world.level.block.entity.BlockEntity entity) {
                wanted.add(net.minecraft.world.level.ChunkPos.asLong(entity.getBlockPos()));
            }
            if (tank instanceof SiloControllerBlockEntity silo && silo.structure() != null) {
                var origin = silo.structure().origin();
                for (int dx = 0; dx < SiloStructure.WIDTH; dx += SiloStructure.WIDTH - 1) {
                    for (int dz = 0; dz < SiloStructure.WIDTH; dz += SiloStructure.WIDTH - 1) {
                        wanted.add(net.minecraft.world.level.ChunkPos.asLong(origin.offset(dx, 0, dz)));
                    }
                }
            }
        }
        for (var chunk : forced) {
            if (!wanted.contains(chunk)) server.setChunkForced(net.minecraft.world.level.ChunkPos.getX(chunk), net.minecraft.world.level.ChunkPos.getZ(chunk), false);
        }
        for (var chunk : wanted) {
            if (!forced.contains(chunk)) server.setChunkForced(net.minecraft.world.level.ChunkPos.getX(chunk), net.minecraft.world.level.ChunkPos.getZ(chunk), true);
        }
        forced.clear();
        forced.addAll(wanted);
        setChanged();
    }

    /** Lets go of every chunk this controller was keeping loaded. */
    public void releaseChunks() {
        if (level instanceof net.minecraft.server.level.ServerLevel server) {
            for (var chunk : forced) server.setChunkForced(net.minecraft.world.level.ChunkPos.getX(chunk), net.minecraft.world.level.ChunkPos.getZ(chunk), false);
        }
        forced.clear();
    }

    public java.util.Set<Long> forcedChunks() {
        return java.util.Collections.unmodifiableSet(forced);
    }

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (anchored) tag.putBoolean("anchored", true);
        if (energy > 0) tag.putLong("energy", energy);
        if (!forced.isEmpty()) tag.putLongArray("forced_chunks", forced.stream().mapToLong(Long::longValue).toArray());
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        anchored = tag.getBoolean("anchored");
        energy = tag.getLong("energy");
        forced.clear();
        for (long chunk : tag.getLongArray("forced_chunks")) forced.add(chunk);
    }
}
