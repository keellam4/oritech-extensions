package io.github.keellam4.oritechextensions.test;

import io.github.keellam4.oritechextensions.ModContent;
import io.github.keellam4.oritechextensions.OritechExtensions;
import io.github.keellam4.oritechextensions.paint.Paint;
import io.github.keellam4.oritechextensions.paint.PaintEvents;
import io.github.keellam4.oritechextensions.paint.PaintTargets;
import io.github.keellam4.oritechextensions.paint.PipeIsolation;
import io.github.keellam4.oritechextensions.paint.PipePaint;
import io.github.keellam4.oritechextensions.paint.PipePaintData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rearth.oritech.block.blocks.pipes.AbstractPipeBlock;
import rearth.oritech.block.blocks.pipes.GenericPipeBlock;
import rearth.oritech.init.BlockContent;

import java.util.Map;

/** The Rainbow Color Cartridge: rainbow is stored, kept on drops, and isolates pipes like any other colour. */
@GameTestHolder(OritechExtensions.MODID)
@PrefixGameTestTemplate(false)
public class RainbowGameTests {

    private static final String TEMPLATE = "empty_tall";

    private static ItemStack rainbow(int count) {
        return new ItemStack(ModContent.RAINBOW_CARTRIDGE.get(), count);
    }

    /** A machine addon painted rainbow stays rainbow, and its dropped item remembers it. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void rainbowAddonKeepsColourWhenDropped(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlockAndUpdate(pos.below(), Blocks.IRON_BLOCK.defaultBlockState());
        var state = BlockContent.MACHINE_SPEED_ADDON.defaultBlockState();
        if (state.hasProperty(BlockStateProperties.ATTACH_FACE)) state = state.setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR);
        level.setBlockAndUpdate(pos, state);

        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var cartridges = rainbow(4);
        player.setItemInHand(InteractionHand.MAIN_HAND, cartridges);
        if (!PaintTargets.tryPaint(level, pos, player, InteractionHand.MAIN_HAND, cartridges)) {
            helper.fail("painting an addon rainbow was refused");
            return;
        }
        if (PaintEvents.getPaint(level.getBlockEntity(pos)) != Paint.RAINBOW) helper.fail("addon isn't rainbow");
        if (player.getMainHandItem().getCount() != 3) helper.fail("expected 3 rainbow cartridges left, have " + player.getMainHandItem().getCount());

        level.destroyBlock(pos, true);
        var dropped = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2)).stream()
                .map(ItemEntity::getItem).filter(s -> s.is(BlockContent.MACHINE_SPEED_ADDON.asItem())).findFirst();
        if (dropped.isEmpty()) {
            helper.fail("addon didn't drop");
            return;
        }
        if (dropped.get().get(ModContent.PAINT_COMPONENT.get()) != Paint.RAINBOW) helper.fail("dropped addon lost its rainbow paint");
        helper.succeed();
    }

    /** One rainbow cartridge paints a whole pipe network rainbow. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void rainbowPaintsPipeNetwork(GameTestHelper helper) {
        var level = helper.getLevel();
        var positions = new BlockPos[3];
        for (int i = 0; i < positions.length; i++) {
            positions[i] = helper.absolutePos(new BlockPos(1 + i, 1, 2));
            level.setBlockAndUpdate(positions[i], BlockContent.ITEM_PIPE.defaultBlockState());
        }
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var cartridges = rainbow(4);
        player.setItemInHand(InteractionHand.MAIN_HAND, cartridges);
        if (!PaintTargets.tryPaint(level, positions[1], player, InteractionHand.MAIN_HAND, cartridges)) {
            helper.fail("painting a pipe network rainbow was refused");
            return;
        }
        for (var pos : positions) {
            if (PipePaint.getPaint(level, pos) != Paint.RAINBOW) helper.fail("pipe at " + pos + " isn't rainbow");
        }
        helper.succeed();
    }

    /** Rainbow is a colour of its own: rainbow pipes don't connect to white ones. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void rainbowPipesIsolateFromOtherColours(GameTestHelper helper) {
        var level = helper.getLevel();
        var a = helper.absolutePos(new BlockPos(1, 1, 2));
        var b = helper.absolutePos(new BlockPos(2, 1, 2));
        for (var pos : new BlockPos[]{a, b}) place(level, pos, BlockContent.ITEM_PIPE);

        helper.runAfterDelay(2, () -> {
            if (!connected(level, a, Direction.EAST)) {
                helper.fail("fresh unpainted pipes didn't connect");
                return;
            }
            paint(level, a, Paint.RAINBOW);
            paint(level, b, Paint.WHITE);
            if (connected(level, a, Direction.EAST) || connected(level, b, Direction.WEST)) {
                helper.fail("rainbow and white pipes still connect");
                return;
            }
            paint(level, b, Paint.RAINBOW);
            if (!connected(level, a, Direction.EAST)) {
                helper.fail("two rainbow pipes don't connect");
                return;
            }
            helper.succeed();
        });
    }

    private static void paint(ServerLevel level, BlockPos pos, Paint colour) {
        var data = PipePaintData.get(level);
        var before = data.getOrDefault(pos);
        data.set(pos, colour);
        PipeIsolation.onPaintChanged(level, Map.of(pos, before));
    }

    private static void place(ServerLevel level, BlockPos pos, Block block) {
        var pipe = (AbstractPipeBlock) block;
        level.setBlockAndUpdate(pos, pipe.addConnectionStates(block.defaultBlockState(), level, pos, true));
    }

    private static boolean connected(ServerLevel level, BlockPos pos, Direction direction) {
        var state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof GenericPipeBlock pipe)) return false;
        return state.getValue(pipe.directionToProperty(direction)) != GenericPipeBlock.NO_CONNECTION;
    }
}
