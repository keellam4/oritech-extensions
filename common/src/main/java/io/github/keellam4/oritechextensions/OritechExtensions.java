package io.github.keellam4.oritechextensions;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

/**
 * Shared constants for every loader. The loader entry points (NeoForge's {@code @Mod} class, Fabric's
 * initializer) live in their own modules and call into the shared code from there.
 */
public final class OritechExtensions {

    public static final String MODID = "oritech_extensions";
    public static final Logger LOGGER = LogUtils.getLogger();

    private OritechExtensions() {
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
