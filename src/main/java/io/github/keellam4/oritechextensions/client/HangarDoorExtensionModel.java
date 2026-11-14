package io.github.keellam4.oritechextensions.client;

import com.geckolib.model.DefaultedBlockGeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import io.github.keellam4.oritechextensions.OritechExtensions;
import io.github.keellam4.oritechextensions.hangar.HangarDoorExtensionBlockEntity;
import net.minecraft.resources.Identifier;
import rearth.oritech.util.ColorableMachine;

/**
 * Geometry lives in this mod (geckolib/models/block/hangar_door_extension.geo.json, a copy of the top segment
 * of Oritech's hangar door stretched to a full block). The texture is Oritech's own hangar door texture,
 * including the painted colour variants, chosen to match the door this extension belongs to.
 */
public class HangarDoorExtensionModel extends DefaultedBlockGeoModel<HangarDoorExtensionBlockEntity> {

    private static final Identifier BASE_TEXTURE = Identifier.fromNamespaceAndPath("oritech", "textures/models/hangar_door.png");

    public HangarDoorExtensionModel() {
        super(OritechExtensions.id("hangar_door_extension"));
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        var color = renderState.getOrDefaultGeckolibData(HangarDoorExtensionRenderer.DOOR_COLOR, ColorableMachine.ColorVariant.ORANGE);
        return ColorableMachine.getTextureForColor(BASE_TEXTURE, color); // Oritech's own naming for painted textures
    }
}
