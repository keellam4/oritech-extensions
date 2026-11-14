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
        ClientPlayNetworking.registerGlobalReceiver(PaintSyncPayload.TYPE, (payload, context) -> PaintClient.handleSync(payload));
        ItemTooltipCallback.EVENT.register((stack, tooltipContext, flag, lines) -> {
            var line = PaintClient.paintTooltip(stack);
            if (line != null) lines.add(line);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> PaintClient.forgetWorldPaint());

        FabricPaintModels.register();
        ClientTickEvents.END_CLIENT_TICK.register(FabricPaintModels::afterReload);
        if (PaintClient.smokeTest()) ClientTickEvents.END_CLIENT_TICK.register(client -> PaintClient.smokeTestTick());
    }
}
