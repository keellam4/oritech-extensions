package io.github.keellam4.oritechextensions.client;

import io.github.keellam4.oritechextensions.ModContent;
import io.github.keellam4.oritechextensions.OritechExtensions;
import io.github.keellam4.oritechextensions.paint.PaintColors;
import io.github.keellam4.oritechextensions.paint.PaintEvents;
import io.github.keellam4.oritechextensions.paint.PaintSyncPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.TextureAtlasStitchedEvent;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.jetbrains.annotations.Nullable;
import rearth.oritech.util.ColorableMachine.ColorVariant;
import io.github.keellam4.oritechextensions.paint.PaintTargets;
import io.github.keellam4.oritechextensions.paint.PipePaint;
import io.github.keellam4.oritechextensions.paint.targets.AddonTarget;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Client side of painting: receives colours, wraps paintable block models, item tooltip. */
public final class PaintClient {

    private PaintClient() {
    }

    public static void register(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(PaintClient::wrapPaintableModels);
        modBus.addListener(PaintClient::checkPaintedTextures);
        gameBus.addListener(PaintClient::addTooltip);
        gameBus.addListener(PaintClient::forgetWorldPaint);
        if (Boolean.getBoolean(SMOKE_TEST_PROPERTY)) gameBus.addListener(PaintClient::quitWhenLoaded);
    }

    /**
     * Colours of blocks whose paint lives in the world rather than on a block entity (pipes, Industrial Glass,
     * reactor and accelerator blocks), as sent by the server.
     */
    private static final Map<BlockPos, ColorVariant> WORLD_PAINT = new ConcurrentHashMap<>();

    /** The world-stored colour at {@code pos}, or null if that block is unpainted. */
    @Nullable
    static ColorVariant worldPaint(BlockPos pos) {
        return WORLD_PAINT.get(pos);
    }

    /** Leaving a world: its colours must not show up at the same coordinates in the next one. */
    private static void forgetWorldPaint(ClientPlayerNetworkEvent.LoggingOut event) {
        WORLD_PAINT.clear();
    }

    /** Set by the clientSmokeTest run: load everything, report, then quit once the first menu is shown. */
    private static final String SMOKE_TEST_PROPERTY = "oritech_extensions.smokeTest";

    private static void quitWhenLoaded(ClientTickEvent.Post event) {
        var minecraft = Minecraft.getInstance();
        // Loading is done once the resource-loading overlay is gone and a menu (title or first-launch screen) is open.
        if (minecraft.getOverlay() == null && minecraft.screen != null) {
            OritechExtensions.LOGGER.info("[smoke test] Title screen reached ({}), quitting", minecraft.screen.getClass().getSimpleName());
            if (IconExporter.requested()) IconExporter.export(); // docs site icons (clientIconExport run)
            minecraft.stop();
        }
    }

    /** Logs whether the generated painted textures made it into the block atlas (one sample per mod). */
    private static void checkPaintedTextures(TextureAtlasStitchedEvent event) {
        if (!event.getAtlas().location().equals(InventoryMenu.BLOCK_ATLAS)) return;
        for (var sample : new String[]{
                "oritech:block/machine_speed_addon_on_paint_white",
                "oritech:block/capacitor_ctm/horizontal_paint_netherite", // Capacitor Addon Extender (Athena connected textures)
                "oritechthings:block/addon_block_speed_paint_sculk",
                "oritech:block/item_pipe_paint_white",
                "oritech:block/transparent_item_pipe_paint_redstone",
                "oritech:block/machine_extender_paint_netherite", // extender trim accents (separate palette)
                "oritech:block/fluid_pipe_paint_sculk",
                "oritech:block/industrial_glass_block/center_paint_white", // glass frames + reactor/accelerator parts palette
                "oritech:block/reactor_wall/horizontal_paint_sculk",
                "oritech:block/reactor_rod_paint_netherite",
                "oritech:block/reactor_heat_pipe_paint_camo",
                "oritech:block/accelerator_ring_paint_diamond"}) {
            var sprite = event.getAtlas().getSprite(Identifier.parse(sample));
            boolean found = !sprite.contents().name().equals(MissingTextureAtlasSprite.getLocation());
            OritechExtensions.LOGGER.info("[paint] Painted texture {}: {}", sample, found ? "present" : "MISSING");
        }
    }

    /** Applies colours from the server and re-renders the affected blocks. */
    public static void handleSync(PaintSyncPayload payload) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;

        for (var entry : payload.entries()) {
            var pos = entry.pos();
            var variant = PaintColors.fromId(entry.color());

            if (entry.world()) {
                if (PaintColors.isPainted(variant)) {
                    WORLD_PAINT.put(pos.immutable(), variant);
                } else {
                    WORLD_PAINT.remove(pos);
                }
            } else {
                var blockEntity = level.getBlockEntity(pos);
                if (!PaintEvents.isPaintable(blockEntity)) continue;
                if (PaintColors.isPainted(variant)) {
                    blockEntity.setData(ModContent.PAINT, variant);
                } else {
                    blockEntity.removeData(ModContent.PAINT);
                }
            }

            var state = level.getBlockState(pos);
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_IMMEDIATE); // rebuild the chunk mesh now
        }
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
    }

    private static void addTooltip(ItemTooltipEvent event) {
        var paint = event.getItemStack().get(ModContent.PAINT_COMPONENT.get());
        if (!PaintColors.isPainted(paint)) return;
        event.getToolTip().add(Component.translatable("tooltip.oritech_extensions.painted",
                Component.translatable(PaintColors.paintItemTranslationKey(paint))).withStyle(ChatFormatting.GRAY));
    }
}
