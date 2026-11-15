package io.github.keellam4.oritechextensions.storage;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The Cargo Controller: links the vaults touching it (and those touching them) into one inventory. Pipes see the
 * whole wall through it, and right-clicking opens a terminal listing everything stored.
 */
public class CargoControllerBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final MapCodec<CargoControllerBlock> CODEC = simpleCodec(CargoControllerBlock::new);

    public CargoControllerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CargoControllerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                                                     net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return (l, pos, s, entity) -> {
            if (entity instanceof CargoControllerBlockEntity controller) CargoControllerBlockEntity.serverTick(l, pos, s, controller);
        };
    }

    /** Right-click with a Network Anchor: install it (the network's chunks stay loaded). */
    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                                 Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(io.github.keellam4.oritechextensions.ModContent.NETWORK_ANCHOR.get())) return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!(level.getBlockEntity(pos) instanceof CargoControllerBlockEntity controller)) return net.minecraft.world.ItemInteractionResult.FAIL;
        if (!level.isClientSide()) {
            if (controller.anchored()) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.oritech_extensions.anchor.already"), true);
            } else if (!io.github.keellam4.oritechextensions.Config.allowNetworkChunkLoading()) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.oritech_extensions.anchor.disabled")
                        .withStyle(net.minecraft.ChatFormatting.RED), true);
            } else {
                controller.setAnchored(true);
                if (!player.getAbilities().instabuild) stack.shrink(1);
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.oritech_extensions.anchor.installed")
                        .withStyle(net.minecraft.ChatFormatting.AQUA), true);
            }
        }
        return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    /** Broken: let go of the chunks and drop the anchor. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof CargoControllerBlockEntity controller && controller.anchored()) {
            controller.setAnchored(false);
            net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    new net.minecraft.world.item.ItemStack(io.github.keellam4.oritechextensions.ModContent.NETWORK_ANCHOR.get()));
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof CargoControllerBlockEntity controller) {
            boolean crafting = state.getBlock() instanceof CraftingTerminalBlock;
            player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new TerminalMenu(id, inventory, controller, crafting),
                    state.getBlock().getName()));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean moved) {
        super.neighborChanged(state, level, pos, neighbor, neighborPos, moved);
        if (level.getBlockEntity(pos) instanceof CargoControllerBlockEntity controller) controller.invalidate();
    }
}
