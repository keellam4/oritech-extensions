package io.github.keellam4.oritechextensions.neoforge;

import io.github.keellam4.oritechextensions.Config;
import io.github.keellam4.oritechextensions.ModContent;
import io.github.keellam4.oritechextensions.OritechExtensions;
import io.github.keellam4.oritechextensions.paint.PaintEvents;
import io.github.keellam4.oritechextensions.paint.PaintSyncPayload;
import io.github.keellam4.oritechextensions.paint.PaintTargets;
import io.github.keellam4.oritechextensions.tool.DetailingTool;
import io.github.keellam4.oritechextensions.storage.CargoVault;
import io.github.keellam4.oritechextensions.storage.VaultTakePayload;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import io.github.keellam4.oritechextensions.tool.DetailingToolPayload;
import net.minecraft.server.level.ServerPlayer;
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
        modBus.addListener(OritechExtensionsNeoForge::registerCapabilities);
        container.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        var game = NeoForge.EVENT_BUS;
        game.addListener(OritechExtensionsNeoForge::onRightClickBlock);
        game.addListener(OritechExtensionsNeoForge::onLeftClickBlock);
        game.addListener(OritechExtensionsNeoForge::onBlockPlaced);
        game.addListener(OritechExtensionsNeoForge::onBlockBreak);
        game.addListener(OritechExtensionsNeoForge::onBlockDrops);
        game.addListener(OritechExtensionsNeoForge::onChunkSent);
    }

    private static void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS || event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(ModContent.HANGAR_DOOR_EXTENSION_ITEM.get());
        }
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModContent.CARGO_VAULT_ITEM.get());
            event.accept(ModContent.DUAL_VAULT_ITEM.get());
            event.accept(ModContent.QUAD_VAULT_ITEM.get());
            event.accept(ModContent.GRID_VAULT_ITEM.get());
            event.accept(ModContent.COMPACTING_VAULT_ITEM.get());
            event.accept(ModContent.DUAL_COMPACTING_VAULT_ITEM.get());
            event.accept(ModContent.FLUID_VAULT_ITEM.get());
            event.accept(ModContent.CARGO_CONTROLLER_ITEM.get());
            event.accept(ModContent.CRAFTING_TERMINAL_ITEM.get());
            ModContent.VAULT_UPGRADES.values().forEach(upgrade -> event.accept(upgrade.get()));
        }
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModContent.GUIDE.get());
            event.accept(ModContent.DETAILING_TOOL.get());
            event.accept(ModContent.RAINBOW_CARTRIDGE.get());
        }
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar(PaintSyncPayload.VERSION).playToClient(PaintSyncPayload.TYPE, PaintSyncPayload.STREAM_CODEC,
                (payload, context) -> ClientHooks.handleSync(payload));
        event.registrar(DetailingToolPayload.VERSION).playToServer(DetailingToolPayload.TYPE, DetailingToolPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) DetailingTool.onScroll(player, payload.direction());
                }));
        event.registrar(io.github.keellam4.oritechextensions.storage.TerminalActionPayload.VERSION).playToServer(
                io.github.keellam4.oritechextensions.storage.TerminalActionPayload.TYPE, io.github.keellam4.oritechextensions.storage.TerminalActionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player().containerMenu instanceof io.github.keellam4.oritechextensions.storage.TerminalMenu menu
                            && menu.containerId == payload.containerId()) menu.handle(payload.action(), payload.item(), payload.fluid());
                }));
        event.registrar(io.github.keellam4.oritechextensions.storage.TerminalSyncPayload.VERSION).playToClient(
                io.github.keellam4.oritechextensions.storage.TerminalSyncPayload.TYPE, io.github.keellam4.oritechextensions.storage.TerminalSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientHooks.terminalSync(payload)));
        event.registrar(VaultTakePayload.VERSION).playToServer(VaultTakePayload.TYPE, VaultTakePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) CargoVault.take(player, payload.pos(), payload.slot(), payload.one());
                }));
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModContent.CARGO_VAULT_ENTITY.get(), (vault, side) -> new VaultItemHandler(vault));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModContent.COMPACTING_VAULT_ENTITY.get(), (vault, side) -> new VaultItemHandler(vault));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ModContent.FLUID_VAULT_ENTITY.get(), (vault, side) -> new VaultFluidHandler(vault));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModContent.CARGO_CONTROLLER_ENTITY.get(), (controller, side) -> new ControllerItemHandler(controller));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ModContent.CARGO_CONTROLLER_ENTITY.get(), (controller, side) -> new ControllerFluidHandler(controller));
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        PaintEvents.onRightClickBlock(event.getLevel(), event.getEntity(), event.getItemStack());
        var result = DetailingTool.onRightClickBlock(event.getLevel(), event.getPos(), event.getEntity(), event.getHand(), event.getItemStack());
        if (result == null) result = PaintTargets.onRightClickBlock(event.getLevel(), event.getPos(), event.getEntity(), event.getHand(), event.getItemStack());
        if (result != null) {
            event.setCanceled(true);
            event.setCancellationResult(result);
        }
    }

    private static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        var action = event.getAction();
        if (action != PlayerInteractEvent.LeftClickBlock.Action.START && action != PlayerInteractEvent.LeftClickBlock.Action.CLIENT_HOLD) return;
        if (event.getFace() != null && CargoVault.onLeftClick(event.getLevel(), event.getPos(), event.getEntity(), event.getFace())) {
            event.setCanceled(true);
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
        static void terminalSync(io.github.keellam4.oritechextensions.storage.TerminalSyncPayload payload) {
            io.github.keellam4.oritechextensions.client.TerminalScreen.receive(payload);
        }

        static void handleSync(PaintSyncPayload payload) {
            io.github.keellam4.oritechextensions.client.PaintClient.handleSync(payload);
        }
    }
}
