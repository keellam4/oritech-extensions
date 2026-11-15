package io.github.keellam4.oritechextensions.neoforge;

import io.github.keellam4.oritechextensions.storage.NetworkTank;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** Lets pipes, buckets and other mods reach a Fluid Vault or Fluid Silo: one tank. */
public record VaultFluidHandler(NetworkTank vault) implements IFluidHandler {

    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return vault.amount() <= 0 ? FluidStack.EMPTY : new FluidStack(vault.fluid(), (int) Math.min(Integer.MAX_VALUE, vault.amount()));
    }

    @Override
    public int getTankCapacity(int tank) {
        return (int) Math.min(Integer.MAX_VALUE, vault.fluidCapacity());
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return vault.acceptsFluid(stack.getFluid());
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) return 0;
        return (int) vault.fill(resource.getFluid(), resource.getAmount(), action.simulate());
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) return FluidStack.EMPTY;
        var drained = vault.drain(resource.getFluid(), resource.getAmount(), action.simulate());
        return drained.isEmpty() ? FluidStack.EMPTY : new FluidStack(drained.fluid(), (int) drained.amount());
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        var drained = vault.drain(null, maxDrain, action.simulate());
        return drained.isEmpty() ? FluidStack.EMPTY : new FluidStack(drained.fluid(), (int) drained.amount());
    }
}
