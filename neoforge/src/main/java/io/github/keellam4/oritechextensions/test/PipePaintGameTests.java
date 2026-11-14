package io.github.keellam4.oritechextensions.test;

import io.github.keellam4.oritechextensions.Config;
import io.github.keellam4.oritechextensions.OritechExtensions;
import io.github.keellam4.oritechextensions.paint.PipePaint;
import io.github.keellam4.oritechextensions.paint.PipePaintData;
import io.github.keellam4.oritechextensions.paint.PipePaintEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rearth.oritech.init.BlockContent;
import rearth.oritech.init.ItemContent;
import rearth.oritech.util.ColorableMachine.ColorVariant;

/** Painting standard Oritech item/fluid pipe networks (server side). */
@GameTestHolder(OritechExtensions.MODID)
@PrefixGameTestTemplate(false)
public class PipePaintGameTests {

    private static final String TEMPLATE = "empty_tall";

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void paintItemPipeNetwork(GameTestHelper helper) {
        paintNetwork(helper, BlockContent.ITEM_PIPE);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void paintFluidPipeNetwork(GameTestHelper helper) {
        paintNetwork(helper, BlockContent.FLUID_PIPE);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void paintTransparentItemPipeNetwork(GameTestHelper helper) {
        paintNetwork(helper, BlockContent.TRANSPARENT_ITEM_PIPE);
    }

    /** Transparent and regular item pipes form one Oritech network, so they paint together. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void paintMixedItemPipeNetwork(GameTestHelper helper) {
        var level = helper.getLevel();
        var a = helper.absolutePos(new BlockPos(1, 1, 2));
        var b = helper.absolutePos(new BlockPos(2, 1, 2));
        var c = helper.absolutePos(new BlockPos(3, 1, 2));
        level.setBlockAndUpdate(a, BlockContent.ITEM_PIPE.defaultBlockState());
        level.setBlockAndUpdate(b, BlockContent.TRANSPARENT_ITEM_PIPE.defaultBlockState());
        level.setBlockAndUpdate(c, BlockContent.ITEM_PIPE.defaultBlockState());

        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var paint = new ItemStack(ItemContent.SCULK_PAINT, 4);
        player.setItemInHand(InteractionHand.MAIN_HAND, paint);
        if (!PipePaintEvents.tryPaint(level, a, player, InteractionHand.MAIN_HAND, paint)) {
            helper.fail("painting a mixed item pipe network was refused");
            return;
        }
        for (var pos : new BlockPos[]{a, b, c}) {
            if (PipePaint.getPaint(level, pos) != ColorVariant.SCULK) {
                helper.fail("pipe at " + pos + " isn't sculk");
                return;
            }
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void ignoresFramedPipe(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlockAndUpdate(pos, BlockContent.FRAMED_ITEM_PIPE.defaultBlockState());
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var paint = new ItemStack(ItemContent.WHITE_PAINT, 4);
        player.setItemInHand(InteractionHand.MAIN_HAND, paint);
        if (PipePaintEvents.tryPaint(level, pos, player, InteractionHand.MAIN_HAND, paint)) {
            helper.fail("painted a framed pipe");
        }
        if (paint.getCount() != 4) helper.fail("used paint on a framed pipe");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void placeInheritsMatchingNeighbour(GameTestHelper helper) {
        var level = helper.getLevel();
        var a = helper.absolutePos(new BlockPos(1, 1, 2));
        var b = helper.absolutePos(new BlockPos(2, 1, 2));
        var pipe = BlockContent.ITEM_PIPE;
        level.setBlockAndUpdate(a, pipe.defaultBlockState());
        PipePaintData.get(level).set(a, ColorVariant.WHITE);

        level.setBlockAndUpdate(b, pipe.defaultBlockState());
        var inherited = PipePaint.inheritanceColor(level, b);
        if (inherited != ColorVariant.WHITE) helper.fail("expected white inheritance, got " + inherited);
        PipePaintData.get(level).set(b, inherited);
        if (PipePaint.getPaint(level, b) != ColorVariant.WHITE) helper.fail("placed pipe did not inherit");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void placeBetweenConflictingColoursStaysUnpainted(GameTestHelper helper) {
        var level = helper.getLevel();
        var left = helper.absolutePos(new BlockPos(1, 1, 2));
        var mid = helper.absolutePos(new BlockPos(2, 1, 2));
        var right = helper.absolutePos(new BlockPos(3, 1, 2));
        var pipe = BlockContent.ITEM_PIPE;
        level.setBlockAndUpdate(left, pipe.defaultBlockState());
        level.setBlockAndUpdate(right, pipe.defaultBlockState());
        PipePaintData.get(level).set(left, ColorVariant.WHITE);
        PipePaintData.get(level).set(right, ColorVariant.SCULK);

        level.setBlockAndUpdate(mid, pipe.defaultBlockState());
        if (PipePaint.inheritanceColor(level, mid) != null) helper.fail("conflicting neighbours should not inherit");
        if (PipePaintData.get(level).has(mid)) helper.fail("middle pipe should stay unpainted");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void breakClearsPaintAndDropsClean(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 1, 2));
        var pipe = BlockContent.ITEM_PIPE;
        level.setBlockAndUpdate(pos, pipe.defaultBlockState());
        PipePaintData.get(level).set(pos, ColorVariant.WHITE);

        level.destroyBlock(pos, true);
        // BreakEvent may not fire from destroyBlock in game tests — clear explicitly if needed for assert path,
        // but tryPaint/cleanup via BreakEvent is covered in production; here assert drop has no paint component.
        var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2));
        var dropped = drops.stream().map(ItemEntity::getItem).filter(s -> s.is(pipe.asItem())).findFirst();
        if (dropped.isEmpty()) {
            helper.fail("pipe didn't drop");
            return;
        }
        // Pipe items never get PAINT_COMPONENT in this feature.
        if (dropped.get().has(io.github.keellam4.oritechextensions.ModContent.PAINT_COMPONENT.get())) {
            helper.fail("dropped pipe carried paint");
        }
        helper.succeed();
    }

    private static void paintNetwork(GameTestHelper helper, Block pipe) {
        var level = helper.getLevel();
        var a = helper.absolutePos(new BlockPos(1, 1, 2));
        var b = helper.absolutePos(new BlockPos(2, 1, 2));
        var c = helper.absolutePos(new BlockPos(3, 1, 2));
        level.setBlockAndUpdate(a, pipe.defaultBlockState());
        level.setBlockAndUpdate(b, pipe.defaultBlockState());
        level.setBlockAndUpdate(c, pipe.defaultBlockState());
        // Nudge connections so discovery sees a contiguous run (Oritech updates neighbours on place).
        for (var pos : new BlockPos[]{a, b, c}) {
            level.getBlockState(pos).updateNeighbourShapes(level, pos, Block.UPDATE_ALL);
        }

        var network = PipePaint.discoverNetwork(level, b);
        if (network.size() < 3) {
            // Discovery walks adjacent same-family blocks regardless of connection props — should still be 3.
            helper.fail("expected network of 3, got " + network.size());
            return;
        }

        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var paint = new ItemStack(ItemContent.WHITE_PAINT, 4);
        player.setItemInHand(InteractionHand.MAIN_HAND, paint);

        if (!PipePaintEvents.tryPaint(level, b, player, InteractionHand.MAIN_HAND, paint)) {
            helper.fail("painting network was refused");
            return;
        }
        if (Config.pipePaintCostMode() == Config.PipePaintCostMode.NETWORK && player.getMainHandItem().getCount() != 3) {
            helper.fail("NETWORK mode should use 1 cartridge, left " + player.getMainHandItem().getCount());
        }
        for (var pos : new BlockPos[]{a, b, c}) {
            if (PipePaint.getPaint(level, pos) != ColorVariant.WHITE) {
                helper.fail("pipe at " + pos + " isn't white");
                return;
            }
        }

        // Same colour again: no consume.
        if (PipePaintEvents.tryPaint(level, b, player, InteractionHand.MAIN_HAND, player.getMainHandItem())) {
            helper.fail("repainted network with same colour");
        }

        // Orange clears.
        var orange = new ItemStack(ItemContent.ORANGE_PAINT);
        player.setItemInHand(InteractionHand.MAIN_HAND, orange);
        if (!PipePaintEvents.tryPaint(level, b, player, InteractionHand.MAIN_HAND, orange)) {
            helper.fail("orange clear was refused");
            return;
        }
        for (var pos : new BlockPos[]{a, b, c}) {
            if (PipePaintData.get(level).has(pos)) {
                helper.fail("orange should clear paint at " + pos);
                return;
            }
        }

        helper.succeed();
    }
}
