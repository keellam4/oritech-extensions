package io.github.keellam4.oritechextensions.paint;

import io.github.keellam4.oritechextensions.Config;
import io.github.keellam4.oritechextensions.paint.targets.AddonTarget;
import io.github.keellam4.oritechextensions.paint.targets.HangarDoorTarget;
import io.github.keellam4.oritechextensions.paint.targets.WorldPaintTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import rearth.oritech.item.other.ColorCartridgeItem;
import rearth.oritech.util.ColorableMachine.ColorVariant;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Everything this mod can paint, in one place. A Color Cartridge click is handled here for every
 * {@link PaintTarget}; so are the rules for paint that lives in the world (new blocks take over their
 * neighbours' colour, broken blocks lose theirs, chunks are synced to players).
 * <p>
 * Machines that Oritech paints itself are not listed: Oritech's cartridge handles those.
 */
public final class PaintTargets {

    /** Checked in this order; the first target that matches a block paints it. */
    private static final List<PaintTarget> TARGETS = List.of(
            AddonTarget.INSTANCE,
            WorldPaintTarget.PIPES,
            WorldPaintTarget.GLASS,
            WorldPaintTarget.REACTOR,
            WorldPaintTarget.ACCELERATOR,
            HangarDoorTarget.INSTANCE,
            // Datapack tags last, so a tagged block never overrides one of the built-in targets above.
            WorldPaintTarget.TAGGED,
            WorldPaintTarget.TAGGED_CONNECTED);

    private static final List<WorldPaintTarget> WORLD_TARGETS =
            List.of(WorldPaintTarget.PIPES, WorldPaintTarget.GLASS, WorldPaintTarget.REACTOR, WorldPaintTarget.ACCELERATOR,
                    WorldPaintTarget.TAGGED, WorldPaintTarget.TAGGED_CONNECTED);

    private PaintTargets() {
    }

    public static void register(IEventBus gameBus) {
        gameBus.addListener(PaintTargets::onRightClickBlock);
        gameBus.addListener(PaintTargets::onBlockPlaced);
        gameBus.addListener(PaintTargets::onBlockBreak);
        gameBus.addListener(PaintTargets::onChunkSent);
    }

    public static List<PaintTarget> all() {
        return TARGETS;
    }

    /** The target that paints the block at {@code pos}, or null if it can't be painted by this mod. */
    @Nullable
    public static PaintTarget find(Level level, BlockPos pos) {
        for (var target : TARGETS) {
            if (target.matches(level, pos)) return target;
        }
        return null;
    }

    /** The target whose block models should be wrapped for {@code block}, or null. Used when models are baked. */
    @Nullable
    public static PaintTarget findForModel(Block block) {
        for (var target : TARGETS) {
            if (target.wrapsModels() && target.matchesBlock(block)) return target;
        }
        return null;
    }

    // ---------------------------------------------------------------- applying paint

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        var stack = event.getItemStack();
        if (!(stack.getItem() instanceof ColorCartridgeItem cartridge)) return;

        var level = event.getLevel();
        var pos = event.getPos();
        var target = find(level, pos);
        if (target == null) return;

        if (level instanceof ServerLevel serverLevel) {
            if (!tryPaint(serverLevel, pos, event.getEntity(), event.getHand(), stack)) return;
        } else if (target.alreadyPainted(level, pos, cartridge.variant)) {
            return; // nothing to do: let the click behave normally
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide()));
    }

    /**
     * Paints whatever is at {@code pos} with the cartridge in {@code stack}, together with its group.
     * Mirrors Oritech's ColorCartridgeItem: splash sound, gust particles.
     * <p>
     * Cost: one cartridge for a single block. For a group it follows the config: one for the whole group,
     * or one per block (painting stops when the stack runs out).
     *
     * @return false if the block can't be painted, or nothing would change (no cartridge is used).
     */
    public static boolean tryPaint(ServerLevel level, BlockPos pos, Player player, InteractionHand hand, ItemStack stack) {
        if (!(stack.getItem() instanceof ColorCartridgeItem cartridge)) return false;
        var target = find(level, pos);
        if (target == null) return false;
        var group = target.group(level, pos);
        if (group.isEmpty()) return false;

        boolean free = player.getAbilities().instabuild;
        boolean perBlock = group.size() > 1 && Config.pipePaintCostMode() == Config.PipePaintCostMode.PER_BLOCK;
        int budget = perBlock && !free ? stack.getCount() : Integer.MAX_VALUE;

        var changed = target.apply(level, group, cartridge.variant, budget);
        if (changed.isEmpty()) return false;

        if (!free) {
            stack.shrink(perBlock ? changed.size() : 1);
            player.setItemInHand(hand, stack);
        }

        level.playSound(null, pos, SoundEvents.AXOLOTL_SPLASH, SoundSource.PLAYERS, 1f, 0.6f);
        var at = pos.getCenter();
        level.sendParticles(ParticleTypes.GUST, at.x, at.y, at.z, 1,
                level.random.nextFloat(), level.random.nextFloat(), level.random.nextFloat(), 0.15f);
        return true;
    }

    // ---------------------------------------------------------------- paint that lives in the world

    @Nullable
    private static WorldPaintTarget worldTarget(Level level, BlockPos pos) {
        var block = level.getBlockState(pos).getBlock();
        for (var target : WORLD_TARGETS) {
            if (target.matchesBlock(block)) return target;
        }
        return null;
    }

    /** A new pipe, glass, reactor or accelerator block takes over the colour of its painted neighbours, if they agree. */
    private static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var pos = event.getPos();
        var target = worldTarget(level, pos);
        if (target == null) return;

        var data = PipePaintData.get(level);
        var before = data.getOrDefault(pos);
        var inherited = target.inheritanceColor(level, pos);
        if (inherited != null) {
            data.set(pos, inherited);
            syncWorld(level, List.of(pos), inherited);
        } else if (data.has(pos)) {
            // Left over from a block that was removed without being broken by hand (an explosion, a piston).
            data.remove(pos);
            syncWorld(level, List.of(pos), ColorVariant.ORANGE);
        }
        if (target.isolated() && data.getOrDefault(pos) != ColorVariant.ORANGE) {
            // The pipe connected to its neighbours while still unpainted; drop the links its colour doesn't allow.
            PipeIsolation.onPaintChanged(level, java.util.Map.of(pos.immutable(), ColorVariant.ORANGE));
        } else if (target.isolated() && before != ColorVariant.ORANGE) {
            PipeIsolation.onPaintChanged(level, java.util.Map.of(pos.immutable(), before));
        }
    }

    private static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var pos = event.getPos();
        var data = PipePaintData.get(level);
        if (!data.has(pos)) return;

        data.remove(pos);
        syncWorld(level, List.of(pos), ColorVariant.ORANGE);
    }

    private static void onChunkSent(ChunkWatchEvent.Sent event) {
        var data = PipePaintData.get(event.getLevel());
        var positions = data.inChunk(event.getChunk().getPos());
        if (positions.isEmpty()) return;

        var entries = new ArrayList<PaintSyncPayload.Entry>(positions.size());
        for (var pos : positions) {
            entries.add(PaintSyncPayload.Entry.inWorld(pos, data.getOrDefault(pos)));
        }
        PacketDistributor.sendToPlayer(event.getPlayer(), new PaintSyncPayload(entries));
    }

    /** Tells every player who can see {@code positions} that they now have {@code variant} (ORANGE = cleared). */
    public static void syncWorld(ServerLevel level, List<BlockPos> positions, ColorVariant variant) {
        if (positions.isEmpty()) return;
        var entries = new ArrayList<PaintSyncPayload.Entry>(positions.size());
        for (var pos : positions) {
            entries.add(PaintSyncPayload.Entry.inWorld(pos, variant));
        }
        var payload = new PaintSyncPayload(entries);

        // Once per affected chunk; a player tracking several of them gets the list more than once, which is harmless.
        var sent = new HashSet<Long>();
        for (var pos : positions) {
            if (sent.add(ChunkPos.asLong(pos))) {
                PacketDistributor.sendToPlayersTrackingChunk(level, new ChunkPos(pos), payload);
            }
        }
    }
}
