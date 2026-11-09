package dev.garciajg.oritechextensions.client;

import dev.garciajg.oritechextensions.ModContent;
import dev.garciajg.oritechextensions.OritechExtensions;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = OritechExtensions.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = OritechExtensions.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class OritechExtensionsClient {

    public OritechExtensionsClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModContent.HANGAR_DOOR_EXTENSION_ENTITY.get(), HangarDoorExtensionRenderer::new);
    }
}
