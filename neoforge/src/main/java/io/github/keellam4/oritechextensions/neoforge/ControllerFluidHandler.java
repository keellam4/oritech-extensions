package io.github.keellam4.oritechextensions.neoforge;

import io.github.keellam4.oritechextensions.storage.CargoControllerBlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** The Cargo Wall's Fluid Vaults as one fluid handler: one tank per vault, filling routes through the network. */
public record ControllerFluidHandler(CargoControllerBlockEntity controller) implements IFluidHandler {

    @Override
    public int getTanks() {
        return Math.max(1, controller.network().tanks().size());
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        var tanks = controller.network().tanks();
        if (tank >= tanks.size() || tanks.get(tank).amount() <= 0) return FluidStack.EMPTY;
        var vault = tanks.get(tank);
        return new FluidStack(vault.fluid(), (int) Math.min(Integer.MAX_VALUE, vault.amount()));
    }

    @Override
    public int getTankCapacity(int tank) {
        var tanks = controller.network().tanks();
        return tank >= tanks.size() ? 0 : (int) Math.min(Integer.MAX_VALUE, tanks.get(tank).fluidCapacity());
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return controller.network().fill(stack.getFluid(), 1, true) > 0;
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) return 0;
        return (int) controller.network().fill(resource.getFluid(), resource.getAmount(), action.simulate());
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) return FluidStack.EMPTY;
        var drained = controller.network().drain(resource.getFluid(), resource.getAmount(), action.simulate());
        return drained.isEmpty() ? FluidStack.EMPTY : new FluidStack(drained.fluid(), (int) drained.amount());
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        var drained = controller.network().drain(null, maxDrain, action.simulate());
        return drained.isEmpty() ? FluidStack.EMPTY : new FluidStack(drained.fluid(), (int) drained.amount());
    }
}
