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

    /** A Dual Vault has two windows with half the space each. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void dualHasTwoHalves(GameTestHelper helper) {
        var vault = place(helper, new BlockPos(2, 1, 2), ModContent.DUAL_VAULT.get());
        if (vault.slots() != 2) helper.fail("a Dual Vault should have 2 slots, has " + vault.slots());
        int perSlot = Math.max(1, Config.cargoVaultStacks() / 2) * 64;
        if (vault.capacity(1) != perSlot) helper.fail("slot capacity " + vault.capacity(1) + ", expected " + perSlot);
        vault.insert(new ItemStack(Items.OAK_LOG, 3), false);
        vault.insert(new ItemStack(Items.SPRUCE_LOG, 4), false);
        if (vault.count(0) != 3 || vault.count(1) != 4) helper.fail("the two logs didn't get a window each");
        if (vault.insert(new ItemStack(Items.BIRCH_LOG, 1), true) != 0) helper.fail("a third item type fit");
        helper.succeed();
    }

    /** A Compacting Vault stores iron once and hands it out as blocks, ingots or nuggets. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void compactingConvertsForms(GameTestHelper helper) {
        var level = helper.getLevel();
        var vault = place(helper, new BlockPos(2, 1, 2), ModContent.COMPACTING_VAULT.get());
        if (vault.insert(new ItemStack(Items.IRON_INGOT, 20), false) != 20) {
            helper.fail("the Compacting Vault didn't take 20 iron ingots");
            return;
        }
        if (!vault.item(0).is(Items.IRON_BLOCK) || !vault.item(1).is(Items.IRON_INGOT) || !vault.item(2).is(Items.IRON_NUGGET)) {
            helper.fail("forms should be block, ingot, nugget; are " + vault.item(0) + ", " + vault.item(1) + ", " + vault.item(2));
            return;
        }
        if (vault.count(0) != 2 || vault.count(1) != 20 || vault.count(2) != 180) helper.fail("20 ingots should read 2 / 20 / 180, read "
                + vault.count(0) + " / " + vault.count(1) + " / " + vault.count(2));

        var block = vault.extract(0, 1, false);
        if (!block.is(Items.IRON_BLOCK) || block.getCount() != 1) helper.fail("taking a block gave " + block);
        if (vault.count(1) != 11 || vault.count(2) != 99) helper.fail("after taking a block: " + vault.count(1) + " ingots, " + vault.count(2) + " nuggets");
        vault.insert(new ItemStack(Items.IRON_NUGGET, 5), false);
        if (vault.count(2) != 104 || vault.count(1) != 11) helper.fail("nuggets didn't join the stock");
        if (vault.insert(new ItemStack(Items.GOLD_INGOT, 1), true) != 0) helper.fail("took a different item");

        // Breaking and placing it again keeps the exact stock.
        var pos = vault.getBlockPos();
        level.destroyBlock(pos, true);
        var dropped = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2)).stream()
                .map(ItemEntity::getItem).filter(s -> s.is(ModContent.COMPACTING_VAULT_ITEM.get())).findFirst();
        if (dropped.isEmpty()) {
            helper.fail("the Compacting Vault didn't drop");
            return;
        }
        var again = place(helper, new BlockPos(2, 1, 4), ModContent.COMPACTING_VAULT.get());
        again.applyComponentsFromItemStack(dropped.get());
        if (again.count(2) != 104 || !again.item(0).is(Items.IRON_BLOCK)) helper.fail("the placed vault holds " + again.count(2) + " nuggets");

        // An item with no compacting recipes is kept as it is.
        var plain = place(helper, new BlockPos(4, 1, 2), ModContent.COMPACTING_VAULT.get());
        plain.insert(new ItemStack(Items.FLINT, 7), false);
        if (!plain.item(0).is(Items.FLINT) || plain.count(0) != 7 || !plain.item(1).isEmpty()) helper.fail("flint should sit alone in the top window");
        helper.succeed();
    }

    /** A Dual Compacting Vault keeps two forms: iron ingots and blocks, no nuggets. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void dualCompactingKeepsTwoForms(GameTestHelper helper) {
        var vault = place(helper, new BlockPos(2, 1, 2), ModContent.DUAL_COMPACTING_VAULT.get());
        if (vault.slots() != 2) helper.fail("a Dual Compacting Vault should have 2 slots, has " + vault.slots());
        vault.insert(new ItemStack(Items.IRON_INGOT, 20), false);
        if (!vault.item(0).is(Items.IRON_BLOCK) || !vault.item(1).is(Items.IRON_INGOT)) {
            helper.fail("forms should be block and ingot; are " + vault.item(0) + ", " + vault.item(1));
            return;
        }
        if (vault.count(0) != 2 || vault.count(1) != 20) helper.fail("20 ingots should read 2 / 20");
        if (vault.insert(new ItemStack(Items.IRON_NUGGET, 1), true) != 0) helper.fail("took nuggets, which it has no window for");
        helper.succeed();
    }

    /** Clicks map to windows as seen from the front: top left is slot 0, bottom right the last one. */
    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public static void frontClicksPickWindows(GameTestHelper helper) {
        var pos = new BlockPos(10, 64, 10);
        var north = net.minecraft.core.Direction.NORTH;
        // Facing north: the front is the z = 10 face, and the viewer's right is west (towards smaller x).
        int topLeft = io.github.keellam4.oritechextensions.storage.VaultLayout.QUAD.slotAt(north, pos, new net.minecraft.world.phys.Vec3(10.9, 64.9, 10), north);
        int bottomRight = io.github.keellam4.oritechextensions.storage.VaultLayout.QUAD.slotAt(north, pos, new net.minecraft.world.phys.Vec3(10.1, 64.1, 10), north);
        int gridSecond = io.github.keellam4.oritechextensions.storage.VaultLayout.GRID.slotAt(north, pos, new net.minecraft.world.phys.Vec3(10.6, 64.9, 10), north);
        int side = io.github.keellam4.oritechextensions.storage.VaultLayout.QUAD.slotAt(north, pos, new net.minecraft.world.phys.Vec3(11, 64.5, 10.5), net.minecraft.core.Direction.EAST);
        if (topLeft != 0) helper.fail("top left is slot " + topLeft);
        if (bottomRight != 3) helper.fail("bottom right is slot " + bottomRight);
        if (gridSecond != 1) helper.fail("second grid window is slot " + gridSecond);
        if (side != -1) helper.fail("a click on the side picked slot " + side);
        int dualBottom = io.github.keellam4.oritechextensions.storage.VaultLayout.DUAL.slotAt(north, pos, new net.minecraft.world.phys.Vec3(10.5, 64.2, 10), north);
        int compactingRight = io.github.keellam4.oritechextensions.storage.VaultLayout.COMPACTING.slotAt(north, pos, new net.minecraft.world.phys.Vec3(10.2, 64.2, 10), north);
        if (dualBottom != 1) helper.fail("the Dual Vault's bottom window is slot " + dualBottom);
        if (compactingRight != 2) helper.fail("the Compacting Vault's bottom right window is slot " + compactingRight);
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

    /** Oritech's item pipes find a vault through Oritech's item API, see its contents by slot and pull from it. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void oritechPipesCanExtract(GameTestHelper helper) {
        var pos = helper.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1));
        helper.getLevel().setBlockAndUpdate(pos, ModContent.CARGO_VAULT.get().defaultBlockState());
        var vault = (CargoVaultBlockEntity) helper.getLevel().getBlockEntity(pos);
        vault.insert(new ItemStack(net.minecraft.world.item.Items.ICE, 100), false);
        var storage = rearth.oritech.api.item.ItemApi.BLOCK.find(helper.getLevel(), pos, net.minecraft.core.Direction.DOWN);
        if (storage == null || !storage.supportsExtraction()) helper.fail("Oritech's item API should find the vault and allow extracting");
        if (storage.getSlotCount() < 1) helper.fail("Oritech's pipes only pull from slots: the vault shows " + storage.getSlotCount());
        var shown = storage.getStackInSlot(0);
        if (!shown.is(net.minecraft.world.item.Items.ICE)) helper.fail("slot 0 should show ice, shows " + shown);
        int pulled = storage.extractFromSlot(new ItemStack(net.minecraft.world.item.Items.ICE, 16), 0, false);
        if (pulled != 16 || vault.count() != 84) helper.fail("a pipe pulling 16 should leave 84, pulled " + pulled + ", left " + vault.count());
        helper.succeed();
    }

    /**
     * JNI-93: an empty vault can be locked; then pipes put nothing in, a player's first item by hand becomes its
     * type, and Clear forgets the type at 0 items while staying locked. Same for Compacting and Fluid Vaults.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void lockedEmptyVaultTakesTypeByHand(GameTestHelper helper) {
        var vault = place(helper, new BlockPos(2, 1, 2), ModContent.CARGO_VAULT.get());
        vault.setLocked(true);
        if (!vault.isLocked()) helper.fail("an empty vault should lock");
        if (vault.insert(new ItemStack(Items.COBBLESTONE, 8), false) != 0) helper.fail("pipes shouldn't fill a locked empty vault");
        if (vault.byHand(() -> vault.insert(new ItemStack(Items.IRON_INGOT, 8), false)) != 8) helper.fail("the first item by hand should go in");
        if (vault.insert(new ItemStack(Items.IRON_INGOT, 2), false) != 2) helper.fail("pipes can add more of the locked type");
        vault.extract(0, 10, false);
        if (!vault.item().is(Items.IRON_INGOT)) helper.fail("emptied, it should stay locked to iron");
        if (!vault.canClearTypes()) helper.fail("Clear should be available on a locked empty vault");
        vault.clearTypes();
        if (!vault.item().isEmpty() || !vault.isLocked()) helper.fail("Clear should forget iron and stay locked");
        if (vault.insert(new ItemStack(Items.COBBLESTONE, 1), false) != 0) helper.fail("after Clear, pipes still put nothing in");
        if (vault.byHand(() -> vault.insert(new ItemStack(Items.GOLD_INGOT, 1), false)) != 1) helper.fail("after Clear, a new item by hand sets the type");
        if (vault.canClearTypes()) helper.fail("Clear shouldn't work while items are in");

        var compacting = place(helper, new BlockPos(4, 1, 2), ModContent.COMPACTING_VAULT.get());
        compacting.setLocked(true);
        if (compacting.insert(new ItemStack(Items.IRON_INGOT, 9), false) != 0) helper.fail("a locked empty Compacting Vault refuses pipes");
        if (compacting.byHand(() -> compacting.insert(new ItemStack(Items.IRON_INGOT, 9), false)) != 9) helper.fail("by hand it takes iron");
        compacting.extract(0, 64, false);
        compacting.extract(1, 64, false);
        compacting.extract(2, 64, false);
        if (!compacting.canClearTypes()) helper.fail("an emptied locked Compacting Vault can be cleared");
        compacting.clearTypes();
        if (compacting.hasAnyItemType()) helper.fail("Clear should forget the compacting chain");

        var tank = (io.github.keellam4.oritechextensions.storage.FluidVaultBlockEntity) place(helper, new BlockPos(2, 1, 4), ModContent.FLUID_VAULT.get());
        tank.setLocked(true);
        if (tank.fill(net.minecraft.world.level.material.Fluids.WATER, 1000, false) != 0) helper.fail("a locked empty Fluid Vault refuses pipes");
        if (tank.byHand(() -> tank.fill(net.minecraft.world.level.material.Fluids.LAVA, 1000, false)) != 1000) helper.fail("by hand it takes lava");
        tank.drain(null, 1000, false);
        tank.clearTypes();
        if (tank.fluid() != net.minecraft.world.level.material.Fluids.EMPTY || !tank.isLocked()) helper.fail("Clear should forget lava and stay locked");
        helper.succeed();
    }
}
