package io.github.keellam4.oritechextensions.client;

import io.github.keellam4.oritechextensions.ModContent;
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
import rearth.oritech.util.ColorableMachine.ColorVariant;

import java.util.ArrayList;
import java.util.List;

/**
 * Wraps an addon block's model. When the block is painted, every quad whose texture has a painted variant is
 * redrawn with that variant; everything else (e.g. textures without Oritech's orange) is left as is.
 */
final class PaintableModel extends BakedModelWrapper<BakedModel> {

    static final ModelProperty<ColorVariant> PAINT = new ModelProperty<>();

    private final PaintedSprites sprites;

    PaintableModel(BakedModel original, PaintedSprites sprites) {
        super(original);
        this.sprites = sprites;
    }

    /** Called when the chunk containing the block is (re)built: pick up addon BE paint or pipe-map paint. */
    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {
        var data = super.getModelData(level, pos, state, modelData);
        var blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null && blockEntity.hasData(ModContent.PAINT)) {
            return data.derive().with(PAINT, blockEntity.getData(ModContent.PAINT)).build();
        }
        var pipePaint = PipePaintClient.get(pos);
        if (PaintColors.isPainted(pipePaint)) {
            return data.derive().with(PAINT, pipePaint).build();
        }
        return data;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData extraData, @Nullable RenderType renderType) {
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
}
