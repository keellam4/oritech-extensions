package io.github.keellam4.oritechextensions.test;

import io.github.keellam4.oritechextensions.Config;
import io.github.keellam4.oritechextensions.ModContent;
import io.github.keellam4.oritechextensions.OritechExtensions;
import io.github.keellam4.oritechextensions.paint.PaintTargets;
import io.github.keellam4.oritechextensions.paint.PipePaintData;
import io.github.keellam4.oritechextensions.paint.targets.WorldPaintTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import rearth.oritech.init.BlockContent;
import rearth.oritech.init.ItemContent;
import rearth.oritech.util.ColorableMachine;
import rearth.oritech.util.ColorableMachine.ColorVariant;

import static rearth.oritech.block.blocks.decorative.HangarDoorBlock.ROTATED;
import static rearth.oritech.block.blocks.decorative.HangarDoorBlock.SURFACE;

/** Painting the blocks that go through the shared paint targets: Industrial Glass, reactors, accelerators, hangar doors. */
public class PaintTargetGameTests {

    /** Touching glass is painted together; glass that doesn't touch is left alone. */
    @ModGameTest(timeoutTicks = 100)
    public static void paintIndustrialGlass(GameTestHelper helper) {
        var level = helper.getLevel();
        var glass = BlockContent.INDUSTRIAL_GLASS.get().defaultBlockState();
        var a = helper.absolutePos(new BlockPos(0, 1, 2));
        var b = helper.absolutePos(new BlockPos(1, 1, 2));
        var c = helper.absolutePos(new BlockPos(1, 2, 2));
        var apart = helper.absolutePos(new BlockPos(3, 1, 2));
        for (var pos : new BlockPos[]{a, b, c, apart}) level.setBlockAndUpdate(pos, glass);

        var player = playerWith(helper, ItemContent.SCULK_PAINT, 4);
        if (!paint(level, a, player)) {
            helper.fail("painting Industrial Glass was refused");
            return;
        }
        for (var pos : new BlockPos[]{a, b, c}) {
            if (colour(level, pos) != ColorVariant.SCULK) helper.fail("glass at " + pos + " isn't sculk");
        }
        if (PipePaintData.get(level).has(apart)) helper.fail("glass that doesn't touch the group was painted");
        if (Config.pipePaintCostMode() == Config.PipePaintCostMode.NETWORK && player.getMainHandItem().getCount() != 3) {
            helper.fail("painting three glass blocks should use one cartridge, " + player.getMainHandItem().getCount() + " of 4 left");
        }
        if (paint(level, b, player)) helper.fail("repainted glass with the same colour");
        helper.succeed();
    }

    /** One click paints the walls and everything built into the reactor, but not glass next to it. */
    @ModGameTest(timeoutTicks = 100)
    public static void paintReactor(GameTestHelper helper) {
        var level = helper.getLevel();
        var wallA = helper.absolutePos(new BlockPos(1, 1, 2));
        var wallB = helper.absolutePos(new BlockPos(2, 1, 2));
        var controller = helper.absolutePos(new BlockPos(1, 2, 2));
        var rod = helper.absolutePos(new BlockPos(2, 2, 2));
        var heatPipe = helper.absolutePos(new BlockPos(3, 1, 2));
        var glass = helper.absolutePos(new BlockPos(3, 2, 2));
        level.setBlockAndUpdate(wallA, BlockContent.REACTOR_WALL.get().defaultBlockState());
        level.setBlockAndUpdate(wallB, BlockContent.REACTOR_WALL.get().defaultBlockState());
        level.setBlockAndUpdate(controller, BlockContent.NUCLEAR_REACTOR_CONTROLLER.get().defaultBlockState());
        level.setBlockAndUpdate(rod, BlockContent.REACTOR_ROD.get().defaultBlockState());
        level.setBlockAndUpdate(heatPipe, BlockContent.REACTOR_HEAT_PIPE.get().defaultBlockState());
        level.setBlockAndUpdate(glass, BlockContent.INDUSTRIAL_GLASS.get().defaultBlockState());

        var player = playerWith(helper, ItemContent.WHITE_PAINT, 4);
        if (!paint(level, rod, player)) {
            helper.fail("painting a reactor rod was refused");
            return;
        }
        for (var pos : new BlockPos[]{wallA, wallB, controller, rod, heatPipe}) {
            if (colour(level, pos) != ColorVariant.WHITE) helper.fail("reactor block at " + pos + " isn't white");
        }
        if (PipePaintData.get(level).has(glass)) helper.fail("glass next to the reactor was painted with it");

        // Orange clears the whole reactor again.
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemContent.ORANGE_PAINT));
        if (!paint(level, wallA, player)) helper.fail("orange paint was refused");
        for (var pos : new BlockPos[]{wallA, wallB, controller, rod, heatPipe}) {
            if (PipePaintData.get(level).has(pos)) helper.fail("orange should clear the reactor block at " + pos);
        }
        helper.succeed();
    }

    /** Accelerator parts are built with gaps; parts in line within reach are one group, parts further away aren't. */
    @ModGameTest(timeoutTicks = 100)
    public static void paintAcceleratorAcrossGaps(GameTestHelper helper) {
        var level = helper.getLevel();
        var ring = helper.absolutePos(new BlockPos(0, 1, 2));
        var ringAcrossGap = helper.absolutePos(new BlockPos(3, 1, 2));
        var motor = helper.absolutePos(new BlockPos(3, 1, 4));
        var tooFar = helper.absolutePos(new BlockPos(0, 2 + WorldPaintTarget.ACCELERATOR_REACH, 2));
        level.setBlockAndUpdate(ring, BlockContent.ACCELERATOR_RING.get().defaultBlockState());
        level.setBlockAndUpdate(ringAcrossGap, BlockContent.ACCELERATOR_RING.get().defaultBlockState());
        level.setBlockAndUpdate(motor, BlockContent.ACCELERATOR_MOTOR.get().defaultBlockState());
        level.setBlockAndUpdate(tooFar, BlockContent.ACCELERATOR_RING.get().defaultBlockState());

        var player = playerWith(helper, ItemContent.NETHERITE_PAINT, 4);
        if (!paint(level, ring, player)) {
            helper.fail("painting an accelerator ring was refused");
            return;
        }
        for (var pos : new BlockPos[]{ring, ringAcrossGap, motor}) {
            if (colour(level, pos) != ColorVariant.NETHERITE) helper.fail("accelerator part at " + pos + " isn't netherite");
        }
        if (PipePaintData.get(level).has(tooFar)) helper.fail("a ring beyond reach was painted");
        helper.succeed();
    }

    /** Glass placed against painted glass takes over its colour, like pipes do. */
    @ModGameTest
    public static void newGlassInheritsColour(GameTestHelper helper) {
        var level = helper.getLevel();
        var painted = helper.absolutePos(new BlockPos(1, 1, 2));
        var placed = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlockAndUpdate(painted, BlockContent.INDUSTRIAL_GLASS.get().defaultBlockState());
        PipePaintData.get(level).set(painted, ColorVariant.FLUXITE);
        level.setBlockAndUpdate(placed, BlockContent.INDUSTRIAL_GLASS.get().defaultBlockState());

        if (WorldPaintTarget.GLASS.inheritanceColor(level, placed) != ColorVariant.FLUXITE) helper.fail("new glass did not take over its neighbour's colour");
        if (WorldPaintTarget.REACTOR.inheritanceColor(level, placed) != null) helper.fail("glass was treated as a reactor block");
        helper.succeed();
    }

    /** Painting a hangar door extension paints the Oritech door it hangs on. */
    @ModGameTest(timeoutTicks = 100)
    public static void extensionPaintsDoor(GameTestHelper helper) {
        var level = helper.getLevel();
        var anchor = helper.absolutePos(new BlockPos(2, 1, 2));
        var doorState = BlockContent.HANGAR_DOOR.get().defaultBlockState().setValue(SURFACE, Direction.UP).setValue(ROTATED, false);
        level.setBlock(anchor, doorState, 3);
        BlockContent.HANGAR_DOOR.get().setPlacedBy(level, anchor, doorState, null, ItemStack.EMPTY); // Oritech adds its 2 helper blocks

        var tip = anchor.above(2);
        var hit = new BlockHitResult(Vec3.atCenterOf(tip).relative(Direction.UP, 0.5), Direction.UP, tip, false);
        var extensionState = ModContent.HANGAR_DOOR_EXTENSION.get().getStateForPlacement(
                new BlockPlaceContext(level, null, InteractionHand.MAIN_HAND, new ItemStack(ModContent.HANGAR_DOOR_EXTENSION_ITEM.get()), hit));
        if (extensionState == null) {
            helper.fail("couldn't place an extension on the door");
            return;
        }
        var extension = tip.above();
        level.setBlock(extension, extensionState, 3);

        var player = playerWith(helper, ItemContent.WHITE_PAINT, 4);
        if (!paint(level, extension, player)) {
            helper.fail("painting the extension was refused");
            return;
        }
        if (!(level.getBlockEntity(anchor) instanceof ColorableMachine door) || door.getCurrentColor() != ColorVariant.WHITE) {
            helper.fail("the door isn't white after painting its extension");
            return;
        }
        if (player.getMainHandItem().getCount() != 3) helper.fail("painting a door should use one cartridge");
        if (paint(level, extension, player)) helper.fail("repainted the door with the same colour");
        helper.succeed();
    }

    // ---------------------------------------------------------------- helpers

    private static Player playerWith(GameTestHelper helper, net.minecraft.world.level.ItemLike paint, int count) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(paint, count));
        return player;
    }

    private static boolean paint(ServerLevel level, BlockPos pos, Player player) {
        return PaintTargets.tryPaint(level, pos, player, InteractionHand.MAIN_HAND, player.getMainHandItem());
    }

    private static ColorVariant colour(ServerLevel level, BlockPos pos) {
        return PipePaintData.get(level).getOrDefault(pos);
    }
}
