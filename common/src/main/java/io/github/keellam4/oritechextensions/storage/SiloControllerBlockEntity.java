package io.github.keellam4.oritechextensions.storage;

import io.github.keellam4.oritechextensions.Config;
import io.github.keellam4.oritechextensions.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.SimpleContainer;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * The Silo Controller: checks the silo around it once a second and holds its fluids. A formed silo holds
 * {@code siloBucketsPerBlock} buckets per block of its size, shared by every fluid in it. It holds one fluid, or
 * more with a partition upgrade; each fluid can have a limit, a reserve, a lock and void overflow (see
 * {@link SiloTank}). If the silo is broken the fluids stay in the controller (and in its item when it's mined),
 * but nothing more goes in until the silo is whole again.
 */
public class SiloControllerBlockEntity extends BlockEntity implements NetworkTank {

    private final List<SiloTank> tanks = new ArrayList<>();
    private final SimpleContainer upgrade = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            SiloControllerBlockEntity.this.changed();
        }
    };
    @Nullable
    private SiloStructure structure;
    private int nextCheck;

    public SiloControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.SILO_CONTROLLER_ENTITY.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SiloControllerBlockEntity silo) {
        if (--silo.nextCheck <= 0) {
            silo.nextCheck = 20;
            silo.checkStructure();
        }
    }

    /** Looks for the silo again now (game tests, and after the controller is placed). */
    public void checkStructure() {
        if (level == null) return;
        var found = SiloStructure.find(level, worldPosition);
        if (java.util.Objects.equals(found, structure)) return;
        structure = found;
        changed();
    }

    @Nullable
    public SiloStructure structure() {
        return structure;
    }

    public boolean isFormed() {
        return structure != null;
    }

    // ---------------------------------------------------------------- the tanks

    /** The fluids in the silo, in the screen's order. Pipes pulling out take from the first one first. */
    public List<SiloTank> tanks() {
        return tanks;
    }

    /** How many fluids it may hold at once: 1, or more with a partition upgrade. */
    public int partitions() {
        return upgrade.getItem(0).getItem() instanceof PartitionUpgradeItem part ? part.partitions() : 1;
    }

    public SimpleContainer upgrade() {
        return upgrade;
    }

    @Nullable
    private SiloTank tankFor(Fluid wanted) {
        for (var tank : tanks) if (tank.fluid.isSame(wanted)) return tank;
        return null;
    }

    /** The first fluid, for single-fluid callers. */
    @Override
    public Fluid fluid() {
        for (var tank : tanks) if (tank.amount > 0) return tank.fluid;
        return Fluids.EMPTY;
    }

    /** Everything in the silo, all fluids together. */
    @Override
    public long amount() {
        long total = 0;
        for (var tank : tanks) total += tank.amount;
        return total;
    }

    @Override
    public List<FluidAmount> fluids() {
        var out = new ArrayList<FluidAmount>();
        for (var tank : tanks) if (tank.amount > 0) out.add(new FluidAmount(tank.fluid, tank.amount));
        return out;
    }

    @Override
    public long fluidCapacity() {
        return structure == null ? 0 : structure.volume() * (long) Config.siloBucketsPerBlock() * 1000L;
    }

    /** How full the silo is, 0 to 1. */
    public float fill() {
        long capacity = fluidCapacity();
        long amount = amount();
        return capacity <= 0 ? (amount > 0 ? 1f : 0f) : Math.min(1f, (float) amount / capacity);
    }

    private long percent(int value) {
        return fluidCapacity() * value / 100;
    }

    @Override
    public boolean acceptsFluid(Fluid offered) {
        if (offered == Fluids.EMPTY || structure == null) return false;
        return tankFor(offered) != null || tanks.size() < partitions();
    }

    @Override
    public long roomFor(Fluid offered) {
        if (!acceptsFluid(offered)) return 0;
        var own = tankFor(offered);
        long others = 0;
        for (var tank : tanks) if (tank != own) others += Math.max(tank.amount, percent(tank.reserve));
        long ownAmount = own == null ? 0 : own.amount;
        long room = fluidCapacity() - others - ownAmount;
        if (own != null && own.limit > 0) room = Math.min(room, percent(own.limit) - ownAmount);
        return Math.max(0, room);
    }

    @Override
    public long fill(Fluid offered, long max, boolean simulate) {
        if (max <= 0 || !acceptsFluid(offered)) return 0;
        long moved = Math.min(max, roomFor(offered));
        var tank = tankFor(offered);
        boolean voids = tank != null && tank.voids;
        if (!simulate && moved > 0) {
            if (tank == null) {
                tank = new SiloTank(offered);
                tanks.add(tank);
            }
            tank.amount += moved;
            changed();
        }
        return voids ? max : moved;
    }

    @Override
    public FluidAmount drain(@Nullable Fluid wanted, long max, boolean simulate) {
        if (max <= 0) return FluidAmount.EMPTY;
        SiloTank tank = null;
        for (var candidate : tanks) {
            if (candidate.amount > 0 && (wanted == null || candidate.fluid.isSame(wanted))) {
                tank = candidate;
                break;
            }
        }
        if (tank == null) return FluidAmount.EMPTY;
        long moved = Math.min(max, tank.amount);
        var out = new FluidAmount(tank.fluid, moved);
        if (!simulate) {
            tank.amount -= moved;
            if (tank.disposable()) tanks.remove(tank);
            changed();
        }
        return out;
    }

    /** A screen button: {@code action} on the fluid in row {@code row}. Returns whether anything changed. */
    public boolean configure(int row, int action) {
        if (row < 0 || row >= tanks.size()) return false;
        var tank = tanks.get(row);
        switch (action) {
            case 0, 1 -> tank.limit = SiloTank.step(SiloTank.LIMIT_STEPS, tank.limit, action == 1);
            case 2, 3 -> {
                int next = SiloTank.step(SiloTank.RESERVE_STEPS, tank.reserve, action == 3);
                int others = 0;
                for (var other : tanks) if (other != tank) others += other.reserve;
                // Reserves can't add up to more than the whole silo.
                tank.reserve = others + next <= 100 ? next : 0;
            }
            case 4 -> tank.locked = !tank.locked;
            case 5 -> tank.voids = !tank.voids;
            case 6 -> {
                if (row > 0) {
                    tanks.remove(row);
                    tanks.add(row - 1, tank);
                }
            }
            default -> {
                return false;
            }
        }
        if (tank.disposable()) tanks.remove(tank);
        changed();
        return true;
    }

    /** Whether the upgrade may come out: not while the silo holds more fluids than it could without it. */
    public boolean canRemoveUpgrade() {
        return tanks.size() <= 1;
    }

    @Override
    public FluidContents fluidContents() {
        return new FluidContents(fluid(), amount(), false);
    }

    @Override
    public void loadFluid(FluidContents contents) {
        tanks.clear();
        if (contents.fluid() != Fluids.EMPTY && contents.amount() > 0) {
            var tank = new SiloTank(contents.fluid());
            tank.amount = contents.amount();
            tanks.add(tank);
        }
    }

    @Override
    public Object saveFluidState() {
        var copy = new ArrayList<SiloTank>();
        for (var tank : tanks) copy.add(tank.copy());
        return copy;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void restoreFluidState(Object state) {
        tanks.clear();
        for (var tank : (List<SiloTank>) state) tanks.add(tank.copy());
    }

    @Override
    public void changed() {
        setChanged();
        if (level != null && !level.isClientSide()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    // ---------------------------------------------------------------- saving, syncing, the item

    private CompoundTag writeTanks(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        var list = new net.minecraft.nbt.ListTag();
        for (var tank : tanks) list.add(tank.save());
        tag.put("tanks", list);
        if (!upgrade.getItem(0).isEmpty()) tag.put("upgrade", upgrade.getItem(0).save(registries));
        return tag;
    }

    private void readTanks(CompoundTag tag, HolderLookup.Provider registries) {
        tanks.clear();
        for (var entry : tag.getList("tanks", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            var tank = SiloTank.load((CompoundTag) entry);
            if (tank.fluid != Fluids.EMPTY) tanks.add(tank);
        }
        upgrade.setItem(0, tag.contains("upgrade")
                ? net.minecraft.world.item.ItemStack.parse(registries, tag.getCompound("upgrade")).orElse(net.minecraft.world.item.ItemStack.EMPTY)
                : net.minecraft.world.item.ItemStack.EMPTY);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("silo", writeTanks(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("silo")) {
            readTanks(tag.getCompound("silo"), registries);
        } else {
            // Silos from before partitions kept one fluid.
            loadFluid(FluidContents.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag.get("fluid"))
                    .result().orElse(FluidContents.EMPTY));
        }
        structure = tag.contains("silo_height")
                ? new SiloStructure(BlockPos.of(tag.getLong("silo_origin")), tag.getInt("silo_height")) : null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tag.put("silo", writeTanks(registries));
        if (structure != null) {
            tag.putLong("silo_origin", structure.origin().asLong());
            tag.putInt("silo_height", structure.height());
        }
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** The item keeps the fluids, their rules and the upgrade (in its custom data). */
    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        var data = input.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (data != null && data.contains("silo") && level != null) {
            readTanks(data.copyTag().getCompound("silo"), level.registryAccess());
        } else {
            var old = input.get(ModContent.FLUID_CONTENTS.get());
            if (old != null) loadFluid(old);
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if ((!tanks.isEmpty() || !upgrade.getItem(0).isEmpty()) && level != null) {
            var tag = new CompoundTag();
            tag.put("silo", writeTanks(level.registryAccess()));
            components.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove("silo");
        tag.remove("fluid");
    }
}
