package io.github.keellam4.oritechextensions.test;

import io.github.keellam4.oritechextensions.ModContent;
import io.github.keellam4.oritechextensions.OritechExtensions;
import io.github.keellam4.oritechextensions.storage.CargoControllerBlockEntity;
import io.github.keellam4.oritechextensions.storage.SiloControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The Fluid Silo: forming, capacity, keeping fluid when broken, and joining a Cargo network. */
@GameTestHolder(OritechExtensions.MODID)
@PrefixGameTestTemplate(false)
public class FluidSiloGameTests {

    private static final String TEMPLATE = "empty_tall";

    /** Builds a 3 × 3 silo {@code height} tall at (1, 1, 1) with glass in the middle of the front, controller front-middle. */
    private static SiloControllerBlockEntity build(GameTestHelper helper, int height) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < 3; x++) {
                for (int z = 0; z < 3; z++) {
                    var block = x == 1 && z == 0 && y > 0 ? ModContent.SILO_GLASS.get() : ModContent.SILO_CASING.get();
                    helper.setBlock(new BlockPos(1 + x, 1 + y, 1 + z), block);
                }
            }
        }
        var controllerPos = new BlockPos(2, 1, 1);
        helper.setBlock(controllerPos, ModContent.SILO_CONTROLLER.get());
        var silo = (SiloControllerBlockEntity) helper.getBlockEntity(controllerPos);
        silo.checkStructure();
        return silo;
    }

    /** A 3 × 3 × 4 silo forms and holds 36 × 1,000 buckets; filling and draining work in millibuckets. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void formsAndHolds(GameTestHelper helper) {
        var silo = build(helper, 4);
        if (!silo.isFormed() || silo.structure().height() != 4) helper.fail("a 3x3x4 silo should form, got " + silo.structure());
        if (silo.fluidCapacity() != 36_000_000L) helper.fail("capacity should be 36,000 buckets, is " + silo.fluidCapacity() + " mB");
        if (silo.fill(Fluids.WATER, 5_000, false) != 5_000) helper.fail("water didn't go in");
        if (silo.fill(Fluids.LAVA, 1_000, true) != 0) helper.fail("a second fluid went in");
        if (silo.drain(null, 2_000, false).amount() != 2_000 || silo.amount() != 3_000) helper.fail("draining should leave 3,000 mB, left " + silo.amount());
        helper.succeed();
    }

    /** Two layers isn't a silo; breaking a casing un-forms it, but the fluid stays and drains, and nothing more goes in. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void brokenSiloKeepsFluid(GameTestHelper helper) {
        var low = build(helper, 2);
        if (low.isFormed()) helper.fail("a 2-tall silo shouldn't form");
        build(helper, 3);
        var silo = (SiloControllerBlockEntity) helper.getBlockEntity(new BlockPos(2, 1, 1));
        silo.fill(Fluids.WATER, 10_000, false);
        helper.setBlock(new BlockPos(3, 3, 3), Blocks.AIR);
        silo.checkStructure();
        if (silo.isFormed()) helper.fail("a silo with a missing casing shouldn't stay formed");
        if (silo.amount() != 10_000) helper.fail("the fluid should stay when the silo breaks");
        if (silo.fill(Fluids.WATER, 1_000, true) != 0) helper.fail("a broken silo shouldn't take more fluid");
        if (silo.drain(Fluids.WATER, 1_000, false).amount() != 1_000) helper.fail("a broken silo should still drain");
        helper.succeed();
    }

    /** A silo controller touching a Cargo Controller joins its network as a tank. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void joinsCargoNetwork(GameTestHelper helper) {
        var silo = build(helper, 3);
        var pos = new BlockPos(2, 1, 0);
        helper.setBlock(pos, ModContent.CARGO_CONTROLLER.get());
        var controller = (CargoControllerBlockEntity) helper.getBlockEntity(pos);
        controller.invalidate();
        if (!controller.network().tanks().contains(silo)) helper.fail("the silo should join the network as a tank");
        if (controller.network().fill(Fluids.LAVA, 4_000, false) != 4_000 || silo.amount() != 4_000) helper.fail("the network should fill the silo");
        var contents = controller.network().contents();
        if (contents.size() != 1 || contents.get(0).fluid() != Fluids.LAVA) helper.fail("the terminal should list the lava, lists " + contents);
        helper.succeed();
    }

    /** Any casing or glass block finds its controller (for buckets and pipes), and one paint group is the whole silo. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void anyBlockReachesTheSilo(GameTestHelper helper) {
        var silo = build(helper, 3);
        var topGlass = helper.absolutePos(new BlockPos(2, 3, 1));
        var corner = helper.absolutePos(new BlockPos(3, 3, 3));
        if (io.github.keellam4.oritechextensions.storage.SiloParts.controllerOf(helper.getLevel(), topGlass) != silo) helper.fail("the top glass should find the controller");
        if (io.github.keellam4.oritechextensions.storage.SiloParts.controllerOf(helper.getLevel(), corner) != silo) helper.fail("the far corner should find the controller");
        if (io.github.keellam4.oritechextensions.storage.SiloParts.controllerOf(helper.getLevel(), corner.above()) != null) helper.fail("air above the silo isn't part of it");
        int group = io.github.keellam4.oritechextensions.paint.targets.WorldPaintTarget.SILO.group(helper.getLevel(), topGlass).size();
        if (group != 27) helper.fail("painting should cover the whole 3x3x3 silo, covers " + group);
        helper.succeed();
    }
}
