package io.github.keellam4.oritechextensions.client;

import io.github.keellam4.oritechextensions.ModContent;
import io.github.keellam4.oritechextensions.OritechExtensions;
import io.github.keellam4.oritechextensions.paint.PaintColors;
import io.github.keellam4.oritechextensions.paint.PaintEvents;
import io.github.keellam4.oritechextensions.paint.PaintSyncPayload;
import io.github.keellam4.oritechextensions.platform.Platform;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;
import rearth.oritech.util.ColorableMachine.ColorVariant;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client side of painting shared by every loader: receives colours, item tooltip, smoke-test checks.
 * Each loader wires these to its own events and wraps paintable block models in its own way.
 */
public final class PaintClient {

    private PaintClient() {
    }

    /**
     * Colours of blocks whose paint lives in the world rather than on a block entity (pipes, Industrial Glass,
     * reactor and accelerator blocks, tagged blocks), as sent by the server.
     */
    private static final Map<BlockPos, ColorVariant> WORLD_PAINT = new ConcurrentHashMap<>();

    /** The world-stored colour at {@code pos}, or null if that block is unpainted. */
    @Nullable
    public static ColorVariant worldPaint(BlockPos pos) {
        return WORLD_PAINT.get(pos);
    }

    /** Leaving a world: its colours must not show up at the same coordinates in the next one. */
    public static void forgetWorldPaint() {
        WORLD_PAINT.clear();
    }

    /** Set by the clientSmokeTest run: load everything, report, then quit once the first menu is shown. */
    public static final String SMOKE_TEST_PROPERTY = "oritech_extensions.smokeTest";

    public static boolean smokeTest() {
        return Boolean.getBoolean(SMOKE_TEST_PROPERTY);
    }

    /** Every client tick during a smoke test. */
    public static void smokeTestTick() {
        var minecraft = Minecraft.getInstance();
        // Loading is done once the resource-loading overlay is gone and a menu (title or first-launch screen) is open.
        if (minecraft.getOverlay() == null && minecraft.screen != null) {
            OritechExtensions.LOGGER.info("[smoke test] Title screen reached ({}), quitting", minecraft.screen.getClass().getSimpleName());
            if (IconExporter.requested()) IconExporter.export(); // docs site icons (clientIconExport run)
            minecraft.stop();
        }
    }

    /** Logs whether the generated painted textures made it into the block atlas (one sample per mod). */
    public static void checkPaintedTextures(TextureAtlas atlas) {
        if (!atlas.location().equals(InventoryMenu.BLOCK_ATLAS)) return;
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
            var sprite = atlas.getSprite(ResourceLocation.parse(sample));
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
                Platform.INSTANCE.setAddonPaint(blockEntity, variant);
            }

            var state = level.getBlockState(pos);
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_IMMEDIATE); // rebuild the chunk mesh now
        }
    }

    /** The tooltip line for a painted addon item, or null. */
    @Nullable
    public static Component paintTooltip(ItemStack stack) {
        var paint = stack.get(ModContent.PAINT_COMPONENT.get());
        if (!PaintColors.isPainted(paint)) return null;
        return Component.translatable("tooltip.oritech_extensions.painted",
                Component.translatable(PaintColors.paintItemTranslationKey(paint))).withStyle(ChatFormatting.GRAY);
    }
}
