package io.github.keellam4.oritechextensions.pipes;

import io.github.keellam4.oritechextensions.ModContent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import rearth.oritech.block.blocks.pipes.GenericPipeBlock;
import rearth.oritech.block.blocks.pipes.fluid.FluidPipeConnectionBlock;

/** The Transparent Fluid Pipe where it meets a machine or tank (Oritech swaps it in and out automatically). */
public class TransparentFluidPipeConnectionBlock extends FluidPipeConnectionBlock {

    public TransparentFluidPipeConnectionBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape[] createShapes() {
        return GenericPipeBlock.THICK_SHAPES;
    }

    @Override
    public BlockState getNormalBlock() {
        return ModContent.TRANSPARENT_FLUID_PIPE.get().defaultBlockState();
    }

    @Override
    public BlockState getConnectionBlock() {
        return ModContent.TRANSPARENT_FLUID_PIPE_CONNECTION.get().defaultBlockState();
    }
}
