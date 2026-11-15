package io.github.keellam4.oritechextensions.wireless;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Uplink Mast: placed touching a Cargo Wall, it extends Cargo Link range. Antenna Segments stack on top.
 * Right-click it for its upgrade slot (Infinite Range or Dimensional), which applies to every Link on the wall.
 */
public class UplinkMastBlock extends Block implements net.minecraft.world.level.block.EntityBlock {

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

    @Override
    public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new UplinkMastBlockEntity(pos, state);
    }

    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, net.minecraft.world.level.Level level, BlockPos pos,
                                                                   net.minecraft.world.entity.player.Player player, net.minecraft.world.phys.BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof UplinkMastBlockEntity mast) {
            player.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inventory, p) -> new UplinkMastMenu(id, inventory, mast), getName()));
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide());
    }

    /** Right-click with an upgrade: put it straight in (or swap it for the one inside). */
    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, net.minecraft.world.level.Level level,
                                                                 BlockPos pos, net.minecraft.world.entity.player.Player player,
                                                                 net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        if (!(stack.getItem() instanceof LinkUpgradeItem) || !(level.getBlockEntity(pos) instanceof UplinkMastBlockEntity mast)) {
            return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide()) {
            var old = mast.upgrade().getItem(0);
            mast.upgrade().setItem(0, stack.split(1));
            if (!old.isEmpty() && !player.getInventory().add(old)) player.drop(old, false);
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.SMITHING_TABLE_USE, net.minecraft.sounds.SoundSource.BLOCKS, 0.6f, 1.4f);
        }
        return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    /** Broken: drop the upgrade. */
    @Override
    protected void onRemove(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof UplinkMastBlockEntity mast) {
            net.minecraft.world.Containers.dropContents(level, pos, mast.upgrade());
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
