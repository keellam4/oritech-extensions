package io.github.keellam4.oritechextensions.fabric;

import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.NeoForgeConfigRegistry;
import io.github.keellam4.oritechextensions.Config;
import io.github.keellam4.oritechextensions.ModContent;
import io.github.keellam4.oritechextensions.OritechExtensions;
import io.github.keellam4.oritechextensions.paint.PaintEvents;
import io.github.keellam4.oritechextensions.paint.PaintSyncPayload;
import io.github.keellam4.oritechextensions.paint.PaintTargets;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.fml.config.ModConfig;

/**
 * Fabric entry point: registers content and forwards Fabric events to the shared code. Block placement, block
 * drops and chunk sending have no Fabric API event; small mixins in {@code fabric.mixin} cover those.
 */
public final class OritechExtensionsFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        ModContent.init();
        // Forge Config API Port gives Fabric the same config file and format as NeoForge.
        NeoForgeConfigRegistry.INSTANCE.register(OritechExtensions.MODID, ModConfig.Type.COMMON, Config.SPEC);
        PayloadTypeRegistry.playS2C().register(PaintSyncPayload.TYPE, PaintSyncPayload.STREAM_CODEC);

        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.REDSTONE_BLOCKS).register(entries -> entries.accept(ModContent.HANGAR_DOOR_EXTENSION_ITEM.get()));
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.BUILDING_BLOCKS).register(entries -> entries.accept(ModContent.HANGAR_DOOR_EXTENSION_ITEM.get()));
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries -> entries.accept(ModContent.GUIDE.get()));

        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            var stack = player.getItemInHand(hand);
            PaintEvents.onRightClickBlock(level, player, stack);
            var result = PaintTargets.onRightClickBlock(level, hit.getBlockPos(), player, hand, stack);
            return result != null ? result : InteractionResult.PASS;
        });

        // Same moment as NeoForge's BreakEvent: before the block is removed.
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            if (level instanceof ServerLevel serverLevel) PaintTargets.onBlockBreak(serverLevel, pos);
            return true;
        });
    }
}
