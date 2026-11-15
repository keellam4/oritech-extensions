package io.github.keellam4.oritechextensions.client;

import io.github.keellam4.oritechextensions.neoforge.NeoForgePlatform;
import io.github.keellam4.oritechextensions.paint.PaintColors;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.Nullable;
import io.github.keellam4.oritechextensions.paint.Paint;

import java.util.ArrayList;
import java.util.List;

/**
 * Wraps a paintable block's model. When the block is painted, every quad whose texture has a painted variant is
 * redrawn with that variant; everything else (e.g. textures without Oritech's orange) is left as is.
 */
final class PaintableModel extends BakedModelWrapper<BakedModel> {

    static final ModelProperty<Paint> PAINT = new ModelProperty<>();
    /** Painted glass whose pane is tinted: drawn with an extra see-through face in the translucent layer. */
    static final ModelProperty<Boolean> TINTED = new ModelProperty<>();

    private final PaintedSprites sprites;

    PaintableModel(BakedModel original, PaintedSprites sprites) {
        super(original);
        this.sprites = sprites;
    }

    /** Called when the chunk containing the block is (re)built: pick up the block entity's paint or the world-stored paint. */
    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {
        var data = super.getModelData(level, pos, state, modelData);
        var blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null && blockEntity.hasData(NeoForgePlatform.PAINT)) {
            return data.derive().with(PAINT, blockEntity.getData(NeoForgePlatform.PAINT)).build();
        }
        var worldPaint = PaintClient.worldPaint(pos);
        if (PaintColors.isPainted(worldPaint)) {
            return data.derive().with(PAINT, worldPaint).with(TINTED, PaintClient.isTinted(pos)).build();
        }
        return data;
    }

    @Override
    public net.neoforged.neoforge.client.ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        var types = super.getRenderTypes(state, rand, data);
        if (!Boolean.TRUE.equals(data.get(TINTED))) return types;
        return net.neoforged.neoforge.client.ChunkRenderTypeSet.union(types, net.neoforged.neoforge.client.ChunkRenderTypeSet.of(RenderType.translucent()));
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData extraData, @Nullable RenderType renderType) {
        if (renderType == RenderType.translucent() && Boolean.TRUE.equals(extraData.get(TINTED))) {
            // The tint pass: only the see-through pane, one face per uncovered side.
            var tintPaint = extraData.get(PAINT);
            if (side == null || !PaintColors.isPainted(tintPaint)) return List.of();
            return List.of(tintFace(side, PaintClient.tintSprite(tintPaint)));
        }
        var quads = super.getQuads(state, side, rand, extraData, renderType);
        var paint = extraData.get(PAINT);
        if (!PaintColors.isPainted(paint) || quads.isEmpty()) return quads;

        var painted = new ArrayList<BakedQuad>(quads.size());
        for (var quad : quads) {
            var sprite = sprites.find(quad.getSprite(), paint);
            painted.add(sprite.isPresent() ? repaint(quad, sprite.get()) : quad);
        }
        return painted;
    }

    /** Same quad, texture coordinates moved from the original sprite to the painted one. */
    private static BakedQuad repaint(BakedQuad quad, TextureAtlasSprite target) {
        var source = quad.getSprite();
        int[] vertices = quad.getVertices().clone();
        int stride = vertices.length / 4;
        final int uvOffset = 4; // DefaultVertexFormat.BLOCK: position(3), color(1), uv0(2), uv2(1), normal(1)

        // Animated textures: the source sprite covers one frame, the generated sprite holds the whole strip (unanimated).
        // Scale so the painted block shows the first frame instead of a squashed strip.
        float scaleU = (float) source.contents().width() / target.contents().width();
        float scaleV = (float) source.contents().height() / target.contents().height();

        for (int i = 0; i < 4; i++) {
            int at = i * stride + uvOffset;
            float u = Float.intBitsToFloat(vertices[at]);
            float v = Float.intBitsToFloat(vertices[at + 1]);
            float relU = (u - source.getU0()) / (source.getU1() - source.getU0()) * scaleU;
            float relV = (v - source.getV0()) / (source.getV1() - source.getV0()) * scaleV;
            vertices[at] = Float.floatToRawIntBits(target.getU0() + relU * (target.getU1() - target.getU0()));
            vertices[at + 1] = Float.floatToRawIntBits(target.getV0() + relV * (target.getV1() - target.getV0()));
        }
        return new BakedQuad(vertices, quad.getTintIndex(), quad.getDirection(), target, quad.isShade(), quad.hasAmbientOcclusion());
    }

    /** A full face of the block, a hair inside it so the frame stays on top, showing {@code sprite}. */
    private static BakedQuad tintFace(Direction side, TextureAtlasSprite sprite) {
        final float e = 0.002f;
        float[][] corners = switch (side) {
            case DOWN -> new float[][]{{0, e, 1}, {0, e, 0}, {1, e, 0}, {1, e, 1}};
            case UP -> new float[][]{{0, 1 - e, 0}, {0, 1 - e, 1}, {1, 1 - e, 1}, {1, 1 - e, 0}};
            case NORTH -> new float[][]{{1, 1, e}, {1, 0, e}, {0, 0, e}, {0, 1, e}};
            case SOUTH -> new float[][]{{0, 1, 1 - e}, {0, 0, 1 - e}, {1, 0, 1 - e}, {1, 1, 1 - e}};
            case WEST -> new float[][]{{e, 1, 0}, {e, 0, 0}, {e, 0, 1}, {e, 1, 1}};
            case EAST -> new float[][]{{1 - e, 1, 1}, {1 - e, 0, 1}, {1 - e, 0, 0}, {1 - e, 1, 0}};
        };
        float[][] uvs = {{sprite.getU0(), sprite.getV0()}, {sprite.getU0(), sprite.getV1()}, {sprite.getU1(), sprite.getV1()}, {sprite.getU1(), sprite.getV0()}};
        var normal = side.getNormal();
        int packedNormal = (normal.getX() * 127 & 255) | (normal.getY() * 127 & 255) << 8 | (normal.getZ() * 127 & 255) << 16;
        int[] vertices = new int[32]; // DefaultVertexFormat.BLOCK: position(3), color(1), uv0(2), uv2(1), normal(1)
        for (int i = 0; i < 4; i++) {
            int at = i * 8;
            vertices[at] = Float.floatToRawIntBits(corners[i][0]);
            vertices[at + 1] = Float.floatToRawIntBits(corners[i][1]);
            vertices[at + 2] = Float.floatToRawIntBits(corners[i][2]);
            vertices[at + 3] = -1;
            vertices[at + 4] = Float.floatToRawIntBits(uvs[i][0]);
            vertices[at + 5] = Float.floatToRawIntBits(uvs[i][1]);
            vertices[at + 6] = 0;
            vertices[at + 7] = packedNormal;
        }
        return new BakedQuad(vertices, -1, side, sprite, true, false);
    }
}
