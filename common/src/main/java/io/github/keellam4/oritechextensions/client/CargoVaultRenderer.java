package io.github.keellam4.oritechextensions.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.keellam4.oritechextensions.storage.CargoVaultBlockEntity;
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
 * Draws what's inside a Cargo Vault: the item floating in the glass, the count under it, and the neon fill gauge
 * above it (the dim strip in the texture lights up from left to right as the vault fills).
 */
public class CargoVaultRenderer implements BlockEntityRenderer<CargoVaultBlockEntity> {

    /** The gauge: texture row 3, columns 3 to 12. */
    private static final int GAUGE_ROW = 3;
    private static final int GAUGE_FROM = 3;
    private static final int GAUGE_PIXELS = 10;
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
        if (!state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return;
        var facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);

        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        // Now the front is the +z face, seen from outside: texel (u, v) is at x = u/16 - 0.5, y = 0.5 - v/16.

        if (vault.count() > 0) drawGauge(pose, buffers, vault.fill());

        var item = vault.item();
        if (!item.isEmpty() && vault.getLevel() != null) {
            long time = vault.getLevel().getGameTime();
            float t = time + partialTick;
            pose.pushPose();
            pose.translate(0, 0.02 + Mth.sin(t / 20f) * 0.012, 0.5 + 0.04);
            pose.mulPose(Axis.YP.rotationDegrees(180f + Mth.sin(t / 30f) * 18f));
            pose.scale(0.42f, 0.42f, 0.42f);
            items.renderStatic(item, ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, pose, buffers,
                    vault.getLevel(), (int) vault.getBlockPos().asLong());
            pose.popPose();

            drawCount(pose, buffers, format(vault.count()), vault.isLocked());
        }
        pose.popPose();
    }

    private static void drawGauge(PoseStack pose, MultiBufferSource buffers, float fill) {
        int lit = Math.max(1, Math.min(GAUGE_PIXELS, Math.round(fill * GAUGE_PIXELS)));
        VertexConsumer consumer = buffers.getBuffer(RenderType.lightning());
        Matrix4f matrix = pose.last().pose();
        float z = 0.5f + 0.0015f;
        float top = 0.5f - GAUGE_ROW / 16f;
        float bottom = top - 1 / 16f;
        for (int i = 0; i < lit; i++) {
            float left = (GAUGE_FROM + i) / 16f - 0.5f;
            float right = left + 1 / 16f;
            // Counter-clockwise seen from the front.
            consumer.addVertex(matrix, left, bottom, z).setColor(GAUGE_COLOUR);
            consumer.addVertex(matrix, right, bottom, z).setColor(GAUGE_COLOUR);
            consumer.addVertex(matrix, right, top, z).setColor(GAUGE_COLOUR);
            consumer.addVertex(matrix, left, top, z).setColor(GAUGE_COLOUR);
        }
    }

    private void drawCount(PoseStack pose, MultiBufferSource buffers, String text, boolean locked) {
        pose.pushPose();
        // Bottom of the glass, texture rows 9 to 11.
        pose.translate(0, 0.5 - 9.6 / 16f, 0.5 + 0.002);
        float scale = 1.5f / 16f / font.lineHeight;
        pose.scale(scale, -scale, scale);
        float x = -font.width(text) / 2f;
        font.drawInBatch(text, x, 0, locked ? LOCKED_TEXT_COLOUR : TEXT_COLOUR, false, pose.last().pose(), buffers,
                Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
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
