package io.github.keellam4.oritechextensions.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.keellam4.oritechextensions.OritechExtensions;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/** Reads which textures other resource packs added to the painted-texture atlas (see PaintClient). */
final class PackPaintables {

    private static final ResourceLocation BLOCK_ATLAS_DEFINITION = ResourceLocation.withDefaultNamespace("atlases/blocks.json");

    private PackPaintables() {
    }

    /** Textures listed in {@code paint_*} paletted permutation sources by packs other than this mod. */
    static Set<ResourceLocation> texturesFromOtherPacks(ResourceManager resources) {
        var textures = new HashSet<ResourceLocation>();
        for (var resource : resources.getResourceStack(BLOCK_ATLAS_DEFINITION)) {
            if (resource.sourcePackId().contains(OritechExtensions.MODID)) continue;
            try (var reader = resource.openAsReader()) {
                var root = JsonParser.parseReader(reader).getAsJsonObject();
                if (!root.has("sources")) continue;
                for (var element : root.getAsJsonArray("sources")) {
                    if (!element.isJsonObject()) continue;
                    var source = element.getAsJsonObject();
                    if (!isPaintSource(source)) continue;
                    for (var texture : source.getAsJsonArray("textures")) {
                        var id = ResourceLocation.tryParse(texture.getAsString());
                        if (id != null) textures.add(id);
                    }
                }
            } catch (Exception e) {
                OritechExtensions.LOGGER.warn("[paint] Couldn't read {} from {}: {}", BLOCK_ATLAS_DEFINITION, resource.sourcePackId(), e.toString());
            }
        }
        return textures;
    }

    private static boolean isPaintSource(JsonObject source) {
        var type = source.has("type") ? source.get("type").getAsString() : "";
        if (!type.equals("paletted_permutations") && !type.equals("minecraft:paletted_permutations")) return false;
        if (!source.has("textures") || !source.has("permutations")) return false;
        return source.getAsJsonObject("permutations").keySet().stream().anyMatch(key -> key.startsWith("paint_"));
    }

    /** Whether any quad of {@code model} (for {@code state}) uses one of {@code textures}. */
    static boolean usesAny(BakedModel model, BlockState state, Set<ResourceLocation> textures, RandomSource random) {
        if (usesAny(model, state, null, textures, random)) return true;
        for (var direction : Direction.values()) {
            if (usesAny(model, state, direction, textures, random)) return true;
        }
        return false;
    }

    private static boolean usesAny(BakedModel model, BlockState state, @Nullable Direction side, Set<ResourceLocation> textures, RandomSource random) {
        try {
            for (var quad : model.getQuads(state, side, random)) {
                if (textures.contains(quad.getSprite().contents().name())) return true;
            }
        } catch (RuntimeException e) {
            return false; // some modded models need a level to build quads; those blocks just stay unpaintable
        }
        return false;
    }
}
