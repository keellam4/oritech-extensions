package io.github.keellam4.oritechextensions.mixin;

import io.github.keellam4.oritechextensions.paint.PipeIsolation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import rearth.oritech.block.blocks.pipes.GenericPipeDuctBlock;

/**
 * Colour-isolated pipe ducts. Ducts connect to every neighbour without asking {@code shouldConnect} (two ducts
 * side by side always join), so both checks answer no between painted blocks of different colours.
 */
@Mixin(value = GenericPipeDuctBlock.class, remap = false)
public abstract class GenericPipeDuctBlockMixin {

    @Inject(method = "shouldConnect", at = @At("HEAD"), cancellable = true)
    private void oritechExtensions$colourIsolation(BlockState current, Direction direction, BlockPos currentPos, Level world,
                                                   boolean createConnection, CallbackInfoReturnable<Boolean> cir) {
        if (PipeIsolation.blocksConnection(world, currentPos, direction)) cir.setReturnValue(false);
    }

    @Inject(method = "isConnectingInDirection", at = @At("HEAD"), cancellable = true)
    private void oritechExtensions$colourIsolationConnecting(BlockState current, Direction direction, BlockPos currentPos, Level world,
                                                             boolean createConnection, CallbackInfoReturnable<Boolean> cir) {
        if (PipeIsolation.blocksConnection(world, currentPos, direction)) cir.setReturnValue(false);
    }
}
