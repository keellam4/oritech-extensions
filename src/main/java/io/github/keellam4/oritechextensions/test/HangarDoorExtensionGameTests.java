package io.github.keellam4.oritechextensions.test;

import io.github.keellam4.oritechextensions.Config;
import io.github.keellam4.oritechextensions.ModContent;
import io.github.keellam4.oritechextensions.OritechExtensions;
import io.github.keellam4.oritechextensions.hangar.HangarDoorExtensionBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;
import rearth.oritech.init.BlockContent;

import static rearth.oritech.block.blocks.decorative.HangarDoorBlock.OPENED;
import static rearth.oritech.block.blocks.decorative.HangarDoorBlock.ROTATED;
import static rearth.oritech.block.blocks.decorative.HangarDoorBlock.SURFACE;

/**
 * Server-side checks against the real Oritech hangar door. Run with {@code ./gradlew runGameTestServer}
 * (CI does this on every push). Only registered when game tests are enabled, so they never run in normal play.
 */
@GameTestHolder(OritechExtensions.MODID)
@PrefixGameTestTemplate(false)
public class HangarDoorExtensionGameTests {

    private static final String TEMPLATE = "empty_tall"; // 5 x 20 x 5 of air

    /** Floor-mounted door growing upwards to the configured maximum, then open, close and break it. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void floorDoorFullLength(GameTestHelper helper) {
        int max = Config.maxExtensions();
        var anchor = helper.absolutePos(new BlockPos(2, 1, 2));
        runFullCycle(helper, anchor, Direction.UP, false, max);
    }

    /** Ceiling-mounted door growing downwards, rotated 90 degrees, with a few extensions. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void ceilingDoorRotated(GameTestHelper helper) {
        var anchor = helper.absolutePos(new BlockPos(2, 18, 2));
        runFullCycle(helper, anchor, Direction.DOWN, true, 4);
    }

    /** Wall-mounted door (sticks out sideways). */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void wallDoor(GameTestHelper helper) {
        var anchor = helper.absolutePos(new BlockPos(0, 3, 2));
        runFullCycle(helper, anchor, Direction.EAST, true, 1);
    }

    /** An extension needs a door tip; it must not attach to the side of a door or float in the air. */
    @GameTest(template = TEMPLATE)
    public static void rejectsInvalidPlacement(GameTestHelper helper) {
        var level = helper.getLevel();
        var anchor = helper.absolutePos(new BlockPos(2, 1, 2));
        placeDoor(level, anchor, Direction.UP, false);

        var tip = anchor.above(2);
        if (tryPlace(level, tip, Direction.EAST) != null) helper.fail("extension attached to the side of the door tip");
        if (tryPlace(level, anchor, Direction.EAST) != null) helper.fail("extension attached to the side of the anchor");
        if (tryPlace(level, helper.absolutePos(new BlockPos(0, 10, 0)), Direction.UP) != null) helper.fail("extension placed in mid air");
        if (tryPlace(level, tip, Direction.UP) == null) helper.fail("valid placement on the tip was rejected");
        helper.succeed();
    }

    // ---------------------------------------------------------------- helpers

    private static void runFullCycle(GameTestHelper helper, BlockPos anchor, Direction surface, boolean rotated, int extensions) {
        var level = helper.getLevel();
        placeDoor(level, anchor, surface, rotated);

        // Build the chain one extension at a time, clicking the end face of the current tip like a player would.
        for (int i = 1; i <= extensions; i++) {
            var tip = anchor.relative(surface, 2 + i - 1);
            var state = tryPlace(level, tip, surface);
            if (state == null) {
                helper.fail("extension " + i + " was rejected");
                return;
            }
            if (state.getValue(HangarDoorExtensionBlock.INDEX) != i) helper.fail("extension " + i + " got index " + state.getValue(HangarDoorExtensionBlock.INDEX));
            if (state.getValue(SURFACE) != surface || state.getValue(ROTATED) != rotated) helper.fail("extension " + i + " did not copy the door orientation");
            level.setBlock(tip.relative(surface), state, 3);
        }

        if (extensions == Config.maxExtensions() && tryPlace(level, anchor.relative(surface, 2 + extensions), surface) != null) {
            helper.fail("allowed an extension past the configured maximum of " + Config.maxTotalLength());
        }

        // Power the anchor with a redstone block placed beside it.
        var side = surface.getAxis() == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
        var powerPos = anchor.relative(side);
        level.setBlock(powerPos, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);

        helper.runAfterDelay(5, () -> {
            assertAllOpen(helper, anchor, surface, extensions, true);
            level.setBlock(powerPos, Blocks.AIR.defaultBlockState(), 3);

            helper.runAfterDelay(5, () -> {
                assertAllOpen(helper, anchor, surface, extensions, false);

                // Breaking Oritech's anchor must take every extension with it, dropping them as items.
                level.destroyBlock(anchor, true);

                helper.runAfterDelay(3, () -> {
                    for (int i = 1; i <= extensions; i++) {
                        var pos = anchor.relative(surface, 2 + i);
                        if (!level.getBlockState(pos).isAir()) helper.fail("extension " + i + " survived its door being broken");
                    }
                    int dropped = countDrops(level, anchor, surface, extensions);
                    if (dropped != extensions) helper.fail("expected " + extensions + " extension drops, found " + dropped);
                    helper.succeed();
                });
            });
        });
    }

    private static void placeDoor(ServerLevel level, BlockPos anchor, Direction surface, boolean rotated) {
        var state = BlockContent.HANGAR_DOOR.defaultBlockState().setValue(SURFACE, surface).setValue(ROTATED, rotated);
        level.setBlock(anchor, state, 3);
        // Oritech creates its 2 helper blocks here
        BlockContent.HANGAR_DOOR.setPlacedBy(level, anchor, state, null, ItemStack.EMPTY);
    }

    /** Simulates a player using the extension item on {@code face} of the block at {@code clicked}. */
    @Nullable
    private static BlockState tryPlace(ServerLevel level, BlockPos clicked, Direction face) {
        var hit = new BlockHitResult(Vec3.atCenterOf(clicked).relative(face, 0.5), face, clicked, false);
        var ctx = new BlockPlaceContext(level, null, InteractionHand.MAIN_HAND, new ItemStack(ModContent.HANGAR_DOOR_EXTENSION_ITEM.get()), hit);
        return ModContent.HANGAR_DOOR_EXTENSION.get().getStateForPlacement(ctx);
    }

    private static void assertAllOpen(GameTestHelper helper, BlockPos anchor, Direction surface, int extensions, boolean expected) {
        var level = helper.getLevel();
        if (level.getBlockState(anchor).getValue(OPENED) != expected) helper.fail("Oritech door did not " + (expected ? "open" : "close"));
        for (int i = 1; i <= extensions; i++) {
            var state = level.getBlockState(anchor.relative(surface, 2 + i));
            if (!state.is(ModContent.HANGAR_DOOR_EXTENSION.get())) {
                helper.fail("extension " + i + " is missing");
                return;
            }
            if (state.getValue(OPENED) != expected) helper.fail("extension " + i + " did not " + (expected ? "open" : "close") + " with the door");
        }
    }

    private static int countDrops(ServerLevel level, BlockPos anchor, Direction surface, int extensions) {
        var box = new AABB(anchor).minmax(new AABB(anchor.relative(surface, 2 + extensions))).inflate(3);
        return level.getEntitiesOfClass(ItemEntity.class, box).stream()
                .map(ItemEntity::getItem)
                .filter(stack -> stack.is(ModContent.HANGAR_DOOR_EXTENSION_ITEM.get()))
                .mapToInt(ItemStack::getCount)
                .sum();
    }
}
