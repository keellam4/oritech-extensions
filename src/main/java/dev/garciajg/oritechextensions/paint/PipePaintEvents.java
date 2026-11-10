package dev.garciajg.oritechextensions.paint;

import dev.garciajg.oritechextensions.Config;
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
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import rearth.oritech.item.other.ColorCartridgeItem;
import rearth.oritech.util.ColorableMachine.ColorVariant;

import java.util.ArrayList;
import java.util.List;

/** Applies Oritech Color Cartridges to standard item/fluid pipe networks. */
public final class PipePaintEvents {

    private PipePaintEvents() {
    }

    public static void register(IEventBus gameBus) {
        gameBus.addListener(PipePaintEvents::onRightClickBlock);
        gameBus.addListener(PipePaintEvents::onBlockPlaced);
        gameBus.addListener(PipePaintEvents::onBlockBreak);
        gameBus.addListener(PipePaintEvents::onChunkSent);
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        var stack = event.getItemStack();
        if (!(stack.getItem() instanceof ColorCartridgeItem)) return;

        var level = event.getLevel();
        var pos = event.getPos();
        if (!PipePaint.isPaintablePipe(level, pos)) return;

        if (level instanceof ServerLevel serverLevel) {
            if (!tryPaint(serverLevel, pos, event.getEntity(), event.getHand(), stack)) return;
        } else {
            // Client: cancel if this looks like a paint click so the arm swings; actual colour comes via sync.
            // Already-same-colour is fine to cancel too — tryPaint returns false server-side with no consume.
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide()));
    }

    /**
     * Paints (or clears) the pipe network at {@code pos}.
     *
     * @return false if the block isn't a standard pipe, or nothing would change and no cartridges would be used.
     */
    public static boolean tryPaint(ServerLevel level, BlockPos pos, Player player, InteractionHand hand, ItemStack stack) {
        if (!(stack.getItem() instanceof ColorCartridgeItem cartridge)) return false;
        if (!PipePaint.isPaintablePipe(level, pos)) return false;

        var network = PipePaint.discoverNetwork(level, pos);
        if (network.isEmpty()) return false;

        var variant = cartridge.variant;
        var wouldChange = network.stream().anyMatch(p -> PipePaint.getPaint(level, p) != variant);
        if (!wouldChange) return false;

        List<BlockPos> changed;
        if (Config.pipePaintCostMode() == Config.PipePaintCostMode.PER_BLOCK) {
            int budget = player.getAbilities().instabuild ? Integer.MAX_VALUE : stack.getCount();
            changed = PipePaint.applyToNetworkBudgeted(level, network, variant, budget);
            if (changed.isEmpty()) return false;
            if (!player.getAbilities().instabuild) {
                stack.shrink(changed.size());
                player.setItemInHand(hand, stack);
            }
        } else {
            changed = PipePaint.applyToNetwork(level, network, variant);
            if (changed.isEmpty()) return false;
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
                player.setItemInHand(hand, stack);
            }
        }

        sync(level, changed, variant);
        level.playSound(null, pos, SoundEvents.AXOLOTL_SPLASH, SoundSource.PLAYERS, 1f, 0.6f);
        var at = pos.getCenter();
        level.sendParticles(ParticleTypes.GUST, at.x, at.y, at.z, 1,
                level.random.nextFloat(), level.random.nextFloat(), level.random.nextFloat(), 0.15f);
        return true;
    }

    private static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var pos = event.getPos();
        if (!PipePaint.isPaintablePipe(level, pos)) return;

        var inherited = PipePaint.inheritanceColor(level, pos);
        if (inherited == null) return;

        PipePaintData.get(level).set(pos, inherited);
        sync(level, List.of(pos), inherited);
    }

    private static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var pos = event.getPos();
        if (!PipePaintData.get(level).has(pos)) return;

        PipePaint.clearAt(level, pos);
        sync(level, List.of(pos), ColorVariant.ORANGE);
    }

    private static void onChunkSent(ChunkWatchEvent.Sent event) {
        var level = event.getLevel();
        var chunk = event.getChunk();
        var chunkPos = chunk.getPos();
        var data = PipePaintData.get(level);
        var entries = new ArrayList<PipePaintSyncPayload.Entry>();

        for (var entry : data.all().entrySet()) {
            var pos = entry.getKey();
            if (pos.getX() >> 4 != chunkPos.x || pos.getZ() >> 4 != chunkPos.z) continue;
            entries.add(new PipePaintSyncPayload.Entry(pos, PaintColors.toId(entry.getValue())));
        }
        if (!entries.isEmpty()) {
            PacketDistributor.sendToPlayer(event.getPlayer(), new PipePaintSyncPayload(entries));
        }
    }

    private static void sync(ServerLevel level, List<BlockPos> positions, ColorVariant variant) {
        if (positions.isEmpty()) return;
        var colorId = PaintColors.toId(variant);
        var entries = positions.stream()
                .map(pos -> new PipePaintSyncPayload.Entry(pos, colorId))
                .toList();

        // Send to everyone tracking any of the affected chunks.
        var sentChunks = new HashSetChunks();
        for (var pos : positions) {
            var chunkPos = new ChunkPos(pos);
            if (!sentChunks.add(chunkPos)) continue;
            PacketDistributor.sendToPlayersTrackingChunk(level, chunkPos, new PipePaintSyncPayload(entries));
        }
    }

    /** Tiny helper so we don't re-send the same full payload once per block in a chunk. */
    private static final class HashSetChunks {
        private final java.util.HashSet<Long> keys = new java.util.HashSet<>();

        boolean add(ChunkPos pos) {
            return keys.add(ChunkPos.asLong(pos.x, pos.z));
        }
    }
}
