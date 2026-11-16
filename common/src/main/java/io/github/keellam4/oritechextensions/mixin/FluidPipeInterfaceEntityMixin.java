package io.github.keellam4.oritechextensions.mixin;

import dev.architectury.fluid.FluidStack;
import io.github.keellam4.oritechextensions.pipes.FluidFlows;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import rearth.oritech.api.fluid.FluidApi;
import rearth.oritech.block.entity.pipes.FluidPipeInterfaceEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Transparent Fluid Pipes (JNI-98): when an extracting fluid pipe end really moves fluid into a target, tell
 * {@link FluidFlows} which fluid, so nearby players can see it flow. Oritech's own transfer is unchanged. Optional:
 * if a future Oritech changes this method the hook just doesn't apply.
 */
@Mixin(value = FluidPipeInterfaceEntity.class, remap = false)
public abstract class FluidPipeInterfaceEntityMixin {

    @Redirect(method = "tick", require = 0,
            at = @At(value = "INVOKE", target = "Lrearth/oritech/api/fluid/FluidApi$FluidStorage;insert(Ldev/architectury/fluid/FluidStack;Z)J"))
    private long oritechExtensions$showFlow(FluidApi.FluidStorage target, FluidStack stack, boolean simulate) {
        long moved = target.insert(stack, simulate);
        if (!simulate && moved > 0 && ((Object) this) instanceof BlockEntity entity && entity.getLevel() instanceof ServerLevel level) {
            FluidFlows.moved(level, entity.getBlockPos(), stack.getFluid());
        }
        return moved;
    }
}
