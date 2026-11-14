package io.github.keellam4.oritechextensions.paint.targets;

import io.github.keellam4.oritechextensions.ModContent;
import io.github.keellam4.oritechextensions.hangar.HangarDoorExtensionBlock;
import io.github.keellam4.oritechextensions.paint.PaintTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;
import rearth.oritech.util.ColorableMachine;
import rearth.oritech.util.ColorableMachine.ColorVariant;

import java.util.List;

/**
 * Hangar door extensions. They have no colour of their own: they show the colour of the Oritech door they
 * belong to. Painting an extension paints that door, so the whole door changes whichever part is clicked.
 */
public final class HangarDoorTarget implements PaintTarget {

    public static final HangarDoorTarget INSTANCE = new HangarDoorTarget();

    private HangarDoorTarget() {
    }

    @Override
    public boolean matchesBlock(Block block) {
        return block == ModContent.HANGAR_DOOR_EXTENSION.get();
    }

    @Override
    public boolean wrapsModels() {
        return false; // drawn by a GeckoLib renderer that picks the door's texture itself
    }

    /** Position of the door this extension hangs on, or null if {@code pos} isn't an extension. */
    @Nullable
    private static BlockPos doorPos(Level level, BlockPos pos) {
        var state = level.getBlockState(pos);
        return state.is(ModContent.HANGAR_DOOR_EXTENSION.get()) ? HangarDoorExtensionBlock.anchorPos(pos, state) : null;
    }

    @Nullable
    private static ColorableMachine door(Level level, @Nullable BlockPos doorPos) {
        if (doorPos == null) return null;
        return level.getBlockEntity(doorPos) instanceof ColorableMachine door && door.supportRecoloring() ? door : null;
    }

    @Override
    public List<BlockPos> group(Level level, BlockPos pos) {
        var doorPos = doorPos(level, pos);
        return door(level, doorPos) != null ? List.of(doorPos) : List.of();
    }

    @Override
    public ColorVariant get(ServerLevel level, BlockPos pos) {
        var door = door(level, doorPos(level, pos));
        return door != null ? door.getCurrentColor() : ColorVariant.ORANGE;
    }

    /** {@code group} holds the door's own position (see {@link #group}). */
    @Override
    public List<BlockPos> apply(ServerLevel level, List<BlockPos> group, ColorVariant variant, int budget) {
        if (budget <= 0 || group.isEmpty()) return List.of();
        var door = door(level, group.get(0));
        if (door == null || door.getCurrentColor() == variant) return List.of();
        door.assignColor(variant); // Oritech saves it and sends it to players
        return group;
    }

    @Override
    public boolean alreadyPainted(Level level, BlockPos pos, ColorVariant variant) {
        var door = door(level, doorPos(level, pos));
        return door != null && door.getCurrentColor() == variant;
    }
}
