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

    private static volatile boolean allowed;

    /**
     * Oritech's fluid pipe block entity type only lists Oritech's blocks. Add this one before the first block
     * entity is made (NeoForge also does this in BlockEntityTypeAddBlocksEvent).
     */
    @Override
    public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(net.minecraft.core.BlockPos pos, BlockState state) {
        if (!allowed) allow();
        return super.newBlockEntity(pos, state);
    }

    @SuppressWarnings("unchecked")
    private static synchronized void allow() {
        if (allowed) return;
        var type = rearth.oritech.init.BlockEntitiesContent.FLUID_PIPE_ENTITY;
        if (type.isValid(ModContent.TRANSPARENT_FLUID_PIPE_CONNECTION.get().defaultBlockState())) {
            allowed = true;
            return;
        }
        try {
            for (var field : net.minecraft.world.level.block.entity.BlockEntityType.class.getDeclaredFields()) {
                if (field.getType() != java.util.Set.class || java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                var blocks = new java.util.HashSet<>((java.util.Set<net.minecraft.world.level.block.Block>) field.get(type));
                blocks.add(ModContent.TRANSPARENT_FLUID_PIPE_CONNECTION.get());
                field.set(type, blocks);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            io.github.keellam4.oritechextensions.OritechExtensions.LOGGER.error("Couldn't register the Transparent Fluid Pipe with Oritech's pipe block entity", e);
        }
        allowed = true;
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
