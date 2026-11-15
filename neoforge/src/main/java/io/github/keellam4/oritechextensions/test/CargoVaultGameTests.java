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
        if (contents == null || contents.count() != 50 || !contents.item().is(Items.REDSTONE)) helper.fail("the dropped vault lost its items: " + contents);
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
