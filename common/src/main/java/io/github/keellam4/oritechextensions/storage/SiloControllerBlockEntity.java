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
import org.jetbrains.annotations.Nullable;

/**
 * The Silo Controller: checks the silo around it once a second and holds its fluid. A formed silo holds
 * {@code siloBucketsPerBlock} buckets per block of its size. If the silo is broken the fluid stays in the
 * controller (and in its item when it's mined), but nothing more goes in until the silo is whole again.
 */
public class SiloControllerBlockEntity extends BlockEntity implements NetworkTank {

    private Fluid fluid = Fluids.EMPTY;
    private long amount;
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

    // ---------------------------------------------------------------- the tank

    @Override
    public Fluid fluid() {
        return fluid;
    }

    @Override
    public long amount() {
        return amount;
    }

    @Override
    public long fluidCapacity() {
        return structure == null ? 0 : structure.volume() * (long) Config.siloBucketsPerBlock() * 1000L;
    }

    /** How full the silo is, 0 to 1. */
    public float fill() {
        long capacity = fluidCapacity();
        return capacity <= 0 ? (amount > 0 ? 1f : 0f) : Math.min(1f, (float) amount / capacity);
    }

    @Override
    public boolean acceptsFluid(Fluid offered) {
        if (offered == Fluids.EMPTY || structure == null) return false;
        return fluid == Fluids.EMPTY || fluid.isSame(offered);
    }

    @Override
    public long fill(Fluid offered, long max, boolean simulate) {
        if (max <= 0 || !acceptsFluid(offered)) return 0;
        long moved = Math.max(0, Math.min(max, fluidCapacity() - amount));
        if (moved > 0 && !simulate) {
            if (fluid == Fluids.EMPTY) fluid = offered;
            amount += moved;
            changed();
        }
        return moved;
    }

    @Override
    public FluidAmount drain(@Nullable Fluid wanted, long max, boolean simulate) {
        if (fluid == Fluids.EMPTY || amount <= 0 || max <= 0) return FluidAmount.EMPTY;
        if (wanted != null && !fluid.isSame(wanted)) return FluidAmount.EMPTY;
        long moved = Math.min(max, amount);
        var out = new FluidAmount(fluid, moved);
        if (!simulate) {
            amount -= moved;
            if (amount <= 0) fluid = Fluids.EMPTY;
            changed();
        }
        return out;
    }

    @Override
    public long roomFor(Fluid offered) {
        return acceptsFluid(offered) ? Math.max(0, fluidCapacity() - amount) : 0;
    }

    @Override
    public FluidContents fluidContents() {
        return new FluidContents(fluid, amount, false);
    }

    @Override
    public void loadFluid(FluidContents contents) {
        fluid = contents.fluid();
        amount = contents.amount();
    }

    @Override
    public void changed() {
        setChanged();
        if (level != null && !level.isClientSide()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    // ---------------------------------------------------------------- saving, syncing, the item

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeFluid(tag, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        loadFluid(FluidContents.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag.get("fluid"))
                .result().orElse(FluidContents.EMPTY));
        structure = tag.contains("silo_height")
                ? new SiloStructure(BlockPos.of(tag.getLong("silo_origin")), tag.getInt("silo_height")) : null;
    }

    private void writeFluid(CompoundTag tag, HolderLookup.Provider registries) {
        FluidContents.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), fluidContents())
                .result().ifPresent(encoded -> tag.put("fluid", encoded));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        writeFluid(tag, registries);
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

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        loadFluid(input.getOrDefault(ModContent.FLUID_CONTENTS.get(), FluidContents.EMPTY));
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (fluid != Fluids.EMPTY) components.set(ModContent.FLUID_CONTENTS.get(), fluidContents());
    }

    @Override
    @SuppressWarnings("deprecation")
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove("fluid");
    }
}
