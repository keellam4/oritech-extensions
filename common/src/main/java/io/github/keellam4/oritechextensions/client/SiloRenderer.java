package io.github.keellam4.oritechextensions.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.keellam4.oritechextensions.storage.SiloControllerBlockEntity;
import io.github.keellam4.oritechextensions.storage.SiloStructure;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;

/**
 * Draws a Fluid Silo's fluids: one layer per fluid, heaviest at the bottom, each as tall as its share of the silo. The casing hides
 * it; the Silo Glass shows it. Drawn from the controller, so it doesn't vanish when the controller is off screen.
 */
public class SiloRenderer implements BlockEntityRenderer<SiloControllerBlockEntity> {

    /** Keeps the fluid just inside the glass, so it doesn't flicker against the faces. */
    private static final float INSET = 0.02f;

    public SiloRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(SiloControllerBlockEntity silo, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        var structure = silo.structure();
        long capacity = silo.fluidCapacity();
        if (structure == null || silo.amount() <= 0 || silo.getLevel() == null || capacity <= 0) return;
        // Layers by density: the heaviest at the bottom, gases on top, like oil on water.
        var layers = new java.util.ArrayList<>(silo.fluids());
        layers.sort((a, b) -> Integer.compare(ClientPlatform.INSTANCE.fluidDensity(b.fluid()), ClientPlatform.INSTANCE.fluidDensity(a.fluid())));
        var consumer = buffers.getBuffer(RenderType.entityTranslucentCull(InventoryMenu.BLOCK_ATLAS));
        float usable = structure.height() - 2 * INSET;
        float bottom = 0;
        for (var layer : layers) {
            // Even a little fluid shows as a thin layer.
            float depth = Math.max(0.1f, usable * layer.amount() / capacity);
            float top = Math.min(usable, bottom + depth);
            if (top > bottom) drawLayer(silo, structure, layer.fluid(), bottom, top, pose, consumer);
            bottom = top;
        }
    }

    /** One fluid's layer, from {@code from} to {@code to} blocks above the silo's floor (inside the inset). */
    private void drawLayer(SiloControllerBlockEntity silo, SiloStructure structure, net.minecraft.world.level.material.Fluid fluid,
                           float from, float to, PoseStack pose, VertexConsumer consumer) {
        var level = silo.getLevel();
        var sprite = ClientPlatform.INSTANCE.fluidSprite(fluid);
        int colour = ClientPlatform.INSTANCE.fluidColour(fluid, level, silo.getBlockPos());
        boolean glows = fluid.defaultFluidState().createLegacyBlock().getLightEmission() > 0;

        var base = silo.getBlockPos();
        var origin = structure.origin();
        float floor = origin.getY() - base.getY() + INSET;
        float x0 = origin.getX() - base.getX() + INSET;
        float z0 = origin.getZ() - base.getZ() + INSET;
        float x1 = x0 + SiloStructure.WIDTH - 2 * INSET;
        float z1 = z0 + SiloStructure.WIDTH - 2 * INSET;
        float y0 = floor + from;
        float y1 = floor + to;
        var face = new Face(consumer, pose.last(), sprite, colour);

        // Four walls, one quad per block so the texture tiles; lit by the block outside each face.
        for (int cell = 0; cell < SiloStructure.WIDTH; cell++) {
            float a = cell == 0 ? INSET : cell;
            float b = cell == SiloStructure.WIDTH - 1 ? SiloStructure.WIDTH - INSET : cell + 1;
            for (int row = (int) Math.floor(from); row < to + INSET; row++) {
                float bottom = Math.max(y0, origin.getY() - base.getY() + row);
                float top = Math.min(y1, origin.getY() - base.getY() + row + 1);
                if (top <= bottom) continue;
                float ax = origin.getX() - base.getX() + a, bx = origin.getX() - base.getX() + b;
                float az = origin.getZ() - base.getZ() + a, bz = origin.getZ() - base.getZ() + b;
                var rowPos = origin.offset(0, row, 0);
                face.light = light(level, rowPos.offset(cell, 0, 0).north(), glows);
                face.quad(bx, bottom, z0, ax, bottom, z0, ax, top, z0, bx, top, z0, b - a, top - bottom, 0, 0, -1);
                face.light = light(level, rowPos.offset(cell, 0, SiloStructure.WIDTH), glows);
                face.quad(ax, bottom, z1, bx, bottom, z1, bx, top, z1, ax, top, z1, b - a, top - bottom, 0, 0, 1);
                face.light = light(level, rowPos.offset(-1, 0, cell), glows);
                face.quad(x0, bottom, az, x0, bottom, bz, x0, top, bz, x0, top, az, b - a, top - bottom, -1, 0, 0);
                face.light = light(level, rowPos.offset(SiloStructure.WIDTH, 0, cell), glows);
                face.quad(x1, bottom, bz, x1, bottom, az, x1, top, az, x1, top, bz, b - a, top - bottom, 1, 0, 0);
            }
        }
        // The layer's surface; the next layer sits on top of it.
        face.light = light(level, origin.offset(1, structure.height(), 1), glows);
        for (int cx = 0; cx < SiloStructure.WIDTH; cx++) {
            for (int cz = 0; cz < SiloStructure.WIDTH; cz++) {
                float ax = origin.getX() - base.getX() + Math.max(cx, INSET);
                float bx = origin.getX() - base.getX() + Math.min(cx + 1, SiloStructure.WIDTH - INSET);
                float az = origin.getZ() - base.getZ() + Math.max(cz, INSET);
                float bz = origin.getZ() - base.getZ() + Math.min(cz + 1, SiloStructure.WIDTH - INSET);
                face.quad(ax, y1, bz, bx, y1, bz, bx, y1, az, ax, y1, az, bx - ax, bz - az, 0, 1, 0);
            }
        }
    }

    private static int light(Level level, BlockPos pos, boolean glows) {
        return glows ? LightTexture.FULL_BRIGHT : LevelRenderer.getLightColor(level, pos);
    }

    @Override
    public boolean shouldRenderOffScreen(SiloControllerBlockEntity silo) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    /** Writes textured quads; corners are given counter-clockwise as seen from outside. */
    private static final class Face {
        private final VertexConsumer consumer;
        private final PoseStack.Pose pose;
        private final TextureAtlasSprite sprite;
        private final int colour;
        int light;

        Face(VertexConsumer consumer, PoseStack.Pose pose, TextureAtlasSprite sprite, int colour) {
            this.consumer = consumer;
            this.pose = pose;
            this.sprite = sprite;
            this.colour = colour;
        }

        void quad(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz,
                  float width, float height, float nx, float ny, float nz) {
            float u0 = sprite.getU0();
            float u1 = sprite.getU0() + (sprite.getU1() - sprite.getU0()) * Math.min(1f, width);
            float v1 = sprite.getV1();
            float v0 = sprite.getV1() - (sprite.getV1() - sprite.getV0()) * Math.min(1f, height);
            vertex(ax, ay, az, u0, v1, nx, ny, nz);
            vertex(bx, by, bz, u1, v1, nx, ny, nz);
            vertex(cx, cy, cz, u1, v0, nx, ny, nz);
            vertex(dx, dy, dz, u0, v0, nx, ny, nz);
        }

        private void vertex(float x, float y, float z, float u, float v, float nx, float ny, float nz) {
            consumer.addVertex(pose, x, y, z).setColor(colour).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
        }
    }
}
