package io.github.keellam4.oritechextensions.client;

import io.github.keellam4.oritechextensions.fabric.FabricPlatform;
import io.github.keellam4.oritechextensions.paint.PaintColors;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.model.SpriteFinder;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import rearth.oritech.util.ColorableMachine.ColorVariant;

import java.util.function.Supplier;

/**
 * Fabric version of PaintableModel. When the block is painted, every quad whose texture has a painted variant is
 * redrawn with that variant. It works on the quads the wrapped model emits, so connected-texture models
 * (Athena) keep their connections.
 */
final class FabricPaintableModel extends ForwardingBakedModel {

    private final PaintedSprites sprites;

    FabricPaintableModel(BakedModel original, PaintedSprites sprites) {
        this.wrapped = original;
        this.sprites = sprites;
    }

    @Override
    public boolean isVanillaAdapter() {
        return false;
    }

    @Override
    public void emitBlockQuads(BlockAndTintGetter level, BlockState state, BlockPos pos, Supplier<RandomSource> random, RenderContext context) {
        var paint = paintAt(level, pos);
        if (!PaintColors.isPainted(paint)) {
            super.emitBlockQuads(level, state, pos, random, context);
            return;
        }
        var finder = SpriteFinder.get(Minecraft.getInstance().getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS));
        context.pushTransform(quad -> {
            var source = finder.find(quad);
            if (source != null) sprites.find(source, paint).ifPresent(target -> repaint(quad, source, target));
            return true;
        });
        try {
            super.emitBlockQuads(level, state, pos, random, context);
        } finally {
            context.popTransform();
        }
    }

    /** The block entity's paint (machine addons), else the world-stored paint (pipes, glass, reactors, tagged blocks). */
    @Nullable
    private static ColorVariant paintAt(BlockAndTintGetter level, BlockPos pos) {
        var blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null) {
            var paint = ((AttachmentTarget) blockEntity).getAttached(FabricPlatform.PAINT);
            if (paint != null) return paint;
        }
        return PaintClient.worldPaint(pos);
    }

    /** Same quad, texture coordinates moved from the original sprite to the painted one. */
    private static void repaint(MutableQuadView quad, TextureAtlasSprite source, TextureAtlasSprite target) {
        // Animated textures: the source sprite covers one frame, the generated sprite holds the whole strip (unanimated).
        // Scale so the painted block shows the first frame instead of a squashed strip.
        float scaleU = (float) source.contents().width() / target.contents().width();
        float scaleV = (float) source.contents().height() / target.contents().height();
        for (int i = 0; i < 4; i++) {
            float relU = (quad.u(i) - source.getU0()) / (source.getU1() - source.getU0()) * scaleU;
            float relV = (quad.v(i) - source.getV0()) / (source.getV1() - source.getV0()) * scaleV;
            quad.uv(i, target.getU0() + relU * (target.getU1() - target.getU0()),
                    target.getV0() + relV * (target.getV1() - target.getV0()));
        }
    }
}
