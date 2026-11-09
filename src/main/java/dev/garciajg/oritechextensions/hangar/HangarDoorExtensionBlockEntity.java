package dev.garciajg.oritechextensions.hangar;

import dev.garciajg.oritechextensions.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Exists only so the extension can be drawn with GeckoLib, using Oritech's hangar door texture.
 * Holds no saved data: everything lives in the block state. The animation is driven in code by the
 * renderer, so no GeckoLib animation controllers are registered.
 */
public class HangarDoorExtensionBlockEntity extends BlockEntity implements GeoBlockEntity {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // Client-side animation bookkeeping (never saved or synced).
    public Boolean clientLastOpened = null;
    public double clientChangeTime = Double.NEGATIVE_INFINITY;

    public HangarDoorExtensionBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.HANGAR_DOOR_EXTENSION_ENTITY.get(), pos, state);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
