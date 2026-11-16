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

    private static void partitions(GameTestHelper helper, SiloControllerBlockEntity silo, net.minecraft.world.item.Item upgrade) {
        silo.upgrade().setItem(0, new net.minecraft.world.item.ItemStack(upgrade));
    }

    /** Without an upgrade the silo holds one fluid; a Partition Upgrade lets several share the space. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void partitionsShareSpace(GameTestHelper helper) {
        var silo = build(helper, 3); // 27,000 buckets
        silo.fill(Fluids.WATER, 1_000, false);
        if (silo.fill(Fluids.LAVA, 1_000, true) != 0) helper.fail("one fluid only without an upgrade");
        partitions(helper, silo, ModContent.PARTITION_UPGRADE_1.get());
        if (silo.partitions() != 4) helper.fail("the Partition Upgrade allows 4 fluids, allows " + silo.partitions());
        if (silo.fill(Fluids.LAVA, 2_000, false) != 2_000) helper.fail("lava should go in next to water");
        if (silo.fluids().size() != 2) helper.fail("two fluids expected, has " + silo.fluids());
        long room = silo.roomFor(Fluids.WATER);
        if (room != 27_000_000L - 3_000) helper.fail("water's room should be what's left of the shared space, is " + room);
        if (silo.canRemoveUpgrade()) helper.fail("the upgrade can't come out while it holds two fluids");
        if (!silo.canReplaceUpgradeWith(new net.minecraft.world.item.ItemStack(ModContent.PARTITION_UPGRADE_2.get())))
            helper.fail("a bigger upgrade should be able to replace it while it holds two fluids");
        if (silo.canReplaceUpgradeWith(net.minecraft.world.item.ItemStack.EMPTY)) helper.fail("an empty hand isn't a replacement");
        var contents = new java.util.HashSet<net.minecraft.world.level.material.Fluid>();
        for (var held : silo.fluids()) contents.add(held.fluid());
        if (!contents.contains(Fluids.LAVA) || !contents.contains(Fluids.WATER)) helper.fail("both fluids should be listed");
        helper.succeed();
    }

    /** Limits cap a fluid, reserves keep space for one, locks keep an empty fluid's place, void deletes overflow. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void partitionRules(GameTestHelper helper) {
        var silo = build(helper, 3);
        partitions(helper, silo, ModContent.PARTITION_UPGRADE_1.get());
        silo.fill(Fluids.WATER, 1_000, false);
        silo.fill(Fluids.LAVA, 1_000, false);
        int water = 0, lava = 1;
        silo.configure(water, 0); // limit 10%
        if (silo.tanks().get(water).limit != 10) helper.fail("limit should step to 10%");
        if (silo.roomFor(Fluids.WATER) != 2_700_000L - 1_000) helper.fail("water should be capped at 10% of the silo");
        silo.configure(lava, 2); // reserve 10%
        silo.configure(lava, 2); // reserve 25%
        long reserved = 27_000_000L / 4;
        if (silo.roomFor(Fluids.WATER) != 2_700_000L - 1_000) helper.fail("water's own limit still wins");
        silo.configure(water, 1); // limit back to none
        if (silo.roomFor(Fluids.WATER) != 27_000_000L - reserved - 1_000) helper.fail("lava's reserve should be kept free, room " + silo.roomFor(Fluids.WATER));
        silo.configure(lava, 4); // lock
        silo.drain(Fluids.LAVA, 1_000, false);
        if (silo.tanks().size() != 2) helper.fail("a locked fluid keeps its place when empty");
        silo.configure(water, 5); // void
        if (silo.fill(Fluids.WATER, 100_000_000L, false) != 100_000_000L) helper.fail("void overflow should accept everything");
        if (silo.amount() > silo.fluidCapacity()) helper.fail("void overflow mustn't overfill the silo");
        helper.succeed();
    }

    /** The fluids, their rules and the upgrade survive saving and loading. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void partitionsSave(GameTestHelper helper) {
        var silo = build(helper, 3);
        partitions(helper, silo, ModContent.PARTITION_MATRIX.get());
        silo.fill(Fluids.WATER, 3_000, false);
        silo.fill(Fluids.LAVA, 4_000, false);
        silo.configure(1, 4);
        var registries = helper.getLevel().registryAccess();
        var tag = silo.saveWithoutMetadata(registries);
        var copy = new SiloControllerBlockEntity(silo.getBlockPos(), silo.getBlockState());
        copy.loadWithComponents(tag, registries);
        if (copy.tanks().size() != 2 || copy.tanks().get(1).amount != 4_000 || !copy.tanks().get(1).locked) helper.fail("tanks didn't survive saving");
        if (copy.partitions() != Integer.MAX_VALUE) helper.fail("the Partition Matrix didn't survive saving");
        helper.succeed();
    }

    /** Any casing or glass block touching a Cargo Wall joins the silo to it, and the wall's paint covers the silo. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void siloJoinsWallThroughCasing(GameTestHelper helper) {
        var silo = build(helper, 3);
        silo.fill(Fluids.WATER, 4_000, false);
        // A vault against the side casing (not the controller), and a Cargo Controller on the vault.
        helper.setBlock(new BlockPos(4, 2, 2), ModContent.CARGO_VAULT.get());
        helper.setBlock(new BlockPos(5, 2, 2), ModContent.CARGO_CONTROLLER.get());
        var controller = (io.github.keellam4.oritechextensions.storage.CargoControllerBlockEntity) helper.getBlockEntity(new BlockPos(5, 2, 2));
        controller.invalidate();
        if (!controller.network().tanks().contains(silo)) helper.fail("the silo should join the wall through its casing");
        var group = io.github.keellam4.oritechextensions.paint.targets.WorldPaintTarget.VAULTS
                .group(helper.getLevel(), helper.absolutePos(new BlockPos(4, 2, 2)));
        if (!group.contains(silo.getBlockPos())) helper.fail("painting the wall should paint the silo touching it");
        helper.succeed();
    }

    /** JNI-96: a limit or reserve can be an exact amount in millibuckets; percent clicks replace it again. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void exactLimitsAndReserves(GameTestHelper helper) {
        var silo = build(helper, 3); // 27,000 buckets
        partitions(helper, silo, ModContent.PARTITION_UPGRADE_1.get());
        silo.fill(Fluids.WATER, 1_000, false);
        silo.setExact(0, false, 1_250_500); // 1,250.5 B
        if (silo.roomFor(Fluids.WATER) != 1_250_500 - 1_000) helper.fail("water should stop at 1,250.5 B, room " + silo.roomFor(Fluids.WATER));
        silo.fill(Fluids.LAVA, 1_000, false);
        silo.setExact(1, true, 5_000_000); // keep 5,000 B for lava
        long waterRoom = silo.roomFor(Fluids.WATER);
        if (waterRoom != 1_250_500 - 1_000) helper.fail("the limit still caps water, room " + waterRoom);
        silo.setExact(0, false, 0); // clear water's limit
        if (silo.roomFor(Fluids.WATER) != 27_000_000L - 1_000 - 5_000_000) helper.fail("lava's exact reserve should be kept free, room " + silo.roomFor(Fluids.WATER));
        silo.setExact(1, true, 999_000_000L);
        if (silo.tanks().get(1).reserveMb > silo.fluidCapacity()) helper.fail("a reserve can't be more than the silo");
        silo.configure(1, 2); // a percent click replaces the exact reserve
        if (silo.tanks().get(1).reserveMb != 0 || silo.tanks().get(1).reserve != 10) helper.fail("a percent click should replace the exact reserve with 10%");
        helper.succeed();
    }
}
