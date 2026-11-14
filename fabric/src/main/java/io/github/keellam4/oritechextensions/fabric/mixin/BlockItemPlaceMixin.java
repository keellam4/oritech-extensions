package io.github.keellam4.oritechextensions.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.keellam4.oritechextensions.paint.PaintEvents;
import io.github.keellam4.oritechextensions.paint.PaintTargets;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Fabric has no "block placed" event. This runs right after a block item has placed its block and the block's
 * own setPlacedBy has run (the same moment NeoForge fires EntityPlaceEvent).
 */
@Mixin(BlockItem.class)
public abstract class BlockItemPlaceMixin {

    @WrapOperation(method = "place(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/InteractionResult;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;setPlacedBy(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)V"))
    private void oritechExtensions$afterPlace(Block block, Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
                                              ItemStack stack, Operation<Void> original) {
        original.call(block, level, pos, state, placer, stack);
        if (level instanceof ServerLevel serverLevel) {
            if (placer instanceof Player player) PaintEvents.onBlockPlaced(serverLevel, pos, player);
            PaintTargets.onBlockPlaced(serverLevel, pos);
        }
    }
}
