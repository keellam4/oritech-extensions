package io.github.keellam4.oritechextensions.client;

import io.github.keellam4.oritechextensions.OritechExtensions;
import io.github.keellam4.oritechextensions.paint.PaintTargets;
import io.github.keellam4.oritechextensions.paint.PipePaint;
import io.github.keellam4.oritechextensions.paint.targets.AddonTarget;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Fabric's version of NeoForgeClient.wrapPaintableModels: wraps paintable block models as they are baked. */
public final class FabricPaintModels {

    private enum Kind {ADDON, PIPE, OTHER}

    private static final AtomicInteger ADDONS = new AtomicInteger();
    private static final AtomicInteger PIPES = new AtomicInteger();
    private static final AtomicInteger OTHERS = new AtomicInteger();
    private static final AtomicInteger FROM_PACKS = new AtomicInteger();
    private static volatile int packTextures;
    private static volatile boolean reportPending;

    private FabricPaintModels() {
    }

    public static void register() {
        ModelLoadingPlugin.register(context -> {
            var sprites = new PaintedSprites();
            var kinds = new HashMap<ModelResourceLocation, Kind>();
            var packCandidates = new HashMap<ModelResourceLocation, BlockState>();
            // Blocks a modpack made paintable with tags: see NeoForgeClient.wrapResourcePackPaintables.
            var textures = PackPaintables.texturesFromOtherPacks(Minecraft.getInstance().getResourceManager());

            for (var block : BuiltInRegistries.BLOCK) {
                var target = PaintTargets.findForModel(block);
                if (target == null && textures.isEmpty()) continue;
                var kind = target == null ? null
                        : target == AddonTarget.INSTANCE ? Kind.ADDON
                        : PipePaint.isPaintablePipe(block) ? Kind.PIPE
                        : Kind.OTHER;
                for (var state : block.getStateDefinition().getPossibleStates()) {
                    var location = BlockModelShaper.stateToModelLocation(state);
                    if (kind != null) kinds.put(location, kind);
                    else packCandidates.put(location, state);
                }
            }

            ADDONS.set(0);
            PIPES.set(0);
            OTHERS.set(0);
            FROM_PACKS.set(0);
            packTextures = textures.size();

            context.modifyModelAfterBake().register((model, bake) -> {
                var location = bake.topLevelId();
                if (model == null || location == null || model instanceof FabricPaintableModel) return model;
                var kind = kinds.get(location);
                if (kind != null) {
                    (kind == Kind.ADDON ? ADDONS : kind == Kind.PIPE ? PIPES : OTHERS).incrementAndGet();
                    reportPending = true;
                    return new FabricPaintableModel(model, sprites);
                }
                var state = packCandidates.get(location);
                if (state != null && PackPaintables.usesAny(model, state, textures, RandomSource.create(42))) {
                    FROM_PACKS.incrementAndGet();
                    return new FabricPaintableModel(model, sprites);
                }
                return model;
            });
        });
    }

    /** Every client tick: once a resource reload has finished, report what was wrapped and check the atlas. */
    public static void afterReload(Minecraft minecraft) {
        if (!reportPending || minecraft.getOverlay() != null) return;
        reportPending = false;
        OritechExtensions.LOGGER.info("[paint] Made {} addon block models and {} pipe block models paintable, plus {} glass, reactor and accelerator block models",
                ADDONS.get(), PIPES.get(), OTHERS.get());
        if (packTextures > 0) {
            OritechExtensions.LOGGER.info("[paint] {} textures from resource packs; made {} more block models paintable", packTextures, FROM_PACKS.get());
        }
        PaintClient.checkPaintedTextures(minecraft.getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS));
    }
}
