package io.github.keellam4.oritechextensions.paint.targets;

import io.github.keellam4.oritechextensions.paint.PaintEvents;
import io.github.keellam4.oritechextensions.paint.PaintTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import rearth.oritech.block.blocks.addons.MachineAddonBlock;
import rearth.oritech.util.ColorableMachine.ColorVariant;

import java.util.List;

/**
 * Machine addon blocks, from Oritech and from mods built on its addon block. One block at a time; the colour
 * is stored on the block entity and stays on the item when the addon is broken (see {@link PaintEvents}).
 */
public final class AddonTarget implements PaintTarget {

    public static final AddonTarget INSTANCE = new AddonTarget();

    private AddonTarget() {
    }

    @Override
    public boolean matchesBlock(Block block) {
        return block instanceof MachineAddonBlock;
    }

    @Override
    public boolean matches(Level level, BlockPos pos) {
        return PaintEvents.isPaintable(level.getBlockEntity(pos));
    }

    @Override
    public List<BlockPos> group(Level level, BlockPos pos) {
        return List.of(pos.immutable());
    }

    @Override
    public ColorVariant get(ServerLevel level, BlockPos pos) {
        var blockEntity = level.getBlockEntity(pos);
        return PaintEvents.isPaintable(blockEntity) ? PaintEvents.getPaint(blockEntity) : ColorVariant.ORANGE;
    }

    @Override
    public List<BlockPos> apply(ServerLevel level, List<BlockPos> group, ColorVariant variant, int budget) {
        if (budget <= 0 || group.isEmpty()) return List.of();
        var blockEntity = level.getBlockEntity(group.get(0));
        if (!PaintEvents.isPaintable(blockEntity) || PaintEvents.getPaint(blockEntity) == variant) return List.of();
        PaintEvents.setPaint(level, blockEntity, variant);
        return group;
    }

    @Override
    public boolean alreadyPainted(Level level, BlockPos pos, ColorVariant variant) {
        var blockEntity = level.getBlockEntity(pos);
        return PaintEvents.isPaintable(blockEntity) && PaintEvents.getPaint(blockEntity) == variant;
    }
}
