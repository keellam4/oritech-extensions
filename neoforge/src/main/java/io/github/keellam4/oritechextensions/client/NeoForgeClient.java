package io.github.keellam4.oritechextensions.client;

import io.github.keellam4.oritechextensions.ModContent;
import io.github.keellam4.oritechextensions.OritechExtensions;
import io.github.keellam4.oritechextensions.paint.PaintTargets;
import io.github.keellam4.oritechextensions.paint.PipePaint;
import io.github.keellam4.oritechextensions.paint.targets.AddonTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.TextureAtlasStitchedEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.Map;

/** NeoForge client entry point: renderers, painted block models and client events. */
@Mod(value = OritechExtensions.MODID, dist = Dist.CLIENT)
public final class NeoForgeClient {

    public NeoForgeClient(IEventBus modBus, ModContainer container) {
        // Registered directly on the mod bus so it works on every NeoForge 21.1.x build.
        modBus.addListener(NeoForgeClient::registerRenderers);
        modBus.addListener(NeoForgeClient::wrapPaintableModels);
        modBus.addListener((TextureAtlasStitchedEvent event) -> PaintClient.checkPaintedTextures(event.getAtlas()));
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        var game = NeoForge.EVENT_BUS;
        game.addListener((ItemTooltipEvent event) -> {
            var line = PaintClient.paintTooltip(event.getItemStack());
            if (line != null) event.getToolTip().add(line);
        });
        game.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> PaintClient.forgetWorldPaint());
        if (PaintClient.smokeTest()) game.addListener((ClientTickEvent.Post event) -> PaintClient.smokeTestTick());
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModContent.HANGAR_DOOR_EXTENSION_ENTITY.get(), HangarDoorExtensionRenderer::new);
    }

    /**
     * Wraps the baked models of every block a {@link io.github.keellam4.oritechextensions.paint.PaintTarget}
     * covers, so painted blocks use painted textures.
     */
    private static void wrapPaintableModels(ModelEvent.ModifyBakingResult event) {
        var models = event.getModels();
        var sprites = new PaintedSprites();
        int addons = 0;
        int pipes = 0;
        int others = 0;

        for (var block : BuiltInRegistries.BLOCK) {
            var target = PaintTargets.findForModel(block);
            if (target == null) continue;
            for (var state : block.getStateDefinition().getPossibleStates()) {
                var location = BlockModelShaper.stateToModelLocation(state);
                var model = models.get(location);
                if (model != null && !(model instanceof PaintableModel)) {
                    models.put(location, new PaintableModel(model, sprites));
                    if (target == AddonTarget.INSTANCE) addons++;
                    else if (PipePaint.isPaintablePipe(block)) pipes++;
                    else others++;
                }
            }
        }
        OritechExtensions.LOGGER.info("[paint] Made {} addon block models and {} pipe block models paintable, plus {} glass, reactor and accelerator block models",
                addons, pipes, others);
        wrapResourcePackPaintables(models, sprites);
    }

    /**
     * Blocks a modpack made paintable with the {@code oritech_extensions:paintable(_connected)} tags. Tags only
     * reach the client after joining a world, long after models are baked, so instead any block whose model uses
     * a texture that a resource pack added to the painted-texture atlas is made paintable (see PackPaintables).
     */
    private static void wrapResourcePackPaintables(Map<ModelResourceLocation, BakedModel> models, PaintedSprites sprites) {
        var textures = PackPaintables.texturesFromOtherPacks(Minecraft.getInstance().getResourceManager());
        if (textures.isEmpty()) return;
        var random = RandomSource.create(42);
        int wrapped = 0;
        for (var block : BuiltInRegistries.BLOCK) {
            if (PaintTargets.findForModel(block) != null) continue;
            for (var state : block.getStateDefinition().getPossibleStates()) {
                var location = BlockModelShaper.stateToModelLocation(state);
                var model = models.get(location);
                if (model == null || model instanceof PaintableModel) continue;
                if (PackPaintables.usesAny(model, state, textures, random)) {
                    models.put(location, new PaintableModel(model, sprites));
                    wrapped++;
                }
            }
        }
        OritechExtensions.LOGGER.info("[paint] {} textures from resource packs; made {} more block models paintable", textures.size(), wrapped);
    }
}
