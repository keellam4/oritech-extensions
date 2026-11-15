package io.github.keellam4.oritechextensions.storage;

import io.github.keellam4.oritechextensions.platform.Platform;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Silo Casing and Silo Glass: clicking any block of a formed silo with a bucket fills or drains it, an empty
 * hand shows its status, and pipes can connect to any block.
 */
public final class SiloParts {

    private SiloParts() {
    }

    /** The controller of the formed silo {@code pos} belongs to, or null. Controllers sit in the bottom layer. */
    @Nullable
    public static SiloControllerBlockEntity controllerOf(BlockGetter level, BlockPos pos) {
        int reach = SiloStructure.WIDTH - 1;
        for (int dy = 0; dy < SiloStructure.MAX_HEIGHT; dy++) {
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    if (level.getBlockEntity(pos.offset(dx, -dy, dz)) instanceof SiloControllerBlockEntity silo
                            && silo.structure() != null && silo.structure().contains(pos)) {
                        return silo;
                    }
                }
            }
        }
        return null;
    }

    static ItemInteractionResult useItemOn(Level level, BlockPos pos, Player player, InteractionHand hand) {
        var silo = controllerOf(level, pos);
        if (silo != null && Platform.INSTANCE.interactWithFluidItem(player, hand, silo)) {
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    static InteractionResult useWithoutItem(Level level, BlockPos pos, Player player) {
        var silo = controllerOf(level, pos);
        if (silo == null) return InteractionResult.PASS;
        if (!level.isClientSide()) player.displayClientMessage(SiloControllerBlock.status(silo), true);
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    /** Silo Casing. */
    public static class Casing extends net.minecraft.world.level.block.Block {

        public static final com.mojang.serialization.MapCodec<Casing> CODEC = simpleCodec(Casing::new);

        public Casing(Properties properties) {
            super(properties);
        }

        @Override
        protected com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.block.Block> codec() {
            return CODEC;
        }

        @Override
        protected ItemInteractionResult useItemOn(ItemStack stack, net.minecraft.world.level.block.state.BlockState state, Level level, BlockPos pos,
                                                  Player player, InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
            return SiloParts.useItemOn(level, pos, player, hand);
        }

        @Override
        protected InteractionResult useWithoutItem(net.minecraft.world.level.block.state.BlockState state, Level level, BlockPos pos,
                                                   Player player, net.minecraft.world.phys.BlockHitResult hit) {
            return SiloParts.useWithoutItem(level, pos, player);
        }
    }

    /** Silo Glass: see-through between its grid bars, so the fluid shows. */
    public static class Glass extends net.minecraft.world.level.block.TransparentBlock {

        public static final com.mojang.serialization.MapCodec<Glass> CODEC = simpleCodec(Glass::new);

        public Glass(Properties properties) {
            super(properties);
        }

        @Override
        public com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.block.TransparentBlock> codec() {
            return CODEC;
        }

        @Override
        protected ItemInteractionResult useItemOn(ItemStack stack, net.minecraft.world.level.block.state.BlockState state, Level level, BlockPos pos,
                                                  Player player, InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
            return SiloParts.useItemOn(level, pos, player, hand);
        }

        @Override
        protected InteractionResult useWithoutItem(net.minecraft.world.level.block.state.BlockState state, Level level, BlockPos pos,
                                                   Player player, net.minecraft.world.phys.BlockHitResult hit) {
            return SiloParts.useWithoutItem(level, pos, player);
        }
    }
}
