package io.github.keellam4.oritechextensions.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import org.jetbrains.annotations.Nullable;

public final class NeoForgeClientPlatform implements ClientPlatform {

    @Override
    public TextureAtlasSprite fluidSprite(Fluid fluid) {
        var texture = IClientFluidTypeExtensions.of(fluid).getStillTexture();
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(texture);
    }

    @Override
    public int fluidColour(Fluid fluid, @Nullable BlockAndTintGetter level, @Nullable BlockPos pos) {
        var extensions = IClientFluidTypeExtensions.of(fluid);
        if (level != null && pos != null) return extensions.getTintColor(fluid.defaultFluidState(), level, pos);
        return extensions.getTintColor();
    }
}
