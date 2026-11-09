package dev.garciajg.oritechextensions.client;

import dev.garciajg.oritechextensions.ModContent;
import dev.garciajg.oritechextensions.OritechExtensions;
import dev.garciajg.oritechextensions.paint.PaintColors;
import dev.garciajg.oritechextensions.paint.PaintEvents;
import dev.garciajg.oritechextensions.paint.PaintSyncPayload;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import rearth.oritech.block.blocks.addons.MachineAddonBlock;

/** Client side of addon painting: receives colours, wraps addon block models, item tooltip. */
public final class PaintClient {

    private PaintClient() {
    }

    public static void register(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(PaintClient::wrapAddonModels);
        modBus.addListener(PaintClient::checkPaintedTextures);
        gameBus.addListener(PaintClient::addTooltip);
        if (Boolean.getBoolean(SMOKE_TEST_PROPERTY)) gameBus.addListener(PaintClient::quitWhenLoaded);
    }

    /** Set by the clientSmokeTest run: load everything, report, then quit once the first menu is shown. */
    private static final String SMOKE_TEST_PROPERTY = "oritech_extensions.smokeTest";

    private static void quitWhenLoaded(ClientTickEvent.Post event) {
        var minecraft = Minecraft.getInstance();
        // Loading is done once the resource-loading overlay is gone and a menu (title or first-launch screen) is open.
        if (minecraft.getOverlay() == null && minecraft.screen != null) {
            OritechExtensions.LOGGER.info("[smoke test] Title screen reached ({}), quitting", minecraft.screen.getClass().getSimpleName());
            minecraft.stop();
        }
    }

    /** Logs whether the generated painted textures made it into the block atlas (one sample per mod). */
    private static void checkPaintedTextures(TextureAtlasStitchedEvent event) {
        if (!event.getAtlas().location().equals(InventoryMenu.BLOCK_ATLAS)) return;
        for (var sample : new String[]{
                "oritech:block/machine_speed_addon_on_paint_white",
                "oritech:block/capacitor_ctm/horizontal_paint_netherite", // Capacitor Addon Extender (Athena connected textures)
                "oritechthings:block/addon_block_speed_paint_sculk"}) {
            var sprite = event.getAtlas().getSprite(ResourceLocation.parse(sample));
            boolean found = !sprite.contents().name().equals(MissingTextureAtlasSprite.getLocation());
            OritechExtensions.LOGGER.info("[paint] Painted texture {}: {}", sample, found ? "present" : "MISSING");
        }
    }

    /** Applies colours from the server and re-renders the affected blocks. */
    public static void handleSync(PaintSyncPayload payload) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;

        for (var entry : payload.entries()) {
            var blockEntity = level.getBlockEntity(entry.pos());
            if (!PaintEvents.isPaintable(blockEntity)) continue;

            var variant = PaintColors.fromId(entry.color());
            if (PaintColors.isPainted(variant)) {
                blockEntity.setData(ModContent.PAINT, variant);
            } else {
                blockEntity.removeData(ModContent.PAINT);
            }

            var state = level.getBlockState(entry.pos());
            level.sendBlockUpdated(entry.pos(), state, state, Block.UPDATE_IMMEDIATE); // rebuild the chunk mesh now
        }
    }

    /**
     * Wraps the baked model of every block built on Oritech's MachineAddonBlock (Oritech's own addons and those of
     * addon mods such as Oritech Things) so painted blocks render with the painted textures.
     */
    private static void wrapAddonModels(ModelEvent.ModifyBakingResult event) {
        var models = event.getModels();
        var sprites = new PaintedSprites();
        int wrapped = 0;

        for (var block : BuiltInRegistries.BLOCK) {
            if (!(block instanceof MachineAddonBlock)) continue;
            for (var state : block.getStateDefinition().getPossibleStates()) {
                var location = BlockModelShaper.stateToModelLocation(state);
                var model = models.get(location);
                if (model != null && !(model instanceof PaintableModel)) {
                    models.put(location, new PaintableModel(model, sprites));
                    wrapped++;
                }
            }
        }
        OritechExtensions.LOGGER.info("[paint] Made {} addon block models paintable", wrapped);
    }

    private static void addTooltip(ItemTooltipEvent event) {
        var paint = event.getItemStack().get(ModContent.PAINT_COMPONENT.get());
        if (!PaintColors.isPainted(paint)) return;
        event.getToolTip().add(Component.translatable("tooltip.oritech_extensions.painted",
                Component.translatable(PaintColors.paintItemTranslationKey(paint))).withStyle(ChatFormatting.GRAY));
    }
}
