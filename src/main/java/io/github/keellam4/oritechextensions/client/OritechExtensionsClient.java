package io.github.keellam4.oritechextensions.client;

import io.github.keellam4.oritechextensions.ModContent;
import io.github.keellam4.oritechextensions.OritechExtensions;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = OritechExtensions.MODID, dist = Dist.CLIENT)
public class OritechExtensionsClient {

    public OritechExtensionsClient(IEventBus modEventBus, ModContainer container) {
        // Registered directly on the mod bus so it works on every NeoForge 21.1.x build.
        modEventBus.addListener(OritechExtensionsClient::registerRenderers);
        PaintClient.register(modEventBus, NeoForge.EVENT_BUS);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModContent.HANGAR_DOOR_EXTENSION_ENTITY.get(), HangarDoorExtensionRenderer::new);
    }
}
