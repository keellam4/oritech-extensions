package io.github.keellam4.oritechextensions.client;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.GeoBlockRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.keellam4.oritechextensions.hangar.HangarDoorExtensionBlock;
import io.github.keellam4.oritechextensions.hangar.HangarDoorExtensionBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import rearth.oritech.block.blocks.decorative.HangarDoorBlock;
import rearth.oritech.util.AnimatedBlockRotation;
import rearth.oritech.util.ColorableMachine;

/**
 * Draws one extension segment in the coordinate frame of the door's anchor block, so it lines up exactly
 * with Oritech's own model (which is 3 segments tall: model Y 0..48 pixels). Extension N sits at
 * model Y = 48 + 16 * (N - 1).
 * <p>
 * Opening/closing is animated in code rather than with a GeckoLib animation file, because the travel
 * distance depends on how far the segment is from the door. Timings and easings are copied from Oritech's
 * hangar_door.animation.json ("top" bone) so the extensions move together with the original door.
 * <p>
 * GeckoLib 5 renders from a snapshot ("render state"): everything the drawing needs is worked out in
 * {@link #addRenderData} and read back from the snapshot when drawing.
 */
public class HangarDoorExtensionRenderer<R extends BlockEntityRenderState & GeoRenderState>
        extends GeoBlockRenderer<HangarDoorExtensionBlockEntity, R> {

    // Oritech "retract": top bone moves over 0 -> 1.6667s, easeInOutSine
    private static final double OPEN_DURATION = 1.6667;
    // Oritech "deploy": top bone holds until 0.875s, then moves until 2.5833s, easeOutQuart
    private static final double CLOSE_DELAY = 0.875;
    private static final double CLOSE_DURATION = 2.5833 - CLOSE_DELAY;

    private static final float ORITECH_DOOR_HEIGHT_PX = 48f;
    private static final float SEGMENT_PX = 16f;

    static final DataTicket<ColorableMachine.ColorVariant> DOOR_COLOR = DataTicket.create("oritech_extensions_door_color", ColorableMachine.ColorVariant.class);
    private static final DataTicket<BlockPos> ANCHOR_OFFSET = DataTicket.create("oritech_extensions_anchor_offset", BlockPos.class);
    private static final DataTicket<Direction> SURFACE = DataTicket.create("oritech_extensions_surface", Direction.class);
    private static final DataTicket<Boolean> ROTATED = DataTicket.create("oritech_extensions_rotated", Boolean.class);
    private static final DataTicket<Float> OFFSET_PX = DataTicket.create("oritech_extensions_offset_px", Float.class);
    private static final DataTicket<Boolean> HIDDEN = DataTicket.create("oritech_extensions_hidden", Boolean.class);

    public HangarDoorExtensionRenderer(BlockEntityRendererProvider.Context context) {
        super(context, new HangarDoorExtensionModel());
    }

    @Override
    public void addRenderData(HangarDoorExtensionBlockEntity extension, @Nullable Void relatedObject, R renderState, float partialTick) {
        super.addRenderData(extension, relatedObject, renderState, partialTick);
        var level = extension.getLevel();
        if (level == null) return;

        var state = extension.getBlockState();
        var pos = extension.getBlockPos();
        var anchor = HangarDoorExtensionBlock.anchorPos(pos, state);

        updateAnimationClock(extension, partialTick);
        float retracted = retractedFraction(extension, partialTick);

        int index = state.getValue(HangarDoorExtensionBlock.INDEX);
        float restY = ORITECH_DOOR_HEIGHT_PX + SEGMENT_PX * (index - 1);
        float travel = SEGMENT_PX * index + 32f; // ends inside the housing, where Oritech's top segment ends up

        renderState.addGeckolibData(ANCHOR_OFFSET, anchor.subtract(pos));
        renderState.addGeckolibData(SURFACE, state.getValue(HangarDoorBlock.SURFACE));
        renderState.addGeckolibData(ROTATED, state.getValue(HangarDoorBlock.ROTATED));
        renderState.addGeckolibData(OFFSET_PX, restY - travel * retracted);
        // Once fully retracted the segment is tucked inside the door housing; skip drawing it.
        renderState.addGeckolibData(HIDDEN, extension.clientLastOpened && secondsSinceChange(extension, partialTick) >= OPEN_DURATION);

        if (level.getBlockEntity(anchor) instanceof ColorableMachine door && door.supportRecoloring()) {
            renderState.addGeckolibData(DOOR_COLOR, door.getCurrentColor());
        }
    }

    @Override
    public void submit(R renderState, PoseStack poseStack, SubmitNodeCollector renderTasks, CameraRenderState cameraRenderState) {
        if (Boolean.TRUE.equals(renderState.getGeckolibData(HIDDEN))) return;
        super.submit(renderState, poseStack, renderTasks, cameraRenderState);
    }

    @Override
    protected void tryRotateByBlockstate(RenderPassInfo<R> renderPassInfo, PoseStack poseStack) {
        var anchorOffset = renderPassInfo.getOrDefaultGeckolibData(ANCHOR_OFFSET, BlockPos.ZERO);
        var surface = renderPassInfo.getOrDefaultGeckolibData(SURFACE, Direction.UP);
        boolean rotated = renderPassInfo.getOrDefaultGeckolibData(ROTATED, false);
        float offsetPx = renderPassInfo.getOrDefaultGeckolibData(OFFSET_PX, ORITECH_DOOR_HEIGHT_PX);

        // Move into the anchor block's frame (GeoBlockRenderer has already centred us on X/Z),
        // then turn exactly like Oritech's own hangar door model.
        poseStack.translate(anchorOffset.getX(), anchorOffset.getY(), anchorOffset.getZ());
        poseStack.mulPose(AnimatedBlockRotation.getModelRotation(surface, true, rotated));
        poseStack.translate(0, offsetPx / 16f, 0);
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
    @Override
    public AABB getRenderBoundingBox(HangarDoorExtensionBlockEntity extension) {
        var pos = extension.getBlockPos();
        var anchor = HangarDoorExtensionBlock.anchorPos(pos, extension.getBlockState());
        return new AABB(anchor).minmax(new AABB(pos)).inflate(1);
    }
}
