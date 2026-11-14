package io.github.keellam4.oritechextensions.fabric.mixin;

import io.github.keellam4.oritechextensions.paint.PaintEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Fabric has no "block drops" event. A painted machine addon drops as a painted item, so it is placed back in its
 * colour (NeoForge does this in BlockDropsEvent).
 */
@Mixin(Block.class)
public abstract class BlockDropsMixin {

    @Inject(method = "getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;)Ljava/util/List;",
            at = @At("RETURN"), cancellable = true)
    private static void oritechExtensions$paintDrops(BlockState state, ServerLevel level, BlockPos pos, @Nullable BlockEntity blockEntity,
                                                     CallbackInfoReturnable<List<ItemStack>> cir) {
        oritechExtensions$paint(state, blockEntity, cir);
    }

    @Inject(method = "getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)Ljava/util/List;",
            at = @At("RETURN"), cancellable = true)
    private static void oritechExtensions$paintDropsWithTool(BlockState state, ServerLevel level, BlockPos pos, @Nullable BlockEntity blockEntity,
                                                             @Nullable Entity entity, ItemStack tool, CallbackInfoReturnable<List<ItemStack>> cir) {
        oritechExtensions$paint(state, blockEntity, cir);
    }

    private static void oritechExtensions$paint(BlockState state, @Nullable BlockEntity blockEntity, CallbackInfoReturnable<List<ItemStack>> cir) {
        var paint = PaintEvents.paintForDrops(blockEntity);
        if (paint == null) return;
        var drops = cir.getReturnValue();
        var painted = new ArrayList<ItemStack>(drops.size());
        for (var drop : drops) painted.add(PaintEvents.paintDrop(state, drop, paint));
        cir.setReturnValue(painted);
    }
}
