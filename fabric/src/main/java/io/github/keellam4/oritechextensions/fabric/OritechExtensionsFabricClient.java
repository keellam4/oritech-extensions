package io.github.keellam4.oritechextensions.fabric;

import io.github.keellam4.oritechextensions.ModContent;
import io.github.keellam4.oritechextensions.client.FabricPaintModels;
import io.github.keellam4.oritechextensions.client.HangarDoorExtensionRenderer;
import io.github.keellam4.oritechextensions.client.PaintClient;
import io.github.keellam4.oritechextensions.paint.PaintSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

/** Fabric client entry point: renderers, painted block models and client events. */
public final class OritechExtensionsFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        BlockEntityRenderers.register(ModContent.HANGAR_DOOR_EXTENSION_ENTITY.get(), HangarDoorExtensionRenderer::new);
        BlockEntityRenderers.register(ModContent.CARGO_VAULT_ENTITY.get(), io.github.keellam4.oritechextensions.client.CargoVaultRenderer::new);
        BlockEntityRenderers.register(ModContent.COMPACTING_VAULT_ENTITY.get(), io.github.keellam4.oritechextensions.client.CargoVaultRenderer::new);
        BlockEntityRenderers.register(ModContent.FLUID_VAULT_ENTITY.get(), io.github.keellam4.oritechextensions.client.CargoVaultRenderer::new);
        BlockEntityRenderers.register(ModContent.SILO_CONTROLLER_ENTITY.get(), io.github.keellam4.oritechextensions.client.SiloRenderer::new);
        BlockEntityRenderers.register(ModContent.INDUCTION_PYLON_ENTITY.get(), io.github.keellam4.oritechextensions.client.PylonRenderer::new);
        for (var pylon : io.github.keellam4.oritechextensions.wireless.InductionPylonBlock.all()) {
            net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(pylon, net.minecraft.client.renderer.RenderType.cutout());
        }
        net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(ModContent.SILO_GLASS.get(), net.minecraft.client.renderer.RenderType.cutout());
        net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlocks(net.minecraft.client.renderer.RenderType.cutout(),
                ModContent.TRANSPARENT_FLUID_PIPE.get(), ModContent.TRANSPARENT_FLUID_PIPE_CONNECTION.get());
        ClientPlayNetworking.registerGlobalReceiver(PaintSyncPayload.TYPE, (payload, context) -> PaintClient.handleSync(payload));
        ItemTooltipCallback.EVENT.register((stack, tooltipContext, flag, lines) -> {
            var line = PaintClient.paintTooltip(stack);
            if (line != null) lines.add(line);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> PaintClient.forgetWorldPaint());

        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register((graphics, delta) ->
                io.github.keellam4.oritechextensions.client.VaultHud.render(graphics));
        io.github.keellam4.oritechextensions.storage.CargoVault.clientSlotLookup = io.github.keellam4.oritechextensions.client.VaultHud::slotUnderCrosshair;

        net.minecraft.client.gui.screens.MenuScreens.register(ModContent.VAULT_UPGRADE_MENU.get(),
                io.github.keellam4.oritechextensions.client.VaultUpgradeScreen::new);
        net.minecraft.client.gui.screens.MenuScreens.register(ModContent.TERMINAL_MENU.get(),
                io.github.keellam4.oritechextensions.client.TerminalScreen::new);
        net.minecraft.client.gui.screens.MenuScreens.register(ModContent.CRAFTING_TERMINAL_MENU.get(),
                io.github.keellam4.oritechextensions.client.TerminalScreen::new);
        net.minecraft.client.gui.screens.MenuScreens.register(ModContent.SILO_MENU.get(),
                io.github.keellam4.oritechextensions.client.SiloScreen::new);
        ClientPlayNetworking.registerGlobalReceiver(io.github.keellam4.oritechextensions.storage.TerminalSyncPayload.TYPE,
                (payload, context) -> io.github.keellam4.oritechextensions.client.TerminalScreen.receive(payload));

        FabricPaintModels.register();
        ClientTickEvents.END_CLIENT_TICK.register(FabricPaintModels::afterReload);
        if (PaintClient.smokeTest()) ClientTickEvents.END_CLIENT_TICK.register(client -> PaintClient.smokeTestTick());
    }
}
