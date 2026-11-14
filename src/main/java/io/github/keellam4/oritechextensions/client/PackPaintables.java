package io.github.keellam4.oritechextensions.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.keellam4.oritechextensions.OritechExtensions;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import java.util.ArrayList;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/** Reads which textures other resource packs added to the painted-texture atlas (see PaintClient). */
final class PackPaintables {

    private static final Identifier BLOCK_ATLAS_DEFINITION = Identifier.withDefaultNamespace("atlases/blocks.json");

    private PackPaintables() {
    }

    /** Textures listed in {@code paint_*} paletted permutation sources by packs other than this mod. */
    static Set<Identifier> texturesFromOtherPacks(ResourceManager resources) {
        var textures = new HashSet<Identifier>();
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
                        var id = Identifier.tryParse(texture.getAsString());
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

    /** Whether any quad of {@code model} uses one of {@code textures}. */
    static boolean usesAny(BlockStateModel model, Set<Identifier> textures, RandomSource random) {
        try {
            var parts = new ArrayList<BlockStateModelPart>();
            model.collectParts(random, parts);
            for (var part : parts) {
                if (usesAny(part, null, textures)) return true;
                for (var direction : Direction.values()) {
                    if (usesAny(part, direction, textures)) return true;
                }
            }
        } catch (RuntimeException e) {
            return false; // some modded models need a level to build quads; those blocks just stay unpaintable
        }
        return false;
    }

    private static boolean usesAny(BlockStateModelPart part, @Nullable Direction side, Set<Identifier> textures) {
        for (var quad : part.getQuads(side)) {
            if (textures.contains(quad.materialInfo().sprite().contents().name())) return true;
        }
        return false;
    }
}
