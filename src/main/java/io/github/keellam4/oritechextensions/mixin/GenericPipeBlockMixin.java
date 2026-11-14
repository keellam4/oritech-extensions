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
import rearth.oritech.block.blocks.pipes.GenericPipeBlock;

/**
 * Colour-isolated pipes: Oritech asks this method whether a pipe connects towards a neighbour. Painted pipes of
 * different colours answer no. Everything else is left to Oritech.
 */
@Mixin(value = GenericPipeBlock.class, remap = false)
public abstract class GenericPipeBlockMixin {

    @Inject(method = "shouldConnect", at = @At("HEAD"), cancellable = true)
    private void oritechExtensions$colourIsolation(BlockState current, Direction direction, BlockPos currentPos, Level world,
                                                   boolean createConnection, CallbackInfoReturnable<Boolean> cir) {
        if (PipeIsolation.blocksConnection(world, currentPos, direction)) {
            cir.setReturnValue(false);
        }
    }
}
