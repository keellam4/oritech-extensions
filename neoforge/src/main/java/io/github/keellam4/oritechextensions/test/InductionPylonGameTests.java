package io.github.keellam4.oritechextensions.test;

import io.github.keellam4.oritechextensions.ModContent;
import io.github.keellam4.oritechextensions.OritechExtensions;
import io.github.keellam4.oritechextensions.platform.Platform;
import io.github.keellam4.oritechextensions.wireless.InductionPylonBlock;
import io.github.keellam4.oritechextensions.wireless.InductionPylonBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The Induction Pylon: two halves, taking energy, charging a player's battery, and costing more further away. */
@GameTestHolder(OritechExtensions.MODID)
@PrefixGameTestTemplate(false)
public class InductionPylonGameTests {

    private static final String TEMPLATE = "empty_tall";

    private static InductionPylonBlockEntity place(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModContent.INDUCTION_PYLON.get().defaultBlockState());
        helper.setBlock(pos.above(), ModContent.INDUCTION_PYLON.get().defaultBlockState().setValue(InductionPylonBlock.HALF, DoubleBlockHalf.UPPER));
        return (InductionPylonBlockEntity) helper.getBlockEntity(pos);
    }

    private static ItemStack battery() {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("oritech", "jetpack")));
    }

    /** A filled pylon charges a battery in a nearby player's inventory, and loses at least as much as it gave. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void chargesABattery(GameTestHelper helper) {
        var pylon = place(helper, new BlockPos(2, 1, 2));
        if (pylon.receive(500_000, false) != 500_000) helper.fail("the pylon should take energy in");
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(4.5, 1, 2.5)));
        var battery = battery();
        if (Platform.INSTANCE.itemEnergy(battery) < 0) helper.fail("Oritech's jetpack should hold energy");
        player.getInventory().setItem(5, battery);
        long cost = pylon.charge(player, 20_000);
        long stored = Platform.INSTANCE.itemEnergy(player.getInventory().getItem(5));
        if (stored <= 0) helper.fail("the battery should be charged");
        if (cost < stored) helper.fail("charging can't create energy: gave " + stored + " for " + cost);
        if (pylon.energy() != 500_000 - cost) helper.fail("the pylon should lose what it spent");
        helper.succeed();
    }

    /** Charging is 100% efficient at the pylon and 50% at the edge of its range. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void efficiencyFallsWithDistance(GameTestHelper helper) {
        var pylon = place(helper, new BlockPos(2, 1, 2));
        if (Math.abs(pylon.efficiency(0) - 1.0) > 1e-9) helper.fail("full efficiency at the pylon");
        if (Math.abs(pylon.efficiency(pylon.range()) - 0.5) > 1e-9) helper.fail("half efficiency at the edge");
        if (Math.abs(pylon.efficiency(pylon.range() * 3) - 0.5) > 1e-9) helper.fail("never below half");
        helper.succeed();
    }

    /** Breaking the top half takes the bottom with it. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void halvesBreakTogether(GameTestHelper helper) {
        var pos = new BlockPos(2, 1, 2);
        place(helper, pos);
        helper.destroyBlock(pos.above());
        helper.succeedWhen(() -> helper.assertBlockNotPresent(ModContent.INDUCTION_PYLON.get(), pos));
    }
}
