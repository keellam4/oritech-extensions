package io.github.keellam4.oritechextensions.storage;

import io.github.keellam4.oritechextensions.Config;
import io.github.keellam4.oritechextensions.ModContent;
import io.github.keellam4.oritechextensions.platform.Platform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * The Fluid Vault: a tank for one fluid, with the same look, readout, paint and upgrades as the item vaults.
 * Amounts are millibuckets. It holds no items, so the item side of {@link CargoVaultBlockEntity} stays empty.
 */
public class FluidVaultBlockEntity extends CargoVaultBlockEntity implements NetworkTank {

    private Fluid fluid = Fluids.EMPTY;
    private long amount;

    public FluidVaultBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.FLUID_VAULT_ENTITY.get(), pos, state);
    }

    public Fluid fluid() {
        return fluid;
    }

    public long amount() {
        return amount;
    }

    /** Millibuckets the vault holds: {@code fluidVaultBuckets} buckets, times the capacity upgrades. */
    public long fluidCapacity() {
        return fluidCapacity(multiplier());
    }

    private long fluidCapacity(int multiplier) {
        return (long) vaultStacks() * 1000L * multiplier;
    }

    /** Buckets before upgrades; for this vault the synced "vault stacks" value is the bucket count. */
    @Override
    public int vaultStacks() {
        int synced = super.vaultStacks();
        return level != null && level.isClientSide() ? synced : Config.fluidVaultBuckets();
    }

    public boolean acceptsFluid(Fluid offered) {
        if (offered == Fluids.EMPTY) return false;
        if (fluid != Fluids.EMPTY) return fluid.isSame(offered);
        return !isLocked();
    }

    /** Fills up to {@code max} millibuckets. Returns how much was taken: with a Void upgrade, all of it. */
    public long fill(Fluid offered, long max, boolean simulate) {
        if (max <= 0 || !acceptsFluid(offered)) return 0;
        long moved = Math.max(0, Math.min(max, fluidCapacity() - amount));
        if (moved > 0 && !simulate) {
            fluid = offered.isSame(fluid) ? fluid : offered;
            amount += moved;
            changed();
        }
        return has(VaultUpgrade.VOID) ? max : moved;
    }

    /** Drains up to {@code max} millibuckets of {@code wanted} (or of whatever it holds, when null). */
    public FluidAmount drain(Fluid wanted, long max, boolean simulate) {
        if (fluid == Fluids.EMPTY || amount <= 0 || max <= 0) return FluidAmount.EMPTY;
        if (wanted != null && !fluid.isSame(wanted)) return FluidAmount.EMPTY;
        long moved = Math.min(max, amount);
        var out = new FluidAmount(fluid, moved);
        if (!simulate) {
            amount -= moved;
            if (amount <= 0 && !isLocked()) fluid = Fluids.EMPTY;
            changed();
        }
        return out;
    }

    /** Room for more of {@code offered} (ignoring the Void upgrade). */
    public long roomFor(Fluid offered) {
        return acceptsFluid(offered) ? Math.max(0, fluidCapacity() - amount) : 0;
    }

    // ---------------------------------------------------------------- the item side: always empty

    @Override
    public boolean isEmpty() {
        return amount <= 0;
    }

    @Override
    public boolean takesByHand() {
        return false;
    }

    @Override
    public float fill() {
        long capacity = fluidCapacity();
        return capacity <= 0 ? 0 : Math.min(1f, (float) amount / capacity);
    }

    @Override
    public float fill(int slot) {
        return fill();
    }

    @Override
    public boolean accepts(int slot, net.minecraft.world.item.ItemStack stack) {
        return false;
    }

    @Override
    public boolean hasContentType() {
        return fluid != Fluids.EMPTY;
    }

    @Override
    public void setLocked(boolean locked) {
        setLockedQuietly(locked);
        if (!locked && amount <= 0) fluid = Fluids.EMPTY;
        changed();
    }

    @Override
    protected boolean fitsWith(int multiplier) {
        return amount <= fluidCapacity(multiplier);
    }

    public FluidContents fluidContents() {
        return new FluidContents(fluid, amount, isLocked());
    }

    public void loadFluid(FluidContents contents) {
        fluid = contents.fluid();
        amount = contents.amount();
        setLockedQuietly(contents.locked());
    }

    // ---------------------------------------------------------------- Pull and Push

    @Override
    protected void moveWithNeighbours(ServerLevel server, BlockPos pos, BlockState state, boolean pull, boolean push) {
        var facing = state.getValue(CargoVaultBlock.FACING);
        var platform = Platform.INSTANCE;
        long rate = 1000; // a bucket a second per direction
        if (push && amount > 0) {
            for (var side : VaultSide.values()) {
                if ((pushSides() & side.bit()) == 0) continue;
                var direction = side.toDirection(facing);
                long moved = platform.insertFluid(server, pos.relative(direction), direction.getOpposite(), fluid, Math.min(rate, amount), false);
                if (moved > 0) {
                    drain(fluid, moved, false);
                    break;
                }
            }
        }
        if (pull) {
            for (var side : VaultSide.values()) {
                if ((pullSides() & side.bit()) == 0) continue;
                var direction = side.toDirection(facing);
                var target = pos.relative(direction);
                var offer = platform.extractFluid(server, target, direction.getOpposite(), f -> roomFor(f) > 0, rate, true);
                if (offer.isEmpty()) continue;
                long fits = Math.min(offer.amount(), roomFor(offer.fluid()));
                var real = platform.extractFluid(server, target, direction.getOpposite(), f -> f.isSame(offer.fluid()), fits, false);
                if (!real.isEmpty()) {
                    fill(real.fluid(), real.amount(), false);
                    break;
                }
            }
        }
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
    }

    private void writeFluid(CompoundTag tag, HolderLookup.Provider registries) {
        FluidContents.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), fluidContents())
                .result().ifPresent(encoded -> tag.put("fluid", encoded));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = super.getUpdateTag(registries);
        writeFluid(tag, registries);
        return tag;
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
