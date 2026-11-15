package io.github.keellam4.oritechextensions.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.keellam4.oritechextensions.storage.CargoVaultBlock;
import io.github.keellam4.oritechextensions.storage.CargoVaultBlockEntity;
import io.github.keellam4.oritechextensions.storage.VaultLayout;
import io.github.keellam4.oritechextensions.storage.FluidVaultBlockEntity;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Matrix4f;

/**
 * Draws what's inside a vault, for each slot: the item floating in its glass window, the count under it, and the
 * neon fill gauge above it (the dim strip in the texture lights up from left to right as the slot fills).
 */
public class CargoVaultRenderer<T extends CargoVaultBlockEntity> implements BlockEntityRenderer<T> {

    private static final int GAUGE_COLOUR = 0xFF9CFFF5;
    private static final int TEXT_COLOUR = 0xFFDFFFFB;
    private static final int LOCKED_TEXT_COLOUR = 0xFFFFC27A;

    private final ItemRenderer items;
    private final Font font;

    public CargoVaultRenderer(BlockEntityRendererProvider.Context context) {
        this.items = context.getItemRenderer();
        this.font = context.getFont();
    }

    @Override
    public void render(T vault, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var state = vault.getBlockState();
        if (!state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) || !(state.getBlock() instanceof CargoVaultBlock block)) return;
        var facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        var level = vault.getLevel();
        if (level == null) return;

        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        // Now the front is the +z face, seen from outside: a point (u, v) of the front is at x = u - 0.5, y = 0.5 - v.

        var cells = block.layout().cells();
        if (vault instanceof FluidVaultBlockEntity tank) {
            renderFluid(tank, cells[0], pose, buffers, facing);
            pose.popPose();
            return;
        }
        float t = level.getGameTime() + partialTick;
        for (int slot = 0; slot < cells.length && slot < vault.slots(); slot++) {
            var cell = cells[slot];
            if (vault.count(slot) > 0) drawGauge(pose, buffers, cell, vault.fill(slot));
            var item = vault.item(slot);
            if (item.isEmpty()) continue;

            float width = cell.glassU1() - cell.glassU0();
            float height = cell.glassV1() - cell.glassV0();
            float cx = (cell.glassU0() + cell.glassU1()) / 2 - 0.5f;
            float cy = 0.5f - (cell.glassV0() + height * 0.42f);
            float size = Math.min(width, height) * (block.layout() == VaultLayout.SINGLE ? 0.84f : 0.7f);
            float phase = slot * 1.7f;

            pose.pushPose();
            pose.translate(cx, cy + Mth.sin(t / 20f + phase) * height * 0.02f, 0.5 + size * 0.1f);
            pose.mulPose(Axis.YP.rotationDegrees(180f + Mth.sin(t / 30f + phase) * 18f));
            pose.scale(size, size, size);
            items.renderStatic(item, ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, pose, buffers,
                    level, (int) vault.getBlockPos().asLong() + slot);
            pose.popPose();

            drawCount(pose, buffers, format(vault.count(slot)), vault.isLocked(), cx, 0.5f - (cell.glassV1() - height * 0.2f), height * 0.19f);
        }
        pose.popPose();
    }

    private void renderFluid(FluidVaultBlockEntity tank, VaultLayout.Cell cell, PoseStack pose, MultiBufferSource buffers, net.minecraft.core.Direction facing) {
        if (tank.amount() <= 0) return;
        drawGauge(pose, buffers, cell, tank.fill());
        var level = tank.getLevel();
        var fluid = tank.fluid();
        var sprite = ClientPlatform.INSTANCE.fluidSprite(fluid);
        int colour = ClientPlatform.INSTANCE.fluidColour(fluid, level, tank.getBlockPos());
        int light = fluid.defaultFluidState().createLegacyBlock().getLightEmission() > 0 ? LightTexture.FULL_BRIGHT
                : net.minecraft.client.renderer.LevelRenderer.getLightColor(level, tank.getBlockPos().relative(facing));

        // The fluid fills the glass from the bottom, as high as the tank is full (at least a sliver).
        float height = cell.glassV1() - cell.glassV0();
        float level01 = Math.max(0.06f, tank.fill());
        float left = cell.glassU0() - 0.5f;
        float right = cell.glassU1() - 0.5f;
        float bottom = 0.5f - cell.glassV1();
        float top = bottom + height * level01;
        float z = 0.5f + 0.001f;
        float u0 = sprite.getU0();
        float u1 = sprite.getU0() + (sprite.getU1() - sprite.getU0()) * (cell.glassU1() - cell.glassU0());
        float v1 = sprite.getV1();
        float v0 = sprite.getV1() - (sprite.getV1() - sprite.getV0()) * height * level01;
        var consumer = buffers.getBuffer(RenderType.entityTranslucentCull(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS));
        var last = pose.last();
        consumer.addVertex(last, left, bottom, z).setColor(colour).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(last, 0, 0, 1);
        consumer.addVertex(last, right, bottom, z).setColor(colour).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(last, 0, 0, 1);
        consumer.addVertex(last, right, top, z).setColor(colour).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(last, 0, 0, 1);
        consumer.addVertex(last, left, top, z).setColor(colour).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(last, 0, 0, 1);

        float cx = (cell.glassU0() + cell.glassU1()) / 2 - 0.5f;
        drawCount(pose, buffers, format((int) Math.min(Integer.MAX_VALUE, tank.amount() / 1000)) + "B", tank.isLocked(),
                cx, 0.5f - (cell.glassV1() - height * 0.2f), height * 0.19f);
    }

    private static void drawGauge(PoseStack pose, MultiBufferSource buffers, VaultLayout.Cell cell, float fill) {
        int pixels = cell.gaugePixels();
        int lit = Math.max(1, Math.min(pixels, Math.round(fill * pixels)));
        VertexConsumer consumer = buffers.getBuffer(RenderType.lightning());
        Matrix4f matrix = pose.last().pose();
        float z = 0.5f + 0.0015f;
        float top = 0.5f - cell.gaugeV0();
        float bottom = 0.5f - cell.gaugeV1();
        float pixel = (cell.gaugeU1() - cell.gaugeU0()) / pixels;
        for (int i = 0; i < lit; i++) {
            float left = cell.gaugeU0() + i * pixel - 0.5f;
            float right = left + pixel;
            // Counter-clockwise seen from the front.
            consumer.addVertex(matrix, left, bottom, z).setColor(GAUGE_COLOUR);
            consumer.addVertex(matrix, right, bottom, z).setColor(GAUGE_COLOUR);
            consumer.addVertex(matrix, right, top, z).setColor(GAUGE_COLOUR);
            consumer.addVertex(matrix, left, top, z).setColor(GAUGE_COLOUR);
        }
    }

    /** Count text centred at ({@code x}, {@code y}), {@code height} blocks tall. */
    private void drawCount(PoseStack pose, MultiBufferSource buffers, String text, boolean locked, float x, float y, float height) {
        pose.pushPose();
        pose.translate(x, y, 0.5 + 0.002);
        float scale = height / font.lineHeight;
        pose.scale(scale, -scale, scale);
        font.drawInBatch(text, -font.width(text) / 2f, -font.lineHeight / 2f, locked ? LOCKED_TEXT_COLOUR : TEXT_COLOUR, false,
                pose.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    /** 950, 1.2k, 12k, 1.2M */
    static String format(int count) {
        if (count < 1000) return Integer.toString(count);
        if (count < 10_000) return String.format(java.util.Locale.ROOT, "%.1fk", count / 1000f);
        if (count < 1_000_000) return (count / 1000) + "k";
        return String.format(java.util.Locale.ROOT, "%.1fM", count / 1_000_000f);
    }
}
