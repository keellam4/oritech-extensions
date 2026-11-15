package io.github.keellam4.oritechextensions.test;

import io.github.keellam4.oritechextensions.ModContent;
import io.github.keellam4.oritechextensions.OritechExtensions;
import io.github.keellam4.oritechextensions.paint.Paint;
import io.github.keellam4.oritechextensions.paint.PaintEvents;
import io.github.keellam4.oritechextensions.paint.PipePaint;
import io.github.keellam4.oritechextensions.tool.DetailingMode;
import io.github.keellam4.oritechextensions.tool.DetailingTool;
import io.github.keellam4.oritechextensions.tool.DetailingToolItem;
import io.github.keellam4.oritechextensions.tool.ToolContents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rearth.oritech.init.BlockContent;
import rearth.oritech.init.ItemContent;
import rearth.oritech.util.ColorableMachine;

import java.util.Optional;

/** The Detailing Tool: loading cartridges, the three modes, the eyedropper and Oritech machines. */
@GameTestHolder(OritechExtensions.MODID)
@PrefixGameTestTemplate(false)
public class DetailingToolGameTests {

    private static final String TEMPLATE = "empty_tall";

    private static ItemStack tool(Player player) {
        var tool = new ItemStack(ModContent.DETAILING_TOOL.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        return player.getMainHandItem();
    }

    private static void load(ItemStack tool, Player player, ItemStack cartridges) {
        int moved = DetailingToolItem.load(tool, cartridges, player);
        cartridges.shrink(moved);
    }

    private static void mode(ItemStack tool, DetailingMode mode) {
        DetailingToolItem.setContents(tool, DetailingToolItem.contents(tool).withMode(mode));
    }

    private static BlockPos addon(GameTestHelper helper, BlockPos relative) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(relative);
        level.setBlockAndUpdate(pos.below(), Blocks.IRON_BLOCK.defaultBlockState());
        var state = BlockContent.MACHINE_SPEED_ADDON.defaultBlockState();
        if (state.hasProperty(BlockStateProperties.ATTACH_FACE)) state = state.setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR);
        level.setBlockAndUpdate(pos, state);
        return pos;
    }

    private static BlockPos pipe(GameTestHelper helper, int x, int z) {
        var pos = helper.absolutePos(new BlockPos(x, 1, z));
        helper.getLevel().setBlockAndUpdate(pos, BlockContent.ITEM_PIPE.defaultBlockState());
        return pos;
    }

    /** Cartridges load up to a stack per colour, the first loaded colour is selected, and unloading gives them back. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void loadsAndUnloadsCartridges(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var tool = tool(player);

        var redstone = new ItemStack(ItemContent.REDSTONE_PAINT, 10);
        load(tool, player, redstone);
        var diamond = new ItemStack(ItemContent.DIAMOND_PAINT, 64);
        load(tool, player, diamond);
        var moreDiamond = new ItemStack(ItemContent.DIAMOND_PAINT, 10);
        load(tool, player, moreDiamond);

        var contents = DetailingToolItem.contents(tool);
        if (contents.count(Paint.REDSTONE) != 10 || !redstone.isEmpty()) helper.fail("expected 10 redstone charges, have " + contents.count(Paint.REDSTONE));
        if (contents.count(Paint.DIAMOND) != ToolContents.CAPACITY) helper.fail("diamond should stop at " + ToolContents.CAPACITY);
        if (moreDiamond.getCount() != 10) helper.fail("a full colour took more cartridges");
        if (contents.selected() != Paint.REDSTONE) helper.fail("the first loaded colour should be selected, not " + contents.selected());
        if (contents.cycle(1).selected() != Paint.DIAMOND) helper.fail("scrolling didn't switch to diamond");

        var out = DetailingToolItem.unload(tool, player);
        if (!out.is(ItemContent.REDSTONE_PAINT) || out.getCount() != 10) helper.fail("unloading gave back " + out);
        if (DetailingToolItem.contents(tool).count(Paint.REDSTONE) != 0) helper.fail("redstone charges still in the tool");

        var rainbow = new ItemStack(ModContent.RAINBOW_CARTRIDGE.get(), 3);
        load(tool, player, rainbow);
        if (DetailingToolItem.contents(tool).count(Paint.RAINBOW) != 3) helper.fail("rainbow cartridges didn't load");
        helper.succeed();
    }

    /** Painting an addon uses one charge. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void paintsAddonWithOneCharge(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = addon(helper, new BlockPos(2, 2, 2));
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var tool = tool(player);
        load(tool, player, new ItemStack(ItemContent.SCULK_PAINT, 4));

        DetailingTool.paint(level, pos, player, tool);
        if (PaintEvents.getPaint(level.getBlockEntity(pos)) != Paint.SCULK) helper.fail("addon isn't sculk");
        if (DetailingToolItem.contents(tool).count(Paint.SCULK) != 3) helper.fail("expected 3 charges left");

        DetailingTool.paint(level, pos, player, tool);
        if (DetailingToolItem.contents(tool).count(Paint.SCULK) != 3) helper.fail("repainting the same colour used a charge");
        helper.succeed();
    }

    /** Group mode paints the whole pipe network; Single block mode only the clicked pipe. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void groupAndSingleModes(GameTestHelper helper) {
        var level = helper.getLevel();
        var pipes = new BlockPos[]{pipe(helper, 1, 2), pipe(helper, 2, 2), pipe(helper, 3, 2)};
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var tool = tool(player);
        load(tool, player, new ItemStack(ItemContent.CAMO_PAINT, 4));
        load(tool, player, new ItemStack(ItemContent.WHITE_PAINT, 4));

        DetailingTool.paint(level, pipes[0], player, tool);
        for (var pos : pipes) {
            if (PipePaint.getPaint(level, pos) != Paint.CAMO) helper.fail("group mode missed the pipe at " + pos);
        }

        mode(tool, DetailingMode.SINGLE);
        DetailingToolItem.setContents(tool, DetailingToolItem.contents(tool).withSelected(Paint.WHITE));
        DetailingTool.paint(level, pipes[1], player, tool);
        if (PipePaint.getPaint(level, pipes[1]) != Paint.WHITE) helper.fail("single mode didn't paint the clicked pipe");
        if (PipePaint.getPaint(level, pipes[0]) != Paint.CAMO || PipePaint.getPaint(level, pipes[2]) != Paint.CAMO) {
            helper.fail("single mode painted more than the clicked pipe");
        }
        helper.succeed();
    }

    /** Area mode: two clicks mark a box; each group inside costs one charge, blocks outside stay as they are. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void areaModePaintsTheBox(GameTestHelper helper) {
        var level = helper.getLevel();
        var a = pipe(helper, 1, 1);
        var b = pipe(helper, 3, 1);
        var outside = pipe(helper, 3, 4);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var tool = tool(player);
        load(tool, player, new ItemStack(ItemContent.FLUXITE_PAINT, 10));
        mode(tool, DetailingMode.AREA);

        DetailingTool.paint(level, helper.absolutePos(new BlockPos(0, 1, 0)), player, tool);
        if (DetailingToolItem.contents(tool).corner().isEmpty()) helper.fail("the first click didn't set a corner");
        DetailingTool.paint(level, helper.absolutePos(new BlockPos(4, 2, 2)), player, tool);

        if (PipePaint.getPaint(level, a) != Paint.FLUXITE || PipePaint.getPaint(level, b) != Paint.FLUXITE) helper.fail("pipes in the box weren't painted");
        if (PipePaint.getPaint(level, outside) == Paint.FLUXITE) helper.fail("a pipe outside the box was painted");
        var contents = DetailingToolItem.contents(tool);
        if (contents.count(Paint.FLUXITE) != 8) helper.fail("expected 8 charges left (2 pipe groups), have " + contents.count(Paint.FLUXITE));
        if (contents.corner().isPresent()) helper.fail("the corner wasn't cleared after painting");
        helper.succeed();
    }

    /** Sneak + right-click picks the colour of the clicked block. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void eyedropperPicksColour(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = addon(helper, new BlockPos(2, 2, 2));
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var tool = tool(player);
        load(tool, player, new ItemStack(ItemContent.INDUSTRIAL_PAINT, 2));
        load(tool, player, new ItemStack(ItemContent.NETHERITE_PAINT, 2));
        DetailingTool.paint(level, pos, player, tool); // industrial is selected (loaded first)

        DetailingToolItem.setContents(tool, DetailingToolItem.contents(tool).withSelected(Paint.NETHERITE));
        DetailingTool.pickColour(level, pos, player, tool);
        if (DetailingToolItem.contents(tool).selected() != Paint.INDUSTRIAL) helper.fail("the eyedropper didn't pick industrial");
        helper.succeed();
    }

    /** Oritech machines take paint from the tool too, but not rainbow. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void paintsOritechMachines(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlockAndUpdate(pos, BlockContent.POWERED_FURNACE_BLOCK.defaultBlockState());
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var tool = tool(player);
        load(tool, player, new ItemStack(ModContent.RAINBOW_CARTRIDGE.get(), 2));
        load(tool, player, new ItemStack(ItemContent.DIAMOND_PAINT, 2));

        DetailingTool.paint(level, pos, player, tool); // rainbow selected: refused
        if (!(level.getBlockEntity(pos) instanceof ColorableMachine machine)) {
            helper.fail("no machine at " + pos);
            return;
        }
        if (machine.getCurrentColor() != ColorableMachine.ColorVariant.ORANGE) helper.fail("a machine was painted rainbow");
        if (DetailingToolItem.contents(tool).count(Paint.RAINBOW) != 2) helper.fail("a refused rainbow click used a charge");

        DetailingToolItem.setContents(tool, DetailingToolItem.contents(tool).withSelected(Paint.DIAMOND).withCorner(Optional.empty()));
        DetailingTool.paint(level, pos, player, tool);
        if (machine.getCurrentColor() != ColorableMachine.ColorVariant.DIAMOND) helper.fail("the machine isn't diamond");
        if (DetailingToolItem.contents(tool).count(Paint.DIAMOND) != 1) helper.fail("painting a machine should use one charge");
        helper.succeed();
    }
}
