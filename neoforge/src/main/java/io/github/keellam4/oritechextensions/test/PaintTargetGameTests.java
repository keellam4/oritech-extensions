package io.github.keellam4.oritechextensions.test;

import io.github.keellam4.oritechextensions.Config;
import io.github.keellam4.oritechextensions.ModContent;
import io.github.keellam4.oritechextensions.OritechExtensions;
import io.github.keellam4.oritechextensions.paint.PaintTargets;
import io.github.keellam4.oritechextensions.paint.PipePaintData;
import io.github.keellam4.oritechextensions.paint.targets.WorldPaintTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
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
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rearth.oritech.init.BlockContent;
import rearth.oritech.init.ItemContent;
import rearth.oritech.util.ColorableMachine;
import io.github.keellam4.oritechextensions.paint.Paint;

import static rearth.oritech.block.blocks.decorative.HangarDoorBlock.ROTATED;
import static rearth.oritech.block.blocks.decorative.HangarDoorBlock.SURFACE;

/** Painting the blocks that go through the shared paint targets: Industrial Glass, reactors, accelerators, hangar doors. */
@GameTestHolder(OritechExtensions.MODID)
@PrefixGameTestTemplate(false)
public class PaintTargetGameTests {

    private static final String TEMPLATE = "empty_tall"; // 5 x 20 x 5 of air

    /** Touching glass is painted together; glass that doesn't touch is left alone. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void paintIndustrialGlass(GameTestHelper helper) {
        var level = helper.getLevel();
        var glass = BlockContent.INDUSTRIAL_GLASS_BLOCK.defaultBlockState();
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
            if (colour(level, pos) != Paint.SCULK) helper.fail("glass at " + pos + " isn't sculk");
        }
        if (PipePaintData.get(level).has(apart)) helper.fail("glass that doesn't touch the group was painted");
        if (Config.pipePaintCostMode() == Config.PipePaintCostMode.NETWORK && player.getMainHandItem().getCount() != 3) {
            helper.fail("painting three glass blocks should use one cartridge, " + player.getMainHandItem().getCount() + " of 4 left");
        }
        if (paint(level, b, player)) helper.fail("repainted glass with the same colour");
        helper.succeed();
    }

    /** A column of Metal Beams and the Metal Girders resting on it are one support structure. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void paintSupports(GameTestHelper helper) {
        var level = helper.getLevel();
        var beamA = helper.absolutePos(new BlockPos(1, 1, 2));
        var beamB = helper.absolutePos(new BlockPos(1, 2, 2));
        var girderA = helper.absolutePos(new BlockPos(2, 2, 2));
        var girderB = helper.absolutePos(new BlockPos(3, 2, 2));
        var apart = helper.absolutePos(new BlockPos(1, 4, 2));
        level.setBlockAndUpdate(beamA, BlockContent.METAL_BEAM_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(beamB, BlockContent.METAL_BEAM_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(girderA, BlockContent.METAL_GIRDER_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(girderB, BlockContent.METAL_GIRDER_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(apart, BlockContent.METAL_BEAM_BLOCK.defaultBlockState());

        var player = playerWith(helper, ItemContent.NETHERITE_PAINT, 4);
        if (!paint(level, beamA, player)) {
            helper.fail("painting a Metal Beam was refused");
            return;
        }
        for (var pos : new BlockPos[]{beamA, beamB, girderA, girderB}) {
            if (colour(level, pos) != Paint.NETHERITE) helper.fail(level.getBlockState(pos).getBlock() + " at " + pos + " isn't netherite");
        }
        if (PipePaintData.get(level).has(apart)) helper.fail("a beam that doesn't touch the structure was painted");
        helper.succeed();
    }

    /** Pipe Boosters are painted one at a time, even when they touch. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void paintPipeBoosterAlone(GameTestHelper helper) {
        var level = helper.getLevel();
        var a = helper.absolutePos(new BlockPos(1, 1, 2));
        var b = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlockAndUpdate(a, BlockContent.PIPE_BOOSTER_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(b, BlockContent.PIPE_BOOSTER_BLOCK.defaultBlockState());
        var player = playerWith(helper, ItemContent.FLUXITE_PAINT, 4);
        if (!paint(level, a, player)) {
            helper.fail("painting a Pipe Booster was refused");
            return;
        }
        if (colour(level, a) != Paint.FLUXITE) helper.fail("booster isn't fluxite");
        if (PipePaintData.get(level).has(b)) helper.fail("painting one booster also painted the one next to it");
        helper.succeed();
    }

    /** One click paints the walls and everything built into the reactor, but not glass next to it. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void paintReactor(GameTestHelper helper) {
        var level = helper.getLevel();
        var wallA = helper.absolutePos(new BlockPos(1, 1, 2));
        var wallB = helper.absolutePos(new BlockPos(2, 1, 2));
        var controller = helper.absolutePos(new BlockPos(1, 2, 2));
        var rod = helper.absolutePos(new BlockPos(2, 2, 2));
        var heatPipe = helper.absolutePos(new BlockPos(3, 1, 2));
        var glass = helper.absolutePos(new BlockPos(3, 2, 2));
        level.setBlockAndUpdate(wallA, BlockContent.REACTOR_WALL.defaultBlockState());
        level.setBlockAndUpdate(wallB, BlockContent.REACTOR_WALL.defaultBlockState());
        level.setBlockAndUpdate(controller, BlockContent.REACTOR_CONTROLLER.defaultBlockState());
        level.setBlockAndUpdate(rod, BlockContent.REACTOR_ROD.defaultBlockState());
        level.setBlockAndUpdate(heatPipe, BlockContent.REACTOR_HEAT_PIPE.defaultBlockState());
        level.setBlockAndUpdate(glass, BlockContent.INDUSTRIAL_GLASS_BLOCK.defaultBlockState());

        var player = playerWith(helper, ItemContent.WHITE_PAINT, 4);
        if (!paint(level, rod, player)) {
            helper.fail("painting a reactor rod was refused");
            return;
        }
        for (var pos : new BlockPos[]{wallA, wallB, controller, rod, heatPipe}) {
            if (colour(level, pos) != Paint.WHITE) helper.fail("reactor block at " + pos + " isn't white");
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
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void paintAcceleratorAcrossGaps(GameTestHelper helper) {
        var level = helper.getLevel();
        var ring = helper.absolutePos(new BlockPos(0, 1, 2));
        var ringAcrossGap = helper.absolutePos(new BlockPos(3, 1, 2));
        var motor = helper.absolutePos(new BlockPos(3, 1, 4));
        var tooFar = helper.absolutePos(new BlockPos(0, 2 + WorldPaintTarget.ACCELERATOR_REACH, 2));
        level.setBlockAndUpdate(ring, BlockContent.ACCELERATOR_RING.defaultBlockState());
        level.setBlockAndUpdate(ringAcrossGap, BlockContent.ACCELERATOR_RING.defaultBlockState());
        level.setBlockAndUpdate(motor, BlockContent.ACCELERATOR_MOTOR.defaultBlockState());
        level.setBlockAndUpdate(tooFar, BlockContent.ACCELERATOR_RING.defaultBlockState());

        var player = playerWith(helper, ItemContent.NETHERITE_PAINT, 4);
        if (!paint(level, ring, player)) {
            helper.fail("painting an accelerator ring was refused");
            return;
        }
        for (var pos : new BlockPos[]{ring, ringAcrossGap, motor}) {
            if (colour(level, pos) != Paint.NETHERITE) helper.fail("accelerator part at " + pos + " isn't netherite");
        }
        if (PipePaintData.get(level).has(tooFar)) helper.fail("a ring beyond reach was painted");
        helper.succeed();
    }

    /** Glass placed against painted glass takes over its colour, like pipes do. */
    @GameTest(template = TEMPLATE)
    public static void newGlassInheritsColour(GameTestHelper helper) {
        var level = helper.getLevel();
        var painted = helper.absolutePos(new BlockPos(1, 1, 2));
        var placed = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlockAndUpdate(painted, BlockContent.INDUSTRIAL_GLASS_BLOCK.defaultBlockState());
        PipePaintData.get(level).set(painted, Paint.FLUXITE);
        level.setBlockAndUpdate(placed, BlockContent.INDUSTRIAL_GLASS_BLOCK.defaultBlockState());

        if (WorldPaintTarget.GLASS.inheritanceColor(level, placed) != Paint.FLUXITE) helper.fail("new glass did not take over its neighbour's colour");
        if (WorldPaintTarget.REACTOR.inheritanceColor(level, placed) != null) helper.fail("glass was treated as a reactor block");
        helper.succeed();
    }

    /** Painting a hangar door extension paints the Oritech door it hangs on. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void extensionPaintsDoor(GameTestHelper helper) {
        var level = helper.getLevel();
        var anchor = helper.absolutePos(new BlockPos(2, 1, 2));
        var doorState = BlockContent.HANGAR_DOOR.defaultBlockState().setValue(SURFACE, Direction.UP).setValue(ROTATED, false);
        level.setBlock(anchor, doorState, 3);
        BlockContent.HANGAR_DOOR.setPlacedBy(level, anchor, doorState, null, ItemStack.EMPTY); // Oritech adds its 2 helper blocks

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
        if (!(level.getBlockEntity(anchor) instanceof ColorableMachine door) || Paint.of(door.getCurrentColor()) != Paint.WHITE) {
            helper.fail("the door isn't white after painting its extension");
            return;
        }
        if (player.getMainHandItem().getCount() != 3) helper.fail("painting a door should use one cartridge");

        // Oritech paints the door itself, so a Rainbow Cartridge leaves it alone and isn't used up.
        var rainbow = new ItemStack(ModContent.RAINBOW_CARTRIDGE.get(), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, rainbow);
        if (paint(level, extension, player)) helper.fail("a Rainbow Cartridge painted a hangar door");
        if (player.getMainHandItem().getCount() != 2) helper.fail("a Rainbow Cartridge was used on a hangar door");
        if (Paint.of(door.getCurrentColor()) != Paint.WHITE) helper.fail("a Rainbow Cartridge changed the door's colour");
        if (paint(level, extension, player)) helper.fail("repainted the door with the same colour");
        helper.succeed();
    }

    // ---------------------------------------------------------------- helpers

    private static Player playerWith(GameTestHelper helper, Item paint, int count) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(paint, count));
        return player;
    }

    private static boolean paint(ServerLevel level, BlockPos pos, Player player) {
        return PaintTargets.tryPaint(level, pos, player, InteractionHand.MAIN_HAND, player.getMainHandItem());
    }

    private static Paint colour(ServerLevel level, BlockPos pos) {
        return PipePaintData.get(level).getOrDefault(pos);
    }

    /** Painting glass its own colour again tints the pane; once more clears the tint; repainting keeps it tinted. */
    @net.minecraft.gametest.framework.GameTest(template = "empty_tall", timeoutTicks = 40)
    public static void glassTintToggles(net.minecraft.gametest.framework.GameTestHelper helper) {
        var a = new net.minecraft.core.BlockPos(2, 1, 2);
        helper.setBlock(a, rearth.oritech.init.BlockContent.INDUSTRIAL_GLASS_BLOCK);
        helper.setBlock(a.east(), rearth.oritech.init.BlockContent.INDUSTRIAL_GLASS_BLOCK);
        var level = helper.getLevel();
        var target = io.github.keellam4.oritechextensions.paint.targets.WorldPaintTarget.GLASS;
        var pos = helper.absolutePos(a);
        var data = io.github.keellam4.oritechextensions.paint.PipePaintData.get(level);
        var white = io.github.keellam4.oritechextensions.paint.Paint.WHITE;
        target.apply(level, target.group(level, pos), white, 100);
        if (data.isTinted(pos)) helper.fail("the first coat paints the frame only");
        target.apply(level, target.group(level, pos), white, 100);
        if (!data.isTinted(pos) || !data.isTinted(pos.east())) helper.fail("a second coat of the same colour tints the whole pane");
        target.apply(level, target.group(level, pos), io.github.keellam4.oritechextensions.paint.Paint.SCULK, 100);
        if (!data.isTinted(pos) || data.getOrDefault(pos) != io.github.keellam4.oritechextensions.paint.Paint.SCULK) helper.fail("repainting keeps the tint");
        target.apply(level, target.group(level, pos), io.github.keellam4.oritechextensions.paint.Paint.SCULK, 100);
        if (data.isTinted(pos)) helper.fail("painting the same colour again clears the tint");
        data.setTinted(pos, true);
        data.remove(pos);
        if (data.isTinted(pos)) helper.fail("an unpainted block can't stay tinted");
        helper.succeed();
    }
}
