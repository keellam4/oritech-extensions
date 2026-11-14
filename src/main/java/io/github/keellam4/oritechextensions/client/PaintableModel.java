package io.github.keellam4.oritechextensions.client;

import io.github.keellam4.oritechextensions.paint.PaintColors;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;
import org.jetbrains.annotations.Nullable;
import rearth.oritech.util.ColorableMachine.ColorVariant;

import java.util.ArrayList;
import java.util.List;

/**
 * Wraps a paintable block's model. When the block is painted, every quad whose texture has a painted variant is
 * redrawn with that variant; everything else (e.g. textures without Oritech's orange) is left as is.
 */
final class PaintableModel extends DelegateBlockStateModel {

    private final PaintedSprites sprites;

    PaintableModel(BlockStateModel original, PaintedSprites sprites) {
        super(original);
        this.sprites = sprites;
    }

    /** Called when the chunk containing the block is (re)built. */
    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockStateModelPart> parts) {
        var paint = PaintClient.worldPaint(pos);
        if (!PaintColors.isPainted(paint)) {
            super.collectParts(level, pos, state, random, parts);
            return;
        }
        var original = new ArrayList<BlockStateModelPart>();
        super.collectParts(level, pos, state, random, original);
        for (var part : original) parts.add(new PaintedPart(part, paint, sprites));
    }

    /** One part of the original model, with its quads moved onto the painted textures. */
    private record PaintedPart(BlockStateModelPart original, ColorVariant paint, PaintedSprites sprites) implements BlockStateModelPart {

        @Override
        public List<BakedQuad> getQuads(@Nullable Direction side) {
            var quads = original.getQuads(side);
            if (quads.isEmpty()) return quads;
            var painted = new ArrayList<BakedQuad>(quads.size());
            for (var quad : quads) painted.add(sprites.repainted(quad, paint));
            return painted;
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean useAmbientOcclusion() {
            return original.useAmbientOcclusion();
        }

        @Override
        public Material.Baked particleMaterial() {
            return original.particleMaterial();
        }

        @Override
        public int materialFlags() {
            return original.materialFlags();
        }
    }

    /** Same quad, texture coordinates moved from the original sprite to the painted one. */
    static BakedQuad repaint(BakedQuad quad, TextureAtlasSprite target) {
        var info = quad.materialInfo();
        var source = info.sprite();

        // Animated textures: the source sprite covers one frame, the generated sprite holds the whole strip (unanimated).
        // Scale so the painted block shows the first frame instead of a squashed strip.
        float scaleU = (float) source.contents().width() / target.contents().width();
        float scaleV = (float) source.contents().height() / target.contents().height();

        long[] uvs = new long[4];
        for (int i = 0; i < 4; i++) {
            long packed = quad.packedUV(i);
            float relU = (unpackU(packed) - source.getU0()) / (source.getU1() - source.getU0()) * scaleU;
            float relV = (unpackV(packed) - source.getV0()) / (source.getV1() - source.getV0()) * scaleV;
            uvs[i] = pack(target.getU0() + relU * (target.getU1() - target.getU0()),
                    target.getV0() + relV * (target.getV1() - target.getV0()));
        }
        var paintedInfo = new BakedQuad.MaterialInfo(target, info.layer(), info.itemRenderType(), info.tintIndex(),
                info.shade(), info.lightEmission(), info.ambientOcclusion());
        return new BakedQuad(quad.position0(), quad.position1(), quad.position2(), quad.position3(),
                uvs[0], uvs[1], uvs[2], uvs[3], quad.direction(), paintedInfo, quad.bakedNormals(), quad.bakedColors());
    }

    // Packed UVs: u in the high 32 bits, v in the low 32 bits (both as float bits).
    private static float unpackU(long packed) {
        return Float.intBitsToFloat((int) (packed >>> 32));
    }

    private static float unpackV(long packed) {
        return Float.intBitsToFloat((int) packed);
    }

    private static long pack(float u, float v) {
        return ((long) Float.floatToRawIntBits(u) << 32) | (Float.floatToRawIntBits(v) & 0xFFFFFFFFL);
    }
}
