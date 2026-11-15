package io.github.keellam4.oritechextensions.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.keellam4.oritechextensions.wireless.InductionPylonBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the Induction Pylon's arcs: a flickering teal bolt from its tip to the chest of each player it's
 * charging. The bolt's shape changes a few times a second.
 */
public class PylonRenderer implements BlockEntityRenderer<InductionPylonBlockEntity> {

    private static final int SEGMENTS = 8;

    public PylonRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(InductionPylonBlockEntity pylon, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var level = pylon.getLevel();
        if (level == null || pylon.charging().isEmpty()) return;
        var origin = Vec3.atLowerCornerOf(pylon.getBlockPos());
        var tip = pylon.tip().subtract(origin);
        var consumer = buffers.getBuffer(RenderType.lightning());
        long flicker = level.getGameTime() / 2;
        for (int id : pylon.charging()) {
            var entity = level.getEntity(id);
            if (entity == null) continue;
            var target = entity.getPosition(partialTick).add(0, entity.getBbHeight() * 0.55, 0).subtract(origin);
            var random = RandomSource.create(flicker * 31 + id);
            bolt(consumer, pose.last(), tip, target, random, 0.06f, 0.24f, 0.79f, 0.84f, 0.55f);
            bolt(consumer, pose.last(), tip, target, RandomSource.create(flicker * 31 + id), 0.025f, 0.61f, 1f, 0.96f, 0.9f);
        }
    }

    private static void bolt(VertexConsumer consumer, PoseStack.Pose pose, Vec3 from, Vec3 to, RandomSource random,
                             float width, float r, float g, float b, float a) {
        var previous = from;
        double jitter = from.distanceTo(to) * 0.06;
        for (int i = 1; i <= SEGMENTS; i++) {
            double t = (double) i / SEGMENTS;
            var point = from.lerp(to, t);
            if (i < SEGMENTS) point = point.add((random.nextDouble() - 0.5) * jitter * 2, (random.nextDouble() - 0.5) * jitter * 2, (random.nextDouble() - 0.5) * jitter * 2);
            segment(consumer, pose, previous, point, width, r, g, b, a);
            previous = point;
        }
    }

    /** Two crossed quads along the segment, so it looks solid from any side. */
    private static void segment(VertexConsumer consumer, PoseStack.Pose pose, Vec3 a, Vec3 b, float width, float r, float g, float bl, float alpha) {
        var direction = b.subtract(a).normalize();
        var side = direction.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1e-4) side = new Vec3(1, 0, 0);
        side = side.normalize().scale(width);
        var up = direction.cross(side).normalize().scale(width);
        for (var offset : new Vec3[]{side, up}) {
            quad(consumer, pose, a.subtract(offset), a.add(offset), b.add(offset), b.subtract(offset), r, g, bl, alpha);
            quad(consumer, pose, b.subtract(offset), b.add(offset), a.add(offset), a.subtract(offset), r, g, bl, alpha);
        }
    }

    private static void quad(VertexConsumer consumer, PoseStack.Pose pose, Vec3 p1, Vec3 p2, Vec3 p3, Vec3 p4, float r, float g, float b, float a) {
        for (var p : new Vec3[]{p1, p2, p3, p4}) consumer.addVertex(pose, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, a);
    }

    @Override
    public boolean shouldRenderOffScreen(InductionPylonBlockEntity pylon) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
