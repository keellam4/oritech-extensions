package io.github.keellam4.oritechextensions.neoforge;

import io.github.keellam4.oritechextensions.Config;
import io.github.keellam4.oritechextensions.ModContent;
import io.github.keellam4.oritechextensions.OritechExtensions;
import io.github.keellam4.oritechextensions.paint.PaintEvents;
import io.github.keellam4.oritechextensions.paint.PaintSyncPayload;
import io.github.keellam4.oritechextensions.paint.PaintTargets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** NeoForge entry point: registers content and forwards NeoForge events to the shared code. */
@Mod(OritechExtensions.MODID)
public final class OritechExtensionsNeoForge {

    public OritechExtensionsNeoForge(IEventBus modBus, ModContainer container) {
        ModContent.init();
        NeoForgePlatform.registerAll(modBus);
        modBus.addListener(OritechExtensionsNeoForge::addCreative);
        modBus.addListener(OritechExtensionsNeoForge::registerPayloads);
        container.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        var game = NeoForge.EVENT_BUS;
        game.addListener(OritechExtensionsNeoForge::onRightClickBlock);
        game.addListener(OritechExtensionsNeoForge::onBlockPlaced);
        game.addListener(OritechExtensionsNeoForge::onBlockBreak);
        game.addListener(OritechExtensionsNeoForge::onBlockDrops);
        game.addListener(OritechExtensionsNeoForge::onChunkSent);
    }

    private static void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS || event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(ModContent.HANGAR_DOOR_EXTENSION_ITEM.get());
        }
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModContent.GUIDE.get());
        }
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar(PaintSyncPayload.VERSION).playToClient(PaintSyncPayload.TYPE, PaintSyncPayload.STREAM_CODEC,
                (payload, context) -> ClientHooks.handleSync(payload));
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        PaintEvents.onRightClickBlock(event.getLevel(), event.getEntity(), event.getItemStack());
        var result = PaintTargets.onRightClickBlock(event.getLevel(), event.getPos(), event.getEntity(), event.getHand(), event.getItemStack());
        if (result != null) {
            event.setCanceled(true);
            event.setCancellationResult(result);
        }
    }

    private static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (event.getEntity() instanceof Player player) PaintEvents.onBlockPlaced(level, event.getPos(), player);
        PaintTargets.onBlockPlaced(level, event.getPos());
    }

    private static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level) PaintTargets.onBlockBreak(level, event.getPos());
    }

    private static void onBlockDrops(BlockDropsEvent event) {
        var paint = PaintEvents.paintForDrops(event.getBlockEntity());
        if (paint == null) return;
        for (var drop : event.getDrops()) {
            drop.setItem(PaintEvents.paintDrop(event.getState(), drop.getItem(), paint));
        }
    }

    private static void onChunkSent(ChunkWatchEvent.Sent event) {
        PaintEvents.onChunkSent(event.getPlayer(), event.getChunk());
        PaintTargets.onChunkSent(event.getPlayer(), event.getLevel(), event.getChunk().getPos());
    }

    /** Keeps client-only classes out of the server: only touched when a payload arrives on the client. */
    private static final class ClientHooks {
        static void handleSync(PaintSyncPayload payload) {
            io.github.keellam4.oritechextensions.client.PaintClient.handleSync(payload);
        }
    }
}
