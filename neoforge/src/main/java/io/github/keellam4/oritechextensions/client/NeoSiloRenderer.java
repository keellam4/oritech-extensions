package io.github.keellam4.oritechextensions.client;

import io.github.keellam4.oritechextensions.storage.SiloControllerBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;

/** NeoForge culls block entities by their render box: make it the whole silo, so its fluid stays visible. */
public class NeoSiloRenderer extends SiloRenderer {

    public NeoSiloRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public AABB getRenderBoundingBox(SiloControllerBlockEntity silo) {
        var structure = silo.structure();
        return structure == null ? new AABB(silo.getBlockPos()) : structure.bounds();
    }
}
