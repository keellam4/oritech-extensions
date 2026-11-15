package io.github.keellam4.oritechextensions.wireless;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The Uplink Mast: placed touching a Cargo Wall, it extends Cargo Link range. Antenna Segments stack on top. */
public class UplinkMastBlock extends Block {

    public static final MapCodec<UplinkMastBlock> CODEC = simpleCodec(UplinkMastBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(Shapes.box(2 / 16.0, 0, 2 / 16.0, 14 / 16.0, 4 / 16.0, 14 / 16.0),
            Shapes.box(6 / 16.0, 4 / 16.0, 6 / 16.0, 10 / 16.0, 11 / 16.0, 10 / 16.0),
            Shapes.box(1 / 16.0, 11 / 16.0, 1 / 16.0, 15 / 16.0, 1, 15 / 16.0));

    public UplinkMastBlock(Properties properties) {
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
