package io.github.keellam4.oritechextensions.client;

import io.github.keellam4.oritechextensions.wireless.InductionPylonBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;

/** NeoForge culls block entities by their render box: make it cover the pylon's range, so arcs stay visible. */
public class NeoPylonRenderer extends PylonRenderer {

    public NeoPylonRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public AABB getRenderBoundingBox(InductionPylonBlockEntity pylon) {
        return new AABB(pylon.getBlockPos()).inflate(pylon.range() + 2);
    }
}
