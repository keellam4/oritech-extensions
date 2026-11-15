package io.github.keellam4.oritechextensions.paint;

import io.github.keellam4.oritechextensions.ModContent;
import io.github.keellam4.oritechextensions.storage.CargoVaultBlockEntity;
import io.github.keellam4.oritechextensions.platform.Platform;
import net.minecraft.server.level.ServerPlayer;

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
import org.jetbrains.annotations.Nullable;
import rearth.oritech.item.other.ColorCartridgeItem;

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
            WorldPaintTarget.SUPPORTS,
            WorldPaintTarget.PIPE_BOOSTER,
            WorldPaintTarget.VAULTS,
            WorldPaintTarget.SILO,
            WorldPaintTarget.PYLON,
            WorldPaintTarget.UPLINK,
            HangarDoorTarget.INSTANCE,
            // Datapack tags last, so a tagged block never overrides one of the built-in targets above.
            WorldPaintTarget.TAGGED,
            WorldPaintTarget.TAGGED_CONNECTED);

    private static final List<WorldPaintTarget> WORLD_TARGETS =
            List.of(WorldPaintTarget.PIPES, WorldPaintTarget.GLASS, WorldPaintTarget.REACTOR, WorldPaintTarget.ACCELERATOR,
                    WorldPaintTarget.SUPPORTS, WorldPaintTarget.PIPE_BOOSTER, WorldPaintTarget.VAULTS, WorldPaintTarget.SILO, WorldPaintTarget.PYLON, WorldPaintTarget.UPLINK, WorldPaintTarget.TAGGED, WorldPaintTarget.TAGGED_CONNECTED);

    private PaintTargets() {
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

    /** The colour a cartridge in {@code stack} paints with: Oritech's Color Cartridges, or this mod's Rainbow Cartridge. */
    @Nullable
    public static Paint cartridgePaint(ItemStack stack) {
        if (stack.getItem() instanceof ColorCartridgeItem cartridge) return Paint.of(cartridge.variant);
        if (stack.is(ModContent.RAINBOW_CARTRIDGE.get())) return Paint.RAINBOW;
        return null;
    }

    /**
     * Loader hook: a player right-clicked a block. Returns the result to cancel the click with, or null to let
     * the click behave normally.
     */
    @Nullable
    public static InteractionResult onRightClickBlock(Level level, BlockPos pos, Player player, InteractionHand hand, ItemStack stack) {
        var paint = cartridgePaint(stack);
        if (paint == null) return null;
        var target = find(level, pos);
        if (target == null) return null;

        if (level instanceof ServerLevel serverLevel) {
            if (!tryPaint(serverLevel, pos, player, hand, stack)) return null;
        } else if (target.alreadyPainted(level, pos, paint)) {
            return null; // nothing to do: let the click behave normally
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    /**
     * Paints whatever is at {@code pos} with the cartridge in {@code stack}, together with its group.
     * Mirrors Oritech's ColorCartridgeItem: splash sound, gust particles.
     * <p>
     * Cost: see {@link #paintGroup}.
     *
     * @return false if the block can't be painted, or nothing would change (no cartridge is used).
     */
    public static boolean tryPaint(ServerLevel level, BlockPos pos, Player player, InteractionHand hand, ItemStack stack) {
        var paint = cartridgePaint(stack);
        if (paint == null) return false;
        var target = find(level, pos);
        if (target == null) return false;
        var group = target.group(level, pos);
        if (group.isEmpty()) return false;

        boolean free = player.getAbilities().instabuild;
        var painted = paintGroup(level, target, group, paint, free ? Integer.MAX_VALUE : stack.getCount());
        if (painted == null) return false;

        if (!free) {
            stack.shrink(painted.cost());
            player.setItemInHand(hand, stack);
        }
        paintEffects(level, pos);
        return true;
    }

    /** What one paint action did: how many blocks changed and how many cartridges (or tool charges) it costs. */
    public record Painted(int changed, int cost) {
    }

    /**
     * Paints {@code group} through {@code target} with at most {@code available} cartridges' worth of paint.
     * Cost: one for a single block. For a group it follows the config: one for the whole group, or one per block
     * (painting stops when the paint runs out).
     *
     * @return what changed, or null if nothing did.
     */
    @Nullable
    public static Painted paintGroup(ServerLevel level, PaintTarget target, List<BlockPos> group, Paint paint, int available) {
        if (group.isEmpty() || available <= 0) return null;
        boolean perBlock = group.size() > 1 && Config.pipePaintCostMode() == Config.PipePaintCostMode.PER_BLOCK;
        var changed = target.apply(level, group, paint, perBlock ? available : Integer.MAX_VALUE);
        if (changed.isEmpty()) return null;
        return new Painted(changed.size(), perBlock ? changed.size() : 1);
    }

    /** Oritech's Color Cartridge feedback: splash sound and a gust of particles. */
    public static void paintEffects(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.AXOLOTL_SPLASH, SoundSource.PLAYERS, 1f, 0.6f);
        var at = pos.getCenter();
        level.sendParticles(ParticleTypes.GUST, at.x, at.y, at.z, 1,
                level.random.nextFloat(), level.random.nextFloat(), level.random.nextFloat(), 0.15f);
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
    public static void onBlockPlaced(ServerLevel level, BlockPos pos) {
        var target = worldTarget(level, pos);
        if (target == null) return;

        var data = PipePaintData.get(level);
        var before = data.getOrDefault(pos);
        // A vault placed from a painted item keeps that paint; otherwise blocks take their neighbours' colour.
        var placed = level.getBlockEntity(pos) instanceof CargoVaultBlockEntity vault ? vault.takePlacedPaint() : null;
        var inherited = placed != null && PaintColors.isPainted(placed) ? placed : target.inheritanceColor(level, pos);
        if (inherited != null) {
            data.set(pos, inherited);
            if (target == WorldPaintTarget.GLASS) {
                // New glass next to tinted glass of the same colour is tinted too.
                for (var direction : net.minecraft.core.Direction.values()) {
                    var neighbour = pos.relative(direction);
                    if (data.isTinted(neighbour) && data.getOrDefault(neighbour) == inherited) {
                        data.setTinted(pos, true);
                        break;
                    }
                }
            }
            syncWorld(level, List.of(pos), inherited);
        } else if (data.has(pos)) {
            // Left over from a block that was removed without being broken by hand (an explosion, a piston).
            data.remove(pos);
            syncWorld(level, List.of(pos), Paint.ORANGE);
        }
        if (target.isolated() && data.getOrDefault(pos) != Paint.ORANGE) {
            // The pipe connected to its neighbours while still unpainted; drop the links its colour doesn't allow.
            PipeIsolation.onPaintChanged(level, java.util.Map.of(pos.immutable(), Paint.ORANGE));
        } else if (target.isolated() && before != Paint.ORANGE) {
            PipeIsolation.onPaintChanged(level, java.util.Map.of(pos.immutable(), before));
        }
    }

    /** Loader hook: a player broke the block at {@code pos} (before it is removed). */
    public static void onBlockBreak(ServerLevel level, BlockPos pos) {
        var data = PipePaintData.get(level);
        if (level.getBlockEntity(pos) instanceof CargoVaultBlockEntity vault) vault.rememberPaint(data.getOrDefault(pos));
        if (!data.has(pos)) return;

        data.remove(pos);
        syncWorld(level, List.of(pos), Paint.ORANGE);
    }

    /** Loader hook: {@code chunk} was just sent to {@code player}. */
    public static void onChunkSent(ServerPlayer player, ServerLevel level, ChunkPos chunk) {
        var data = PipePaintData.get(level);
        var positions = data.inChunk(chunk);
        if (positions.isEmpty()) return;

        var entries = new ArrayList<PaintSyncPayload.Entry>(positions.size());
        for (var pos : positions) {
            entries.add(PaintSyncPayload.Entry.inWorld(pos, data.getOrDefault(pos), data.isTinted(pos)));
        }
        Platform.INSTANCE.sendToPlayer(player, new PaintSyncPayload(entries));
    }

    /** Tells every player who can see {@code positions} that they now have {@code variant} (ORANGE = cleared). */
    public static void syncWorld(ServerLevel level, List<BlockPos> positions, Paint variant) {
        if (positions.isEmpty()) return;
        var data = PipePaintData.get(level);
        var entries = new ArrayList<PaintSyncPayload.Entry>(positions.size());
        for (var pos : positions) {
            entries.add(PaintSyncPayload.Entry.inWorld(pos, variant, data.isTinted(pos)));
        }
        var payload = new PaintSyncPayload(entries);

        // Once per affected chunk; a player tracking several of them gets the list more than once, which is harmless.
        var sent = new HashSet<Long>();
        for (var pos : positions) {
            if (sent.add(ChunkPos.asLong(pos))) {
                Platform.INSTANCE.sendToPlayersTrackingChunk(level, new ChunkPos(pos), payload);
            }
        }
    }
}
