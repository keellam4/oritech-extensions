package io.github.keellam4.oritechextensions.client;

import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

public final class FabricClientPlatform implements ClientPlatform {

    @Override
    public TextureAtlasSprite fluidSprite(Fluid fluid) {
        var handler = FluidRenderHandlerRegistry.INSTANCE.get(fluid);
        if (handler == null) {
            return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation());
        }
        return handler.getFluidSprites(null, null, fluid.defaultFluidState())[0];
    }

    @Override
    public int fluidColour(Fluid fluid, @Nullable BlockAndTintGetter level, @Nullable BlockPos pos) {
        var handler = FluidRenderHandlerRegistry.INSTANCE.get(fluid);
        if (handler == null) return 0xFFFFFFFF;
        int colour = handler.getFluidColor(level, pos, fluid.defaultFluidState());
        return colour == -1 ? 0xFFFFFFFF : 0xFF000000 | colour;
    }

    @Override
    public int fluidDensity(net.minecraft.world.level.material.Fluid fluid) {
        // Fabric has no density: gases float, and thicker fluids (lava 30000, water 5000) sink.
        var variant = net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant.of(fluid);
        if (net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes.isLighterThanAir(variant)) return -1000;
        return net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes.getViscosity(variant, null) / 5;
    }
}
