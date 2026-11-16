package io.github.keellam4.oritechextensions.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.keellam4.oritechextensions.pipes.FluidFlowPayload;
import io.github.keellam4.oritechextensions.pipes.TransparentFluidPipeBlock;
import io.github.keellam4.oritechextensions.pipes.TransparentFluidPipeConnectionBlock;
import net.minecraft.client.Minecraft;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import rearth.oritech.block.blocks.pipes.GenericPipeBlock;
import rearth.oritech.block.blocks.pipes.fluid.FluidPipeBlock;
import rearth.oritech.block.blocks.pipes.fluid.FluidPipeConnectionBlock;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Draws the fluid flowing through Transparent Fluid Pipes (JNI-98). Registered on Oritech's fluid pipe end
 * (its block entity): while the server says that end is moving a fluid ({@link FluidFlowPayload}), every
 * Transparent Fluid Pipe connected to it shows that fluid; it empties {@link #LINGER_TICKS} after the flow stops.
 * Like Oritech's own item pipes showing the items they carry.
 */
public class FluidFlowRenderer<T extends BlockEntity> implements BlockEntityRenderer<T> {

    /** How long after the last report the fluid still shows. */
    static final int LINGER_TICKS = 40;
    /** How often (ticks) a pipe end's connected pipes are looked up again. */
    private static final int RESCAN_TICKS = 40;
    /** The most pipe blocks followed from one end. */
    private static final int MAX_PIPES = 2048;
    /** The fluid's cross-section inside the pipe, in pixels from the block edge (the pipe itself spans 5-11). */
    private static final float INNER = 5.75f / 16f, OUTER = 10.25f / 16f;

    private record Flow(Fluid fluid, long seen) {
    }

    private record Pipes(List<BlockPos> transparent, long scannedAt) {
    }

    private static final Map<Long, Flow> FLOWS = new HashMap<>();
    private static final Map<Long, Pipes> PIPES = new HashMap<>();
    /** Pipes already drawn this frame, so two ends of one network don't draw them twice. */
    private static final Set<Long> DRAWN = new HashSet<>();
    private static long drawnFrame = Long.MIN_VALUE;

    private static final Direction[] DIRECTIONS = Direction.values();
    private static final IntegerProperty[] SIDES = {GenericPipeBlock.DOWN, GenericPipeBlock.UP, GenericPipeBlock.NORTH,
            GenericPipeBlock.SOUTH, GenericPipeBlock.WEST, GenericPipeBlock.EAST}; // Direction order

    public FluidFlowRenderer(BlockEntityRendererProvider.Context context) {
    }

    /** Client: a pipe end is moving a fluid. */
    public static void receive(FluidFlowPayload payload) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        var fluid = BuiltInRegistries.FLUID.get(payload.fluid());
        if (fluid == Fluids.EMPTY) return;
        FLOWS.put(payload.pos().asLong(), new Flow(fluid, level.getGameTime()));
        if (FLOWS.size() > 4096) FLOWS.entrySet().removeIf(entry -> level.getGameTime() - entry.getValue().seen > LINGER_TICKS);
    }

    @Override
    public void render(T entity, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        var level = entity.getLevel();
        if (level == null) return;
        long now = level.getGameTime();
        var origin = entity.getBlockPos();
        var flow = FLOWS.get(origin.asLong());
        if (flow == null || now - flow.seen > LINGER_TICKS || now < flow.seen) return;

        long frame = now * 1000 + (int) (partialTick * 999);
        if (frame != drawnFrame) {
            DRAWN.clear();
            drawnFrame = frame;
        }
        var pipes = PIPES.get(origin.asLong());
        if (pipes == null || now - pipes.scannedAt >= RESCAN_TICKS || now < pipes.scannedAt) {
            pipes = new Pipes(scan(level, origin), now);
            PIPES.put(origin.asLong(), pipes);
        }
        if (pipes.transparent.isEmpty()) return;

        var sprite = ClientPlatform.INSTANCE.fluidSprite(flow.fluid);
        int colour = ClientPlatform.INSTANCE.fluidColour(flow.fluid, level, origin);
        boolean glows = flow.fluid.defaultFluidState().createLegacyBlock().getLightEmission() > 0;
        var consumer = buffers.getBuffer(RenderType.entityTranslucentCull(InventoryMenu.BLOCK_ATLAS));
        for (var pipe : pipes.transparent) {
            if (!DRAWN.add(pipe.asLong())) continue;
            var state = level.getBlockState(pipe);
            if (!isTransparent(state)) continue;
            int light = glows ? LightTexture.FULL_BRIGHT : LevelRenderer.getLightColor(level, pipe);
            pose.pushPose();
            pose.translate(pipe.getX() - origin.getX(), pipe.getY() - origin.getY(), pipe.getZ() - origin.getZ());
            drawPipe(pose, consumer, sprite, colour, light, state);
            pose.popPose();
        }
    }

    /** The Transparent Fluid Pipes connected to a pipe end, following Oritech's pipe connections. */
    static List<BlockPos> scan(Level level, BlockPos start) {
        var found = new ArrayList<BlockPos>();
        var seen = new HashSet<BlockPos>();
        var queue = new ArrayDeque<BlockPos>();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty() && seen.size() < MAX_PIPES) {
            var pos = queue.poll();
            var state = level.getBlockState(pos);
            if (!isFluidPipe(state)) continue;
            if (isTransparent(state)) found.add(pos);
            for (int i = 0; i < DIRECTIONS.length; i++) {
                if (!state.hasProperty(SIDES[i]) || state.getValue(SIDES[i]) == GenericPipeBlock.NO_CONNECTION) continue;
                var next = pos.relative(DIRECTIONS[i]);
                if (seen.add(next) && isFluidPipe(level.getBlockState(next))) queue.add(next);
            }
        }
        return found;
    }

    private static boolean isFluidPipe(BlockState state) {
        return state.getBlock() instanceof FluidPipeBlock || state.getBlock() instanceof FluidPipeConnectionBlock;
    }

    private static boolean isTransparent(BlockState state) {
        return state.getBlock() instanceof TransparentFluidPipeBlock || state.getBlock() instanceof TransparentFluidPipeConnectionBlock;
    }

    /** The fluid core, plus an arm towards every side the pipe connects on. */
    private static void drawPipe(PoseStack pose, VertexConsumer consumer, TextureAtlasSprite sprite, int colour, int light, BlockState state) {
        box(pose, consumer, sprite, colour, light, INNER, INNER, INNER, OUTER, OUTER, OUTER);
        for (int i = 0; i < DIRECTIONS.length; i++) {
            if (!state.hasProperty(SIDES[i]) || state.getValue(SIDES[i]) == GenericPipeBlock.NO_CONNECTION) continue;
            switch (DIRECTIONS[i]) {
                case DOWN -> box(pose, consumer, sprite, colour, light, INNER, 0, INNER, OUTER, INNER, OUTER);
                case UP -> box(pose, consumer, sprite, colour, light, INNER, OUTER, INNER, OUTER, 1, OUTER);
                case NORTH -> box(pose, consumer, sprite, colour, light, INNER, INNER, 0, OUTER, OUTER, INNER);
                case SOUTH -> box(pose, consumer, sprite, colour, light, INNER, INNER, OUTER, OUTER, OUTER, 1);
                case WEST -> box(pose, consumer, sprite, colour, light, 0, INNER, INNER, INNER, OUTER, OUTER);
                case EAST -> box(pose, consumer, sprite, colour, light, OUTER, INNER, INNER, 1, OUTER, OUTER);
            }
        }
    }

    private static void box(PoseStack pose, VertexConsumer consumer, TextureAtlasSprite sprite, int colour, int light,
                            float x0, float y0, float z0, float x1, float y1, float z1) {
        var last = pose.last();
        // Six faces, counter-clockwise seen from outside.
        quad(consumer, last, sprite, colour, light, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0);
        quad(consumer, last, sprite, colour, light, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0, 1, 0);
        quad(consumer, last, sprite, colour, light, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0, 0, -1);
        quad(consumer, last, sprite, colour, light, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, 0, 1);
        quad(consumer, last, sprite, colour, light, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1, 0, 0);
        quad(consumer, last, sprite, colour, light, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 1, 0, 0);
    }

    private static void quad(VertexConsumer consumer, PoseStack.Pose pose, TextureAtlasSprite sprite, int colour, int light,
                             float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz,
                             float dx, float dy, float dz, float nx, float ny, float nz) {
        float u0 = sprite.getU(0.3f), u1 = sprite.getU(0.7f), v0 = sprite.getV(0.3f), v1 = sprite.getV(0.7f);
        consumer.addVertex(pose, ax, ay, az).setColor(colour).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
        consumer.addVertex(pose, bx, by, bz).setColor(colour).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
        consumer.addVertex(pose, cx, cy, cz).setColor(colour).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
        consumer.addVertex(pose, dx, dy, dz).setColor(colour).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
    }

    @Override
    public boolean shouldRenderOffScreen(T entity) {
        return true; // the pipes it draws reach well beyond the pipe end
    }

    @Override
    public int getViewDistance() {
        return 64;
    }
}
