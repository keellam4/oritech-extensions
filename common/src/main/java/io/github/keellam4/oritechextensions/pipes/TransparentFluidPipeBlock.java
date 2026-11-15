package io.github.keellam4.oritechextensions.pipes;

import io.github.keellam4.oritechextensions.ModContent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import rearth.oritech.block.blocks.pipes.GenericPipeBlock;
import rearth.oritech.block.blocks.pipes.fluid.FluidPipeBlock;

/**
 * A see-through fluid pipe with a line along its centre, so it's easy to tell from Oritech's transparent item
 * pipe. It is an Oritech fluid pipe in every other way: it joins fluid pipe networks and connects to machines.
 */
public class TransparentFluidPipeBlock extends FluidPipeBlock {

    public TransparentFluidPipeBlock(Properties properties) {
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
