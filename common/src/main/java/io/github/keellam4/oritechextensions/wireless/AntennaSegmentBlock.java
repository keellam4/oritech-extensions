package io.github.keellam4.oritechextensions.wireless;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** An Antenna Segment: stacked on an Uplink Mast, each one adds Cargo Link range. */
public class AntennaSegmentBlock extends Block {

    public static final MapCodec<AntennaSegmentBlock> CODEC = simpleCodec(AntennaSegmentBlock::new);
    private static final VoxelShape SHAPE = Shapes.box(5 / 16.0, 0, 5 / 16.0, 11 / 16.0, 1, 11 / 16.0);

    public AntennaSegmentBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
