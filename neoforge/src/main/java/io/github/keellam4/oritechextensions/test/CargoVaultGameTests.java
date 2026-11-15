package io.github.keellam4.oritechextensions.test;

import io.github.keellam4.oritechextensions.Config;
import io.github.keellam4.oritechextensions.ModContent;
import io.github.keellam4.oritechextensions.OritechExtensions;
import io.github.keellam4.oritechextensions.paint.Paint;
import io.github.keellam4.oritechextensions.paint.PaintTargets;
import io.github.keellam4.oritechextensions.paint.PipePaintData;
import io.github.keellam4.oritechextensions.storage.CargoVaultBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rearth.oritech.init.ItemContent;

/** The Cargo Vault: storage rules, the lock, keeping contents and paint on the item, and painting a wall at once. */
@GameTestHolder(OritechExtensions.MODID)
@PrefixGameTestTemplate(false)
public class CargoVaultGameTests {

    private static final String TEMPLATE = "empty_tall";

    private static CargoVaultBlockEntity vault(GameTestHelper helper, BlockPos relative) {
        var pos = helper.absolutePos(relative);
        helper.getLevel().setBlockAndUpdate(pos, ModContent.CARGO_VAULT.get().defaultBlockState());
        return (CargoVaultBlockEntity) helper.getLevel().getBlockEntity(pos);
    }

    /** One item type, up to the configured number of stacks; an empty vault forgets its type unless locked. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void storesOneItemTypeUpToCapacity(GameTestHelper helper) {
        var vault = vault(helper, new BlockPos(2, 1, 2));
        int capacity = Config.cargoVaultStacks() * 64;

        if (vault.insert(new ItemStack(Items.IRON_INGOT, 64), false) != 64) helper.fail("didn't take 64 iron");
        if (vault.insert(new ItemStack(Items.GOLD_INGOT, 1), true) != 0) helper.fail("took a second item type");
        vault.setContents(vault.item(), capacity - 10);
        if (vault.insert(new ItemStack(Items.IRON_INGOT, 64), false) != 10) helper.fail("went over capacity");
        if (vault.capacity() != capacity) helper.fail("capacity " + vault.capacity() + ", expected " + capacity);

        var out = vault.extract(500, false);
        if (out.getCount() != 64 || !out.is(Items.IRON_INGOT)) helper.fail("extract should give one stack, gave " + out);

        vault.setContents(vault.item(), 1);
        vault.setLocked(true);
        vault.extract(1, false);
        if (!vault.item().is(Items.IRON_INGOT)) helper.fail("a locked vault forgot its item");
        if (vault.insert(new ItemStack(Items.GOLD_INGOT, 1), true) != 0) helper.fail("a locked vault took another item");
        vault.setLocked(false);
        if (!vault.item().isEmpty()) helper.fail("an unlocked empty vault kept its item");
        if (vault.insert(new ItemStack(Items.GOLD_INGOT, 3), false) != 3) helper.fail("an empty vault didn't take a new item");
        helper.succeed();
    }

    /** Breaking a vault drops it with its items and its paint. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void keepsContentsAndPaintWhenBroken(GameTestHelper helper) {
        var level = helper.getLevel();
        var vault = vault(helper, new BlockPos(2, 1, 2));
        var pos = vault.getBlockPos();
        vault.insert(new ItemStack(Items.REDSTONE, 50), false);

        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var paint = new ItemStack(ItemContent.CAMO_PAINT, 1);
        player.setItemInHand(InteractionHand.MAIN_HAND, paint);
        if (!PaintTargets.tryPaint(level, pos, player, InteractionHand.MAIN_HAND, paint)) {
            helper.fail("painting a vault was refused");
            return;
        }

        level.destroyBlock(pos, true);
        var dropped = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2)).stream()
                .map(ItemEntity::getItem).filter(s -> s.is(ModContent.CARGO_VAULT_ITEM.get())).findFirst();
        if (dropped.isEmpty()) {
            helper.fail("the vault didn't drop");
            return;
        }
        var contents = dropped.get().get(ModContent.VAULT_CONTENTS.get());
        if (contents == null || contents.slot(0).count() != 50 || !contents.slot(0).item().is(Items.REDSTONE)) helper.fail("the dropped vault lost its items: " + contents);
        if (dropped.get().get(ModContent.PAINT_COMPONENT.get()) != Paint.CAMO) helper.fail("the dropped vault lost its paint");

        // Placing it again brings both back.
        var again = vault(helper, new BlockPos(2, 1, 4));
        again.applyComponentsFromItemStack(dropped.get());
        PaintTargets.onBlockPlaced(level, again.getBlockPos());
        if (again.count() != 50) helper.fail("the placed vault didn't get its items back");
        if (PipePaintData.get(level).getOrDefault(again.getBlockPos()) != Paint.CAMO) helper.fail("the placed vault didn't get its paint back");
        helper.succeed();
    }

    /** Touching vaults are a Cargo Wall: one cartridge paints all of them. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void wallPaintsTogether(GameTestHelper helper) {
        var level = helper.getLevel();
        var a = vault(helper, new BlockPos(1, 1, 2)).getBlockPos();
        var b = vault(helper, new BlockPos(2, 1, 2)).getBlockPos();
        var c = vault(helper, new BlockPos(2, 2, 2)).getBlockPos();
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var paint = new ItemStack(ItemContent.SCULK_PAINT, 1);
        player.setItemInHand(InteractionHand.MAIN_HAND, paint);
        PaintTargets.tryPaint(level, a, player, InteractionHand.MAIN_HAND, paint);
        var data = PipePaintData.get(level);
        for (var pos : new BlockPos[]{a, b, c}) {
            if (data.getOrDefault(pos) != Paint.SCULK) helper.fail("vault at " + pos + " wasn't painted with its wall");
        }
        helper.succeed();
    }

    private static CargoVaultBlockEntity place(GameTestHelper helper, BlockPos relative, net.minecraft.world.level.block.Block block) {
        var pos = helper.absolutePos(relative);
        helper.getLevel().setBlockAndUpdate(pos, block.defaultBlockState());
        return (CargoVaultBlockEntity) helper.getLevel().getBlockEntity(pos);
    }

    /** A Quad Vault keeps each item type in its own window, a quarter of the space each. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void quadKeepsItemTypesApart(GameTestHelper helper) {
        var vault = place(helper, new BlockPos(2, 1, 2), ModContent.QUAD_VAULT.get());
        if (vault.slots() != 4) helper.fail("a Quad Vault should have 4 slots, has " + vault.slots());
        int perSlot = Math.max(1, Config.cargoVaultStacks() / 4) * 64;
        if (vault.capacity(0) != perSlot) helper.fail("slot capacity " + vault.capacity(0) + ", expected " + perSlot);

        vault.insert(new ItemStack(Items.IRON_INGOT, 10), false);
        vault.insert(new ItemStack(Items.GOLD_INGOT, 10), false);
        if (!vault.item(0).is(Items.IRON_INGOT) || !vault.item(1).is(Items.GOLD_INGOT)) helper.fail("items didn't go to the first empty windows");
        if (vault.insert(2, new ItemStack(Items.IRON_INGOT, 5), false) != 0) helper.fail("iron went into a second window");
        vault.insert(new ItemStack(Items.IRON_INGOT, 5), false);
        if (vault.count(0) != 15) helper.fail("more iron didn't join the iron window");
        if (vault.insert(3, new ItemStack(Items.DIAMOND, 1), false) != 1 || !vault.item(3).is(Items.DIAMOND)) helper.fail("a clicked empty window didn't take a new item");

        vault.setLocked(true);
        if (vault.insert(new ItemStack(Items.EMERALD, 1), true) != 0) helper.fail("a locked vault took a new item type");
        helper.succeed();
    }

    /** A Grid Vault has 16 windows; a 17th item type doesn't fit. Breaking it keeps every window. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void gridHoldsSixteenTypes(GameTestHelper helper) {
        var level = helper.getLevel();
        var vault = place(helper, new BlockPos(2, 1, 2), ModContent.GRID_VAULT.get());
        var types = new net.minecraft.world.item.Item[]{Items.STONE, Items.DIRT, Items.SAND, Items.GRAVEL, Items.OAK_LOG, Items.BIRCH_LOG,
                Items.IRON_INGOT, Items.GOLD_INGOT, Items.COPPER_INGOT, Items.COAL, Items.REDSTONE, Items.LAPIS_LAZULI,
                Items.DIAMOND, Items.EMERALD, Items.QUARTZ, Items.FLINT};
        for (int i = 0; i < types.length; i++) {
            if (vault.insert(new ItemStack(types[i], i + 1), false) != i + 1) helper.fail("window " + i + " didn't take " + types[i]);
        }
        if (vault.insert(new ItemStack(Items.CLAY_BALL, 1), true) != 0) helper.fail("a 17th item type fit");

        var pos = vault.getBlockPos();
        level.destroyBlock(pos, true);
        var dropped = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2)).stream()
                .map(ItemEntity::getItem).filter(s -> s.is(ModContent.GRID_VAULT_ITEM.get())).findFirst();
        if (dropped.isEmpty()) {
            helper.fail("the Grid Vault didn't drop");
            return;
        }
        var contents = dropped.get().get(ModContent.VAULT_CONTENTS.get());
        if (contents == null || contents.slots().size() != 16 || contents.slot(15).count() != 16 || !contents.slot(15).item().is(Items.FLINT)) {
            helper.fail("the dropped Grid Vault lost its windows: " + contents);
        }
        helper.succeed();
    }

    /** Clicks map to windows as seen from the front: top left is slot 0, bottom right the last one. */
    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public static void frontClicksPickWindows(GameTestHelper helper) {
        var pos = new BlockPos(10, 64, 10);
        var north = net.minecraft.core.Direction.NORTH;
        // Facing north: the front is the z = 10 face, and the viewer's right is west (towards smaller x).
        int topLeft = io.github.keellam4.oritechextensions.storage.VaultLayout.slotAt(north, 2, pos, new net.minecraft.world.phys.Vec3(10.9, 64.9, 10), north);
        int bottomRight = io.github.keellam4.oritechextensions.storage.VaultLayout.slotAt(north, 2, pos, new net.minecraft.world.phys.Vec3(10.1, 64.1, 10), north);
        int gridSecond = io.github.keellam4.oritechextensions.storage.VaultLayout.slotAt(north, 4, pos, new net.minecraft.world.phys.Vec3(10.6, 64.9, 10), north);
        int side = io.github.keellam4.oritechextensions.storage.VaultLayout.slotAt(north, 2, pos, new net.minecraft.world.phys.Vec3(11, 64.5, 10.5), net.minecraft.core.Direction.EAST);
        if (topLeft != 0) helper.fail("top left is slot " + topLeft);
        if (bottomRight != 3) helper.fail("bottom right is slot " + bottomRight);
        if (gridSecond != 1) helper.fail("second grid window is slot " + gridSecond);
        if (side != -1) helper.fail("a click on the side picked slot " + side);
        helper.succeed();
    }

    /** Comparators read how full the vault is. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void comparatorReadsFill(GameTestHelper helper) {
        var level = helper.getLevel();
        var vault = vault(helper, new BlockPos(2, 1, 2));
        var pos = vault.getBlockPos();
        var state = level.getBlockState(pos);
        if (state.getAnalogOutputSignal(level, pos) != 0) helper.fail("an empty vault should give 0");
        vault.insert(new ItemStack(Items.COBBLESTONE, 1), false);
        if (state.getAnalogOutputSignal(level, pos) != 1) helper.fail("one item should give 1");
        vault.setContents(vault.item(), vault.capacity());
        if (state.getAnalogOutputSignal(level, pos) != 15) helper.fail("a full vault should give 15");
        helper.succeed();
    }
}
