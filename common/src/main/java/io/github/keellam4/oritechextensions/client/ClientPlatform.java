package io.github.keellam4.oritechextensions.client;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

import java.util.ServiceLoader;

/** Client-only things each loader does its own way (fluid textures and colours). */
public interface ClientPlatform {

    ClientPlatform INSTANCE = ServiceLoader.load(ClientPlatform.class, ClientPlatform.class.getClassLoader()).findFirst()
            .orElseThrow(() -> new IllegalStateException("No Oritech Extensions client platform implementation found"));

    /** The still texture of {@code fluid}, from the block atlas. */
    TextureAtlasSprite fluidSprite(Fluid fluid);

    /** The fluid's tint (ARGB), for water's biome colour and the like. */
    int fluidColour(Fluid fluid, @Nullable BlockAndTintGetter level, @Nullable BlockPos pos);

    /** How heavy a fluid is, for stacking silo layers (water about 1000, lava higher, gases below 0). */
    int fluidDensity(Fluid fluid);
}
