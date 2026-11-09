package dev.garciajg.oritechextensions;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import dev.garciajg.oritechextensions.paint.PaintEvents;
import dev.garciajg.oritechextensions.paint.PaintSyncPayload;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import org.slf4j.Logger;

@Mod(OritechExtensions.MODID)
public class OritechExtensions {

    public static final String MODID = "oritech_extensions";
    public static final Logger LOGGER = LogUtils.getLogger();

    public OritechExtensions(IEventBus modEventBus, ModContainer modContainer) {
        ModContent.register(modEventBus);
        modEventBus.addListener(this::addCreative);
        modEventBus.addListener(PaintSyncPayload::register);
        PaintEvents.register(NeoForge.EVENT_BUS);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS || event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(ModContent.HANGAR_DOOR_EXTENSION_ITEM.get());
        }
    }
}
