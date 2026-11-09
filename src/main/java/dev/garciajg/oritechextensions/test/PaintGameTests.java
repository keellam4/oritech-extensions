package dev.garciajg.oritechextensions.test;

import dev.garciajg.oritechextensions.ModContent;
import dev.garciajg.oritechextensions.OritechExtensions;
import dev.garciajg.oritechextensions.paint.PaintEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rearth.oritech.init.BlockContent;
import rearth.oritech.init.ItemContent;
import rearth.oritech.util.ColorableMachine.ColorVariant;

/** Painting machine addons with Oritech's Color Cartridges (server side). */
@GameTestHolder(OritechExtensions.MODID)
@PrefixGameTestTemplate(false)
public class PaintGameTests {

    private static final String TEMPLATE = "empty_tall";

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void paintOritechAddon(GameTestHelper helper) {
        paintCycle(helper, BlockContent.MACHINE_SPEED_ADDON);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void paintOritechThingsAddon(GameTestHelper helper) {
        if (!ModList.get().isLoaded("oritechthings")) {
            helper.succeed(); // only meaningful when Oritech Things is present (it is in CI)
            return;
        }
        var id = ResourceLocation.fromNamespaceAndPath("oritechthings", "addon_block_speed_tier_5");
        if (!BuiltInRegistries.BLOCK.containsKey(id)) {
            helper.fail("Oritech Things is loaded but " + id + " doesn't exist");
            return;
        }
        paintCycle(helper, BuiltInRegistries.BLOCK.get(id));
    }

    /** Machines Oritech paints itself are left to Oritech. */
    @GameTest(template = TEMPLATE)
    public static void ignoresNonAddons(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var paint = new ItemStack(ItemContent.WHITE_PAINT, 4);
        player.setItemInHand(InteractionHand.MAIN_HAND, paint);
        if (PaintEvents.tryPaint(level, pos, player, InteractionHand.MAIN_HAND, paint)) helper.fail("painted plain stone");
        if (paint.getCount() != 4) helper.fail("used paint on a block that can't be painted");
        helper.succeed();
    }

    private static void paintCycle(GameTestHelper helper, Block addon) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlockAndUpdate(pos.below(), Blocks.IRON_BLOCK.defaultBlockState());
        var state = addon.defaultBlockState();
        if (state.hasProperty(BlockStateProperties.ATTACH_FACE)) state = state.setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR);
        level.setBlockAndUpdate(pos, state);

        if (!PaintEvents.isPaintable(level.getBlockEntity(pos))) {
            helper.fail(BuiltInRegistries.BLOCK.getKey(addon) + " should be paintable");
            return;
        }

        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var paint = new ItemStack(ItemContent.WHITE_PAINT, 4);
        player.setItemInHand(InteractionHand.MAIN_HAND, paint);

        // 1. Paint it white: uses one cartridge.
        if (!PaintEvents.tryPaint(level, pos, player, InteractionHand.MAIN_HAND, paint)) helper.fail("painting was refused");
        if (PaintEvents.getPaint(level.getBlockEntity(pos)) != ColorVariant.WHITE) helper.fail("block isn't white after painting");
        if (player.getMainHandItem().getCount() != 3) helper.fail("expected 3 cartridges left, have " + player.getMainHandItem().getCount());

        // 2. Same colour again: nothing happens, nothing used.
        if (PaintEvents.tryPaint(level, pos, player, InteractionHand.MAIN_HAND, player.getMainHandItem())) helper.fail("repainted with the same colour");
        if (player.getMainHandItem().getCount() != 3) helper.fail("used a cartridge on a block that already had that colour");

        // 3. Break it: the dropped item remembers the colour.
        level.destroyBlock(pos, true);
        var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2));
        var dropped = drops.stream().map(ItemEntity::getItem).filter(s -> s.is(addon.asItem())).findFirst();
        if (dropped.isEmpty()) {
            helper.fail("addon didn't drop");
            return;
        }
        if (dropped.get().get(ModContent.PAINT_COMPONENT.get()) != ColorVariant.WHITE) helper.fail("dropped item lost its paint");

        // 4. Place it again: the block is white again.
        PaintEvents.rememberHeldPaint(player, dropped.get(), level.getGameTime());
        level.setBlockAndUpdate(pos, state);
        PaintEvents.applyRememberedPaint(level, pos, player);
        if (PaintEvents.getPaint(level.getBlockEntity(pos)) != ColorVariant.WHITE) helper.fail("placed addon isn't white");

        // 5. Orange (Oritech's default) removes the paint.
        var orange = new ItemStack(ItemContent.ORANGE_PAINT);
        player.setItemInHand(InteractionHand.MAIN_HAND, orange);
        if (!PaintEvents.tryPaint(level, pos, player, InteractionHand.MAIN_HAND, orange)) helper.fail("orange paint was refused");
        if (level.getBlockEntity(pos).hasData(ModContent.PAINT)) helper.fail("orange paint should remove the stored colour");

        helper.succeed();
    }
}
