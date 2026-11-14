package io.github.keellam4.oritechextensions.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.keellam4.oritechextensions.hangar.HangarDoorExtensionBlock;
import io.github.keellam4.oritechextensions.hangar.HangarDoorExtensionBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import rearth.oritech.block.blocks.decorative.HangarDoorBlock;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * Draws one extension segment in the coordinate frame of the door's anchor block, so it lines up exactly
 * with Oritech's own model (which is 3 segments tall: model Y 0..48 pixels). Extension N sits at
 * model Y = 48 + 16 * (N - 1).
 * <p>
 * Opening/closing is animated in code rather than with a GeckoLib animation file, because the travel
 * distance depends on how far the segment is from the door. Timings and easings are copied from Oritech's
 * hangar_door.animation.json ("top" bone) so the extensions move together with the original door:
 * when opening, every segment slides back into the door housing; when closing, they slide back out.
 */
public class HangarDoorExtensionRenderer extends GeoBlockRenderer<HangarDoorExtensionBlockEntity> {

    // Oritech "retract": top bone moves over 0 -> 1.6667s, easeInOutSine
    private static final double OPEN_DURATION = 1.6667;
    // Oritech "deploy": top bone holds until 0.875s, then moves until 2.5833s, easeOutQuart
    private static final double CLOSE_DELAY = 0.875;
    private static final double CLOSE_DURATION = 2.5833 - CLOSE_DELAY;

    private static final float ORITECH_DOOR_HEIGHT_PX = 48f;
    private static final float SEGMENT_PX = 16f;

    private float currentPartialTick;

    public HangarDoorExtensionRenderer(BlockEntityRendererProvider.Context context) {
        super(new HangarDoorExtensionModel());
    }

    @Override
    public void render(HangarDoorExtensionBlockEntity extension, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (extension.getLevel() == null) return;

        currentPartialTick = partialTick;
        updateAnimationClock(extension, partialTick);

        // Once fully retracted the segment is tucked inside the door housing; skip drawing it.
        if (extension.clientLastOpened && secondsSinceChange(extension, partialTick) >= OPEN_DURATION) return;

        super.render(extension, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
    }

    @Override
    protected void rotateBlock(Direction facing, PoseStack poseStack) {
        var extension = this.animatable;
        var state = extension.getBlockState();
        var pos = extension.getBlockPos();
        var anchor = HangarDoorExtensionBlock.anchorPos(pos, state);

        // Move into the anchor block's frame (GeoBlockRenderer has already centred us on X/Z).
        poseStack.translate(anchor.getX() - pos.getX(), anchor.getY() - pos.getY(), anchor.getZ() - pos.getZ());

        applyOritechHangarDoorRotation(state.getValue(HangarDoorBlock.SURFACE), state.getValue(HangarDoorBlock.ROTATED), poseStack);

        int index = state.getValue(HangarDoorExtensionBlock.INDEX);
        float restY = ORITECH_DOOR_HEIGHT_PX + SEGMENT_PX * (index - 1);
        float travel = SEGMENT_PX * index + 32f; // ends inside the housing, where Oritech's top segment ends up
        float offset = -travel * retractedFraction(extension, currentPartialTick);

        poseStack.translate(0, (restY + offset) / 16f, 0);
    }

    /** Exact copy of rearth.oritech.client.renderers.HangarDoorRenderer#rotateBlock (Oritech 1.2.x). */
    private static void applyOritechHangarDoorRotation(Direction surface, boolean rotated, PoseStack poseStack) {
        if (rotated && surface.getAxis().isVertical()) {
            poseStack.translate(0, 0, -1);
            poseStack.translate(0.5, 0, 0.5);
            poseStack.mulPose(Axis.YP.rotationDegrees(90));
            poseStack.translate(-0.5, 0, -0.5);
        }

        if (surface == Direction.DOWN) {
            poseStack.translate(0, 1, 0);
            poseStack.mulPose(Axis.XP.rotationDegrees(180));
        } else if (surface == Direction.WEST) {
            poseStack.translate(0.5, 0.5, 0);
            poseStack.mulPose(Axis.XN.rotationDegrees(90));
            poseStack.mulPose(Axis.ZP.rotationDegrees(90));
        } else if (surface == Direction.EAST) {
            poseStack.translate(-0.5, 0.5, 0);
            poseStack.mulPose(Axis.XN.rotationDegrees(90));
            poseStack.mulPose(Axis.ZP.rotationDegrees(270));
        } else if (surface == Direction.SOUTH) {
            poseStack.translate(0, 0.5, -0.5);
            poseStack.mulPose(Axis.XP.rotationDegrees(90));
        } else if (surface == Direction.NORTH) {
            poseStack.translate(0, 0.5, 0.5);
            poseStack.mulPose(Axis.XN.rotationDegrees(90));
        }

        if (rotated && surface.getAxis().isHorizontal())
            poseStack.mulPose(Axis.YP.rotationDegrees(90));
    }

    // ---------------------------------------------------------------- animation clock

    private static void updateAnimationClock(HangarDoorExtensionBlockEntity extension, float partialTick) {
        boolean open = extension.getBlockState().getValue(HangarDoorBlock.OPENED);
        if (extension.clientLastOpened == null) {
            // First frame after load: show the current state without animating.
            extension.clientLastOpened = open;
            extension.clientChangeTime = Double.NEGATIVE_INFINITY;
        } else if (extension.clientLastOpened != open) {
            extension.clientLastOpened = open;
            extension.clientChangeTime = extension.getLevel().getGameTime() + partialTick;
        }
    }

    private static double secondsSinceChange(HangarDoorExtensionBlockEntity extension, float partialTick) {
        return (extension.getLevel().getGameTime() + partialTick - extension.clientChangeTime) / 20.0;
    }

    /** 0 = fully extended (closed door), 1 = fully retracted into the housing (open door). */
    private static float retractedFraction(HangarDoorExtensionBlockEntity extension, float partialTick) {
        double t = secondsSinceChange(extension, partialTick);
        if (extension.clientLastOpened) {
            return (float) easeInOutSine(Mth.clamp(t / OPEN_DURATION, 0, 1));
        }
        if (t < CLOSE_DELAY) return 1f;
        return (float) (1 - easeOutQuart(Mth.clamp((t - CLOSE_DELAY) / CLOSE_DURATION, 0, 1)));
    }

    private static double easeInOutSine(double x) {
        return -(Math.cos(Math.PI * x) - 1) / 2;
    }

    private static double easeOutQuart(double x) {
        return 1 - Math.pow(1 - x, 4);
    }

    // ---------------------------------------------------------------- culling

    /** Segments slide through other blocks while animating, so cover everything from the anchor to here. */
    // NeoForge's renderer extension (no @Override, so the shared code also compiles on Fabric).
    public AABB getRenderBoundingBox(HangarDoorExtensionBlockEntity extension) {
        var pos = extension.getBlockPos();
        var anchor = HangarDoorExtensionBlock.anchorPos(pos, extension.getBlockState());
        return new AABB(anchor).minmax(new AABB(pos)).inflate(1);
    }
}
