package io.github.keellam4.oritechextensions.wireless;

import io.github.keellam4.oritechextensions.Config;
import io.github.keellam4.oritechextensions.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Induction Pylon (lower half): stores energy from cables and, twice a second, charges the energy items of
 * the players it may charge within range. Charging is less efficient further away: 100% at the pylon, 50% at
 * the edge of its range. The client gets the list of players being charged, to draw the arcs.
 */
public class InductionPylonBlockEntity extends BlockEntity {

    public static final int INTERVAL = 10;

    private long energy;
    @Nullable
    private UUID owner;
    private String ownerName = "";
    private PylonAccess access = PylonAccess.TEAM;
    private List<Integer> charging = List.of();
    private int nextCharge;

    public InductionPylonBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.INDUCTION_PYLON_ENTITY.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, InductionPylonBlockEntity pylon) {
        if (--pylon.nextCharge > 0) return;
        pylon.nextCharge = INTERVAL;
        pylon.chargeNearby();
    }

    // ---------------------------------------------------------------- energy

    public long energy() {
        return energy;
    }

    public long capacity() {
        return Config.pylonBuffer();
    }

    /** Energy in from cables. Returns how much was taken. */
    public long receive(long max, boolean simulate) {
        long moved = Math.max(0, Math.min(max, capacity() - energy));
        if (moved > 0 && !simulate) {
            energy += moved;
            setChanged();
        }
        return moved;
    }

    /** Restores a snapshot (Fabric transactions). */
    public void setEnergy(long energy) {
        this.energy = Math.max(0, energy);
        setChanged();
    }

    public long range() {
        return Config.pylonRange();
    }

    /** Where the arcs start: the tip of the pylon. */
    public Vec3 tip() {
        return Vec3.atBottomCenterOf(worldPosition).add(0, 1.8, 0);
    }

    /** How much of the energy reaches a player this far away. */
    public double efficiency(double distance) {
        return 1.0 - 0.5 * Math.min(1.0, distance / range());
    }

    // ---------------------------------------------------------------- charging

    public void chargeNearby() {
        if (level == null) return;
        var tip = tip();
        double r = range();
        var players = level.getEntitiesOfClass(Player.class, new AABB(tip, tip).inflate(r),
                player -> !player.isSpectator() && player.position().add(0, 1, 0).distanceTo(tip) <= r && mayCharge(player));
        players.sort((a, b) -> Double.compare(a.distanceToSqr(tip), b.distanceToSqr(tip)));
        var charged = new ArrayList<Integer>();
        long budget = Math.min(energy, (long) Config.pylonRate() * INTERVAL);
        for (var player : players) {
            if (budget <= 0) break;
            long cost = charge(player, budget);
            if (cost > 0) charged.add(player.getId());
            budget -= cost;
        }
        if (!charged.equals(charging)) {
            charging = List.copyOf(charged);
            sync();
        }
    }

    /** Charges one player from up to {@code budget} of the stored energy. Returns the energy used. */
    public long charge(Player player, long budget) {
        double efficiency = efficiency(player.position().add(0, 1, 0).distanceTo(tip()));
        long delivered = PlayerCharging.charge(player, (long) Math.floor(Math.min(budget, energy) * efficiency));
        if (delivered <= 0) return 0;
        long cost = Math.min(energy, (long) Math.ceil(delivered / efficiency));
        energy -= cost;
        setChanged();
        return cost;
    }

    public boolean mayCharge(Player player) {
        if (access == PylonAccess.EVERYONE || owner == null || player.getUUID().equals(owner)) return true;
        if (access == PylonAccess.OWNER || level == null) return false;
        var ownerTeam = level.getScoreboard().getPlayersTeam(ownerName);
        return ownerTeam != null && ownerTeam == player.getTeam();
    }

    public List<Integer> charging() {
        return charging;
    }

    public void setOwner(Player player) {
        owner = player.getUUID();
        ownerName = player.getGameProfile().getName();
        setChanged();
    }

    public boolean isOwner(Player player) {
        return owner == null || owner.equals(player.getUUID());
    }

    public PylonAccess access() {
        return access;
    }

    public void cycleAccess() {
        access = access.next();
        setChanged();
    }

    // ---------------------------------------------------------------- saving and syncing

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("energy", energy);
        if (owner != null) tag.putUUID("owner", owner);
        tag.putString("owner_name", ownerName);
        tag.putString("access", access.name());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy = tag.getLong("energy");
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        ownerName = tag.getString("owner_name");
        try {
            access = PylonAccess.valueOf(tag.getString("access"));
        } catch (IllegalArgumentException e) {
            access = PylonAccess.TEAM;
        }
        if (tag.contains("charging")) {
            var ids = new ArrayList<Integer>();
            for (int id : tag.getIntArray("charging")) ids.add(id);
            charging = ids;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tag.putIntArray("charging", charging.stream().mapToInt(Integer::intValue).toArray());
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
