package io.github.keellam4.oritechextensions.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.keellam4.oritechextensions.storage.CargoVaultBlock;
import io.github.keellam4.oritechextensions.storage.CargoVaultBlockEntity;
import io.github.keellam4.oritechextensions.storage.VaultLayout;
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
public class CargoVaultRenderer implements BlockEntityRenderer<CargoVaultBlockEntity> {

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
    public void render(CargoVaultBlockEntity vault, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var state = vault.getBlockState();
        if (!state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) || !(state.getBlock() instanceof CargoVaultBlock block)) return;
        var facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        var level = vault.getLevel();
        if (level == null) return;

        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        // Now the front is the +z face, seen from outside: a point (u, v) of the front is at x = u - 0.5, y = 0.5 - v.

        var cells = VaultLayout.cells(block.perSide());
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
            float size = Math.min(width, height) * (block.perSide() == 1 ? 0.84f : 0.7f);
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
