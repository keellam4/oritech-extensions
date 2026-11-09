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
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import rearth.oritech.block.blocks.addons.MachineAddonBlock;

/** Client side of addon painting: receives colours, wraps addon block models, item tooltip. */
public final class PaintClient {

    private PaintClient() {
    }

    public static void register(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(PaintClient::wrapAddonModels);
        gameBus.addListener(PaintClient::addTooltip);
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
        OritechExtensions.LOGGER.debug("Made {} addon block models paintable", wrapped);
    }

    private static void addTooltip(ItemTooltipEvent event) {
        var paint = event.getItemStack().get(ModContent.PAINT_COMPONENT.get());
        if (!PaintColors.isPainted(paint)) return;
        event.getToolTip().add(Component.translatable("tooltip.oritech_extensions.painted",
                Component.translatable(PaintColors.paintItemTranslationKey(paint))).withStyle(ChatFormatting.GRAY));
    }
}
