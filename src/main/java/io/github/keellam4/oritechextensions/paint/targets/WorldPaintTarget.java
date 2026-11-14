package io.github.keellam4.oritechextensions.paint.targets;

import io.github.keellam4.oritechextensions.paint.PipeIsolation;
import java.util.HashSet;
import java.util.LinkedHashMap;

import io.github.keellam4.oritechextensions.Config;
import io.github.keellam4.oritechextensions.OritechExtensions;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import io.github.keellam4.oritechextensions.paint.PaintTarget;
import io.github.keellam4.oritechextensions.paint.PaintTargets;
import io.github.keellam4.oritechextensions.paint.PipePaint;
import io.github.keellam4.oritechextensions.paint.PipePaintData;
import io.github.keellam4.oritechextensions.paint.WorldPaint;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;
import rearth.oritech.block.blocks.reactor.BaseReactorBlock;
import rearth.oritech.init.BlockContent;
import rearth.oritech.util.ColorableMachine.ColorVariant;

import java.util.List;
import java.util.function.Function;
import java.util.function.IntSupplier;

/**
 * Blocks whose paint is stored by position in the world, and that are painted as one connected group.
 * Broken blocks drop unpainted; a new block takes over the colour of its painted neighbours.
 */
public final class WorldPaintTarget implements PaintTarget {

    /** How far apart two accelerator parts may be, in a straight line, and still be painted together. */
    public static final int ACCELERATOR_REACH = 8;

    /** Standard item pipes (with transparent ones) and standard fluid pipes: each connected network is a group. */
    public static final WorldPaintTarget PIPES = new WorldPaintTarget(PipePaint::familyOf, () -> Integer.MAX_VALUE, 1, true);

    /** Industrial Glass: touching glass blocks are a group. */
    public static final WorldPaintTarget GLASS = new WorldPaintTarget(
            block -> block == BlockContent.INDUSTRIAL_GLASS_BLOCK ? "glass" : null, Config::maxPaintGroupSize, 1);

    /** Reactor walls and everything built into a reactor (controller, rods, vents, ports, ...): one reactor is a group. */
    public static final WorldPaintTarget REACTOR = new WorldPaintTarget(
            block -> block instanceof BaseReactorBlock ? "reactor" : null, Config::maxPaintGroupSize, 1);

    /**
     * Particle accelerator: controller, guide rings, motors, sensors and particle collectors. Rings are built
     * with gaps between them, so parts up to {@link #ACCELERATOR_REACH} blocks apart in a straight line still
     * count as one accelerator.
     */
    public static final WorldPaintTarget ACCELERATOR = new WorldPaintTarget(
            block -> isAcceleratorPart(block) ? "accelerator" : null, Config::maxPaintGroupSize, ACCELERATOR_REACH);

    /** Datapack tag: blocks in it are painted one at a time. */
    public static final TagKey<Block> PAINTABLE_TAG = TagKey.create(Registries.BLOCK, OritechExtensions.id("paintable"));

    /** Datapack tag: blocks in it are painted as a connected group of the same block. */
    public static final TagKey<Block> PAINTABLE_CONNECTED_TAG = TagKey.create(Registries.BLOCK, OritechExtensions.id("paintable_connected"));

    /** Blocks a modpack adds to {@link #PAINTABLE_TAG}: one block per click, no colour inheritance. */
    public static final WorldPaintTarget TAGGED = new WorldPaintTarget(
            block -> block.builtInRegistryHolder().is(PAINTABLE_TAG) ? block : null, () -> 1, 1, false, false);

    /** Blocks a modpack adds to {@link #PAINTABLE_CONNECTED_TAG}: touching blocks of the same type paint together. */
    public static final WorldPaintTarget TAGGED_CONNECTED = new WorldPaintTarget(
            block -> block.builtInRegistryHolder().is(PAINTABLE_CONNECTED_TAG) ? block : null, Config::maxPaintGroupSize, 1, false, true);

    private final Function<Block, Object> family;
    private final IntSupplier maxGroupSize;
    private final int reach;

    /** Pipes with colour isolation on: a group is the connected pipes that currently share one colour. */
    private final boolean isolated;

    /** Whether a newly placed block takes over the colour of its painted neighbours. */
    private final boolean inherits;

    private WorldPaintTarget(Function<Block, Object> family, IntSupplier maxGroupSize, int reach) {
        this(family, maxGroupSize, reach, false, true);
    }

    private WorldPaintTarget(Function<Block, Object> family, IntSupplier maxGroupSize, int reach, boolean isolated) {
        this(family, maxGroupSize, reach, isolated, true);
    }

    private WorldPaintTarget(Function<Block, Object> family, IntSupplier maxGroupSize, int reach, boolean isolated, boolean inherits) {
        this.family = family;
        this.maxGroupSize = maxGroupSize;
        this.reach = reach;
        this.isolated = isolated;
        this.inherits = inherits;
    }

    private static boolean isAcceleratorPart(Block block) {
        return block == BlockContent.ACCELERATOR_CONTROLLER || block == BlockContent.ACCELERATOR_RING
                || block == BlockContent.ACCELERATOR_MOTOR || block == BlockContent.ACCELERATOR_SENSOR
                || block == BlockContent.PARTICLE_COLLECTOR_BLOCK;
    }

    @Override
    public boolean matchesBlock(Block block) {
        return family.apply(block) != null;
    }

    @Override
    public List<BlockPos> group(Level level, BlockPos pos) {
        if (isolated && level instanceof ServerLevel server && PipeIsolation.enabled()) {
            // Repainting one colour doesn't spill into neighbouring pipes of other colours (or unpainted ones).
            var data = PipePaintData.get(server);
            var colour = data.getOrDefault(pos);
            return WorldPaint.discover(level, pos, family, maxGroupSize.getAsInt(), reach, other -> data.getOrDefault(other) == colour);
        }
        return WorldPaint.discover(level, pos, family, maxGroupSize.getAsInt(), reach);
    }

    @Override
    public ColorVariant get(ServerLevel level, BlockPos pos) {
        return PipePaintData.get(level).getOrDefault(pos);
    }

    @Override
    public List<BlockPos> apply(ServerLevel level, List<BlockPos> group, ColorVariant variant, int budget) {
        var data = PipePaintData.get(level);
        var before = new LinkedHashMap<BlockPos, ColorVariant>();
        if (isolated) {
            for (var pos : group) before.put(pos, data.getOrDefault(pos));
        }
        var changed = WorldPaint.apply(level, group, variant, budget);
        PaintTargets.syncWorld(level, changed, variant);
        if (isolated && !changed.isEmpty()) {
            before.keySet().retainAll(new HashSet<>(changed));
            PipeIsolation.onPaintChanged(level, before);
        }
        return changed;
    }

    /** Whether this target's colours decide which blocks connect (pipes with colour isolation). */
    public boolean isolated() {
        return isolated;
    }

    /** The colour a block just placed at {@code pos} takes over from its neighbours, or null. */
    @Nullable
    public ColorVariant inheritanceColor(ServerLevel level, BlockPos pos) {
        if (!inherits) return null;
        return WorldPaint.inheritanceColor(level, pos, family, reach);
    }
}
