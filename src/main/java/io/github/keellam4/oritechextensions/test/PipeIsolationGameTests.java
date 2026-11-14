package io.github.keellam4.oritechextensions.test;

import io.github.keellam4.oritechextensions.OritechExtensions;
import io.github.keellam4.oritechextensions.paint.PipeIsolation;
import io.github.keellam4.oritechextensions.paint.PipePaintData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rearth.oritech.block.blocks.pipes.AbstractPipeBlock;
import rearth.oritech.block.blocks.pipes.GenericPipeBlock;
import rearth.oritech.init.BlockContent;
import rearth.oritech.util.ColorableMachine.ColorVariant;

import java.util.Map;

/** Colour-isolated pipes: differently painted pipes don't connect; unpainted pipes connect to everything. */
@GameTestHolder(OritechExtensions.MODID)
@PrefixGameTestTemplate(false)
public class PipeIsolationGameTests {

    private static final String TEMPLATE = "empty_tall";

    /** Paint {@code pos} the way the cartridge path does: store the colour, then let isolation react. */
    private static void paint(ServerLevel level, BlockPos pos, ColorVariant colour) {
        var data = PipePaintData.get(level);
        var before = data.getOrDefault(pos);
        data.set(pos, colour);
        PipeIsolation.onPaintChanged(level, Map.of(pos, before));
    }

    private static boolean connected(ServerLevel level, BlockPos pos, Direction direction) {
        var state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof GenericPipeBlock pipe)) return false;
        return state.getValue(pipe.directionToProperty(direction)) != GenericPipeBlock.NO_CONNECTION;
    }

    private static Integer network(ServerLevel level, BlockPos pos) {
        var pipe = (AbstractPipeBlock) level.getBlockState(pos).getBlock();
        return pipe.getNetworkData(level).pipeNetworkLinks.get(pos);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void differentColoursDisconnectAndReconnect(GameTestHelper helper) {
        var level = helper.getLevel();
        var a = helper.absolutePos(new BlockPos(1, 1, 2));
        var b = helper.absolutePos(new BlockPos(2, 1, 2));
        var c = helper.absolutePos(new BlockPos(3, 1, 2));
        for (var pos : new BlockPos[]{a, b, c}) level.setBlockAndUpdate(pos, BlockContent.ITEM_PIPE.defaultBlockState());

        helper.runAfterDelay(2, () -> {
            if (!connected(level, b, Direction.EAST) || !connected(level, c, Direction.WEST)) {
                helper.fail("fresh unpainted pipes didn't connect");
                return;
            }
            paint(level, a, ColorVariant.WHITE);
            paint(level, b, ColorVariant.WHITE);
            paint(level, c, ColorVariant.SCULK);

            if (connected(level, b, Direction.EAST) || connected(level, c, Direction.WEST)) {
                helper.fail("white and sculk pipes still connect");
                return;
            }
            if (!connected(level, a, Direction.EAST)) {
                helper.fail("two white pipes stopped connecting");
                return;
            }
            if (network(level, b) == null || network(level, b).equals(network(level, c))) {
                helper.fail("white and sculk pipes are still one network");
                return;
            }

            paint(level, c, ColorVariant.WHITE);
            if (!connected(level, b, Direction.EAST) || !connected(level, c, Direction.WEST)) {
                helper.fail("repainting to the same colour didn't reconnect");
                return;
            }
            if (!network(level, b).equals(network(level, c))) {
                helper.fail("reconnected pipes aren't one network again");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void unpaintedPipeBridgesColours(GameTestHelper helper) {
        var level = helper.getLevel();
        var a = helper.absolutePos(new BlockPos(1, 1, 2));
        var b = helper.absolutePos(new BlockPos(2, 1, 2));
        var c = helper.absolutePos(new BlockPos(3, 1, 2));
        for (var pos : new BlockPos[]{a, b, c}) level.setBlockAndUpdate(pos, BlockContent.FLUID_PIPE.defaultBlockState());

        helper.runAfterDelay(2, () -> {
            paint(level, a, ColorVariant.WHITE);
            paint(level, c, ColorVariant.SCULK);
            if (!connected(level, b, Direction.WEST) || !connected(level, b, Direction.EAST)) {
                helper.fail("unpainted fluid pipe stopped connecting to painted neighbours");
                return;
            }
            // Clearing paint (orange) makes a pipe universal again.
            paint(level, b, ColorVariant.WHITE);
            if (connected(level, b, Direction.EAST)) {
                helper.fail("white pipe connects to sculk pipe");
                return;
            }
            paint(level, b, ColorVariant.ORANGE);
            if (!connected(level, b, Direction.EAST) || !connected(level, c, Direction.WEST)) {
                helper.fail("clearing paint didn't reconnect");
                return;
            }
            helper.succeed();
        });
    }
}
