package io.github.keellam4.oritechextensions.test;

import io.github.keellam4.oritechextensions.ModContent;
import io.github.keellam4.oritechextensions.OritechExtensions;
import io.github.keellam4.oritechextensions.storage.CargoControllerBlockEntity;
import io.github.keellam4.oritechextensions.storage.CargoVaultBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The Cargo Controller: which vaults join, where items go, taking across vaults, and the terminal's totals. */
@GameTestHolder(OritechExtensions.MODID)
@PrefixGameTestTemplate(false)
public class CargoControllerGameTests {

    private static final String TEMPLATE = "empty_tall";

    private static <T> T place(GameTestHelper helper, int x, int z, Block block, Class<T> type) {
        var pos = helper.absolutePos(new BlockPos(x, 1, z));
        helper.getLevel().setBlockAndUpdate(pos, block.defaultBlockState());
        return type.cast(helper.getLevel().getBlockEntity(pos));
    }

    /** A wall of three vaults joins through each other; a vault one block away doesn't; a Fluid Vault never does. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void touchingVaultsJoin(GameTestHelper helper) {
        var controller = place(helper, 1, 2, ModContent.CARGO_CONTROLLER.get(), CargoControllerBlockEntity.class);
        place(helper, 2, 2, ModContent.CARGO_VAULT.get(), CargoVaultBlockEntity.class);
        place(helper, 3, 2, ModContent.QUAD_VAULT.get(), CargoVaultBlockEntity.class);
        place(helper, 3, 3, ModContent.COMPACTING_VAULT.get(), CargoVaultBlockEntity.class);
        place(helper, 1, 3, ModContent.FLUID_VAULT.get(), CargoVaultBlockEntity.class);
        place(helper, 5, 2, ModContent.CARGO_VAULT.get(), CargoVaultBlockEntity.class);
        controller.invalidate();
        int size = controller.network().vaults().size();
        if (size != 3) helper.fail("expected 3 item vaults in the network, found " + size);
        if (controller.network().tanks().size() != 1) helper.fail("the Fluid Vault should join as a tank");
        helper.succeed();
    }

    /** Items go to the vault already holding them, a new item type to the first vault with room. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void itemsGoWhereTheyBelong(GameTestHelper helper) {
        var controller = place(helper, 1, 2, ModContent.CARGO_CONTROLLER.get(), CargoControllerBlockEntity.class);
        var near = place(helper, 2, 2, ModContent.CARGO_VAULT.get(), CargoVaultBlockEntity.class);
        var far = place(helper, 3, 2, ModContent.CARGO_VAULT.get(), CargoVaultBlockEntity.class);
        far.insert(new ItemStack(Items.IRON_INGOT, 1), false);
        controller.invalidate();
        var network = controller.network();

        if (network.insert(new ItemStack(Items.IRON_INGOT, 20), false) != 20) helper.fail("iron didn't go in");
        if (far.count() != 21 || near.count() != 0) helper.fail("iron should join the vault holding iron (far " + far.count() + ", near " + near.count() + ")");
        network.insert(new ItemStack(Items.GOLD_INGOT, 5), false);
        if (!near.item().is(Items.GOLD_INGOT)) helper.fail("gold should go to the empty vault");
        if (network.insert(new ItemStack(Items.DIAMOND, 1), true) != 0) helper.fail("a third item type fit in two full single vaults");

        far.insert(new ItemStack(Items.IRON_INGOT, 1), false);
        var taken = network.extract(new ItemStack(Items.IRON_INGOT), 64, false);
        if (taken.getCount() != 22) helper.fail("taking iron should give all 22, gave " + taken.getCount());

        near.insert(new ItemStack(Items.GOLD_INGOT, 3), false);
        var contents = network.contents();
        if (contents.size() != 1 || contents.get(0).count() != 8 || !contents.get(0).item().is(Items.GOLD_INGOT)) {
            helper.fail("the terminal should list 8 gold, lists " + contents);
        }
        helper.succeed();
    }

    /** Fluids go to the Fluid Vault already holding them, then to an empty one, and show in the terminal. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void fluidsGoToTanks(GameTestHelper helper) {
        var controller = place(helper, 1, 2, ModContent.CARGO_CONTROLLER.get(), CargoControllerBlockEntity.class);
        var a = place(helper, 2, 2, ModContent.FLUID_VAULT.get(), io.github.keellam4.oritechextensions.storage.FluidVaultBlockEntity.class);
        var b = place(helper, 3, 2, ModContent.FLUID_VAULT.get(), io.github.keellam4.oritechextensions.storage.FluidVaultBlockEntity.class);
        b.fill(net.minecraft.world.level.material.Fluids.LAVA, 1000, false);
        controller.invalidate();
        var network = controller.network();
        network.fill(net.minecraft.world.level.material.Fluids.LAVA, 2000, false);
        network.fill(net.minecraft.world.level.material.Fluids.WATER, 4000, false);
        if (b.amount() != 3000 || a.fluid() != net.minecraft.world.level.material.Fluids.WATER) helper.fail("lava should join the lava tank, water the empty one");
        var fluids = network.contents().stream().filter(io.github.keellam4.oritechextensions.storage.CargoNetwork.Entry::isFluid).toList();
        if (fluids.size() != 2) helper.fail("the terminal should list two fluids, lists " + fluids.size());
        var drained = network.drain(net.minecraft.world.level.material.Fluids.WATER, 1000, false);
        if (drained.amount() != 1000 || a.amount() != 3000) helper.fail("draining water went wrong");
        helper.succeed();
    }

    /** Two vaults holding the same item: taking more than one holds pulls from both. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void takesAcrossVaults(GameTestHelper helper) {
        var controller = place(helper, 1, 2, ModContent.CARGO_CONTROLLER.get(), CargoControllerBlockEntity.class);
        var a = place(helper, 2, 2, ModContent.CARGO_VAULT.get(), CargoVaultBlockEntity.class);
        var b = place(helper, 3, 2, ModContent.CARGO_VAULT.get(), CargoVaultBlockEntity.class);
        a.insert(new ItemStack(Items.COAL, 40), false);
        b.insert(new ItemStack(Items.COAL, 40), false);
        controller.invalidate();
        var taken = controller.network().extract(new ItemStack(Items.COAL), 64, false);
        if (taken.getCount() != 64 || a.count() + b.count() != 16) helper.fail("took " + taken.getCount() + ", left " + (a.count() + b.count()));
        if (controller.network().contents().get(0).count() != 16) helper.fail("the total should be 16");
        helper.succeed();
    }

    /** Crafting Terminal: shift-clicking the result crafts until the network runs out, refilling the grid each time. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void craftingTerminalRefills(GameTestHelper helper) {
        var terminal = place(helper, 1, 2, ModContent.CRAFTING_TERMINAL.get(), CargoControllerBlockEntity.class);
        var vault = place(helper, 2, 2, ModContent.CARGO_VAULT.get(), CargoVaultBlockEntity.class);
        vault.insert(new ItemStack(Items.OAK_PLANKS, 10), false);
        terminal.invalidate();
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var menu = new io.github.keellam4.oritechextensions.storage.TerminalMenu(1, player.getInventory(), terminal, true);
        // grid slots start at 1: top-middle and centre make sticks
        menu.getSlot(1 + 1).set(new ItemStack(Items.OAK_PLANKS));
        menu.getSlot(1 + 4).set(new ItemStack(Items.OAK_PLANKS));
        if (!menu.getSlot(0).getItem().is(Items.STICK)) helper.fail("two planks should show sticks as the result");
        menu.clicked(0, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE, player);
        int sticks = player.getInventory().countItem(Items.STICK);
        if (sticks != 24) helper.fail("12 planks should make 24 sticks, made " + sticks);
        if (vault.count() != 0) helper.fail("the vault should be out of planks, has " + vault.count());

        vault.insert(new ItemStack(Items.OAK_PLANKS, 5), false);
        menu.getSlot(1).set(new ItemStack(Items.OAK_PLANKS, 3));
        menu.removed(player);
        if (vault.count() != 8) helper.fail("closing should return the grid to the network, vault has " + vault.count());
        helper.succeed();
    }

    /** JEI's "+": the grid fills with the recipe's shape, from the network first, then the player's inventory. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void craftingTerminalFillsFromRecipe(GameTestHelper helper) {
        var terminal = place(helper, 1, 2, ModContent.CRAFTING_TERMINAL.get(), CargoControllerBlockEntity.class);
        var vault = place(helper, 2, 2, ModContent.CARGO_VAULT.get(), CargoVaultBlockEntity.class);
        vault.insert(new ItemStack(Items.OAK_PLANKS, 5), false);
        terminal.invalidate();
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.getInventory().add(new ItemStack(Items.OAK_PLANKS, 1));
        var menu = new io.github.keellam4.oritechextensions.storage.TerminalMenu(1, player.getInventory(), terminal, true);
        menu.getSlot(1 + 4).set(new ItemStack(Items.COBBLESTONE)); // something already in the grid goes back first
        menu.fill(net.minecraft.resources.ResourceLocation.withDefaultNamespace("oak_stairs"));
        int[] planks = {0, 3, 4, 6, 7, 8};
        for (int i : planks) {
            if (!menu.getSlot(1 + i).getItem().is(Items.OAK_PLANKS)) helper.fail("grid slot " + i + " should hold a plank");
        }
        if (!menu.getSlot(0).getItem().is(Items.OAK_STAIRS)) helper.fail("the grid should show oak stairs");
        if (vault.count() != 0) helper.fail("all five planks should come from the vault, it still has " + vault.count());
        if (player.getInventory().countItem(Items.COBBLESTONE) != 1) helper.fail("the cobblestone the vault can't take should go to the inventory");
        if (player.getInventory().countItem(Items.OAK_PLANKS) != 0) helper.fail("the sixth plank should come from the inventory");
        helper.succeed();
    }
}
