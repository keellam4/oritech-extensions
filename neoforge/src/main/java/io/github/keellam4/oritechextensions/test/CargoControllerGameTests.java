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
        if (size != 3) helper.fail("expected 3 vaults in the network, found " + size);
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
}
