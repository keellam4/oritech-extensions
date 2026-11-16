package io.github.keellam4.oritechextensions.client;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

/** NeoForge culls block entities by their render box: widen it so the fluid in pipes far from the pipe end stays visible. */
public class NeoFluidFlowRenderer<T extends BlockEntity> extends FluidFlowRenderer<T> {

    public NeoFluidFlowRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public AABB getRenderBoundingBox(T entity) {
        return new AABB(entity.getBlockPos()).inflate(64);
    }
}
