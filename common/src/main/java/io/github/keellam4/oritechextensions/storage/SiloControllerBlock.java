package io.github.keellam4.oritechextensions.storage;

import com.mojang.serialization.MapCodec;
import io.github.keellam4.oritechextensions.platform.Platform;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The Silo Controller: goes in the bottom layer of a Fluid Silo. Pipes and buckets use it to fill and drain the
 * silo, it joins a Cargo network when it touches a vault or controller, and right-clicking opens its screen
 * (fluids, their rules and the partition upgrade).
 */
public class SiloControllerBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final MapCodec<SiloControllerBlock> CODEC = simpleCodec(SiloControllerBlock::new);

    public SiloControllerBlock(Properties properties) {
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
        return new SiloControllerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return (l, pos, s, entity) -> {
            if (entity instanceof SiloControllerBlockEntity silo) SiloControllerBlockEntity.serverTick(l, pos, s, silo);
        };
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof SiloControllerBlockEntity silo && Platform.INSTANCE.interactWithFluidItem(player, hand, silo)) {
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof SiloControllerBlockEntity silo) {
            silo.checkStructure();
            if (silo.isFormed()) {
                player.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inventory, p) -> new SiloMenu(id, inventory, silo),
                        Component.translatable("block.oritech_extensions.silo_controller")));
            } else {
                player.displayClientMessage(status(silo), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    /** "3×3×5 · Water 12,000 / 45,000 B", or how to build one. */
    public static Component status(SiloControllerBlockEntity silo) {
        var structure = silo.structure();
        if (structure == null) {
            return Component.translatable("message.oritech_extensions.silo.not_formed").withStyle(ChatFormatting.RED);
        }
        var size = structure.height() + "";
        var fluids = silo.fluids();
        Component contents = fluids.isEmpty() ? Component.translatable("message.oritech_extensions.silo.empty")
                : fluids.size() == 1 ? fluids.get(0).fluid().defaultFluidState().createLegacyBlock().getBlock().getName()
                : Component.translatable("message.oritech_extensions.silo.fluids", fluids.size());
        return Component.translatable("message.oritech_extensions.silo.status", size, contents,
                String.format("%,d", silo.amount() / 1000), String.format("%,d", silo.fluidCapacity() / 1000)).withStyle(ChatFormatting.AQUA);
    }
}
