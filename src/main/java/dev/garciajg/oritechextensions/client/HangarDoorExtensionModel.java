package dev.garciajg.oritechextensions.client;

import dev.garciajg.oritechextensions.OritechExtensions;
import dev.garciajg.oritechextensions.hangar.HangarDoorExtensionBlock;
import dev.garciajg.oritechextensions.hangar.HangarDoorExtensionBlockEntity;
import net.minecraft.resources.ResourceLocation;
import rearth.oritech.util.ColorableMachine;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;

import java.util.Locale;

/**
 * Geometry lives in this mod (geo/block/hangar_door_extension.geo.json, a copy of the top segment of
 * Oritech's hangar door stretched to a full block). The texture is Oritech's own hangar door texture,
 * including the painted colour variants, chosen to match the door this extension belongs to.
 */
public class HangarDoorExtensionModel extends DefaultedBlockGeoModel<HangarDoorExtensionBlockEntity> {

    private static final String ORITECH = "oritech";
    private static final ResourceLocation BASE_TEXTURE = ResourceLocation.fromNamespaceAndPath(ORITECH, "textures/block/models/hangar_door.png");

    public HangarDoorExtensionModel() {
        super(OritechExtensions.id("hangar_door_extension"));
    }

    @Override
    public ResourceLocation getTextureResource(HangarDoorExtensionBlockEntity extension) {
        var level = extension.getLevel();
        if (level == null) return BASE_TEXTURE;

        var anchorPos = HangarDoorExtensionBlock.anchorPos(extension.getBlockPos(), extension.getBlockState());
        if (level.getBlockEntity(anchorPos) instanceof ColorableMachine colorable && colorable.supportRecoloring()) {
            var color = colorable.getCurrentColor();
            if (color != ColorableMachine.ColorVariant.ORANGE) {
                // same naming scheme as Oritech's MachineModel
                var suffix = color.toString().toLowerCase(Locale.ROOT);
                return ResourceLocation.fromNamespaceAndPath(ORITECH, "textures/block/models/colored/hangar_door_" + suffix + ".png");
            }
        }
        return BASE_TEXTURE;
    }
}
