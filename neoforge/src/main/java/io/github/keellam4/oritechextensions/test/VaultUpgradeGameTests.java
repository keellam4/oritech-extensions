package io.github.keellam4.oritechextensions.test;

import io.github.keellam4.oritechextensions.Config;
import io.github.keellam4.oritechextensions.ModContent;
import io.github.keellam4.oritechextensions.OritechExtensions;
import io.github.keellam4.oritechextensions.storage.CargoVaultBlock;
import io.github.keellam4.oritechextensions.storage.CargoVaultBlockEntity;
import io.github.keellam4.oritechextensions.storage.VaultUpgrade;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Vault upgrades: capacity tiers add up, Void, Pull, Push and Redstone. */
@GameTestHolder(OritechExtensions.MODID)
@PrefixGameTestTemplate(false)
public class VaultUpgradeGameTests {

    private static final String TEMPLATE = "empty_tall";

    /** A vault facing north at (2, 1, 2): the inventory behind it is at (2, 1, 3). */
    private static CargoVaultBlockEntity vault(GameTestHelper helper) {
        var pos = helper.absolutePos(new BlockPos(2, 1, 2));
        helper.getLevel().setBlockAndUpdate(pos, ModContent.CARGO_VAULT.get().defaultBlockState());
        return (CargoVaultBlockEntity) helper.getLevel().getBlockEntity(pos);
    }

    private static void install(CargoVaultBlockEntity vault, int slot, VaultUpgrade upgrade) {
        vault.upgrades().setItem(slot, new ItemStack(ModContent.VAULT_UPGRADES.get(upgrade).get()));
    }

    private static ChestBlockEntity chestBehind(GameTestHelper helper) {
        var pos = helper.absolutePos(new BlockPos(2, 1, 3));
        helper.getLevel().setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
        return (ChestBlockEntity) helper.getLevel().getBlockEntity(pos);
    }

    /** Capacity upgrades add up, and one can't come out while the vault holds more than it could hold without it. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void capacityUpgradesAddUp(GameTestHelper helper) {
        var vault = vault(helper);
        int base = Config.cargoVaultStacks() * 64;
        install(vault, 0, VaultUpgrade.STEEL_CAPACITY);
        install(vault, 1, VaultUpgrade.ELECTRUM_CAPACITY);
        if (vault.multiplier() != 6) helper.fail("Steel (×2) and Electrum (×4) should make ×6, made ×" + vault.multiplier());
        if (vault.capacity() != base * 6) helper.fail("capacity " + vault.capacity() + ", expected " + base * 6);

        vault.setContents(new ItemStack(Items.COBBLESTONE), base * 3);
        if (vault.canRemoveUpgrade(1)) helper.fail("removing Electrum would leave too little room, but was allowed");
        if (!vault.canRemoveUpgrade(0)) helper.fail("removing Steel still leaves room (×4), but was refused");
        helper.succeed();
    }

    /** With Void, a full vault keeps taking its item and deletes it; other items still don't fit. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void voidDeletesOverflow(GameTestHelper helper) {
        var vault = vault(helper);
        install(vault, 0, VaultUpgrade.VOID);
        vault.setContents(new ItemStack(Items.SAND), vault.capacity(0, new ItemStack(Items.SAND)));
        int full = vault.count();
        if (vault.insert(new ItemStack(Items.SAND, 64), false) != 64) helper.fail("a full Void vault refused more sand");
        if (vault.count() != full) helper.fail("the count went over capacity");
        if (vault.insert(new ItemStack(Items.GRAVEL, 1), false) != 0) helper.fail("a Void vault took an item it doesn't hold");
        helper.succeed();
    }

    /** Push moves the vault's items into the chest behind it. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void pushFillsChestBehind(GameTestHelper helper) {
        var vault = vault(helper);
        var chest = chestBehind(helper);
        vault.insert(new ItemStack(Items.IRON_INGOT, 10), false);
        install(vault, 0, VaultUpgrade.PUSH);
        helper.succeedWhen(() -> {
            if (!chest.getItem(0).is(Items.IRON_INGOT) || vault.count() != 0) helper.fail("iron not pushed yet");
        });
    }

    /** Pull takes the vault's item out of the chest behind it, and leaves other items there. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void pullEmptiesChestBehind(GameTestHelper helper) {
        var vault = vault(helper);
        var chest = chestBehind(helper);
        vault.insert(new ItemStack(Items.GOLD_INGOT, 1), false);
        chest.setItem(0, new ItemStack(Items.GOLD_INGOT, 5));
        chest.setItem(1, new ItemStack(Items.DIRT, 5));
        install(vault, 0, VaultUpgrade.PULL);
        helper.succeedWhen(() -> {
            if (vault.count() != 6) helper.fail("gold not pulled yet");
            if (!chest.getItem(1).is(Items.DIRT)) helper.fail("pulled dirt, which the vault doesn't hold");
        });
    }

    /** Redstone: the vault becomes a signal source as strong as it is full. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void redstoneGivesSignal(GameTestHelper helper) {
        var vault = vault(helper);
        var level = helper.getLevel();
        var pos = vault.getBlockPos();
        vault.insert(new ItemStack(Items.STONE, 1), false);
        install(vault, 0, VaultUpgrade.REDSTONE);
        var state = level.getBlockState(pos);
        if (!state.getValue(CargoVaultBlock.REDSTONE)) helper.fail("the block didn't switch to giving a signal");
        if (state.getSignal(level, pos, Direction.NORTH) != 1) helper.fail("one item should give signal 1, gave " + state.getSignal(level, pos, Direction.NORTH));
        vault.upgrades().setItem(0, ItemStack.EMPTY);
        if (level.getBlockState(pos).getValue(CargoVaultBlock.REDSTONE)) helper.fail("still a signal source after removing the upgrade");
        helper.succeed();
    }
}
