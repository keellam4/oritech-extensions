package io.github.keellam4.oritechextensions.test;

import io.github.keellam4.oritechextensions.Config;
import io.github.keellam4.oritechextensions.ModContent;
import io.github.keellam4.oritechextensions.OritechExtensions;
import io.github.keellam4.oritechextensions.storage.CargoControllerBlockEntity;
import io.github.keellam4.oritechextensions.wireless.CargoLinkItem;
import io.github.keellam4.oritechextensions.wireless.InductionPylonBlock;
import io.github.keellam4.oritechextensions.wireless.InductionPylonBlockEntity;
import io.github.keellam4.oritechextensions.wireless.LinkRange;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The Cargo Link: range from the mast and segments, the battery, the remote terminal, and pylon charging. */
@GameTestHolder(OritechExtensions.MODID)
@PrefixGameTestTemplate(false)
public class CargoLinkGameTests {

    private static final String TEMPLATE = "empty_tall";

    private static CargoControllerBlockEntity wall(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 2), ModContent.CARGO_CONTROLLER.get());
        helper.setBlock(new BlockPos(2, 1, 2), ModContent.CARGO_VAULT.get());
        var controller = (CargoControllerBlockEntity) helper.getBlockEntity(new BlockPos(1, 1, 2));
        controller.invalidate();
        return controller;
    }

    /** 32 blocks with no mast; a mast touching the wall makes it 64; each Antenna Segment adds 16. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void rangeFromMastAndSegments(GameTestHelper helper) {
        var controller = wall(helper);
        var level = helper.getLevel();
        if (LinkRange.range(level, controller) != Config.linkRange()) helper.fail("no mast: " + Config.linkRange() + " blocks");
        helper.setBlock(new BlockPos(3, 1, 2), ModContent.UPLINK_MAST.get());
        if (LinkRange.range(level, controller) != Config.linkMastRange()) helper.fail("a mast on the vault should give " + Config.linkMastRange());
        for (int i = 1; i <= 3; i++) helper.setBlock(new BlockPos(3, 1 + i, 2), ModContent.ANTENNA_SEGMENT.get());
        int expected = Config.linkMastRange() + 3 * Config.linkSegmentRange();
        if (LinkRange.range(level, controller) != expected) helper.fail("three segments should give " + expected + ", got " + LinkRange.range(level, controller));
        if (LinkRange.bars(0, expected) != 4 || LinkRange.bars(expected, expected) != 1 || LinkRange.bars(expected + 1, expected) != 0) {
            helper.fail("signal bars: 4 close by, 1 at the edge, 0 outside");
        }
        helper.succeed();
    }

    /** Transfers use the battery; a low battery refuses them without using anything. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void batteryPaysForTransfers(GameTestHelper helper) {
        var link = new ItemStack(ModContent.CARGO_LINK.get());
        CargoLinkItem.setEnergy(link, 1000);
        if (!CargoLinkItem.spend(link, 64, 0)) helper.fail("1000 RF should pay for a stack");
        int left = 1000 - 64 * Config.linkEnergyPerItem();
        if (CargoLinkItem.energy(link) != left) helper.fail("the battery should have " + left + " RF left, has " + CargoLinkItem.energy(link));
        if (CargoLinkItem.spend(link, 100_000, 0)) helper.fail("an empty battery can't pay");
        if (CargoLinkItem.energy(link) != left) helper.fail("a refused transfer mustn't use energy");
        helper.succeed();
    }

    /** The remote terminal stays open while the linked Link is in hand and in range. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void remoteTerminalNeedsRange(GameTestHelper helper) {
        var controller = wall(helper);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var link = new ItemStack(ModContent.CARGO_LINK.get());
        var target = GlobalPos.of(helper.getLevel().dimension(), controller.getBlockPos());
        CargoLinkItem.bind(link, target);
        player.setItemInHand(InteractionHand.MAIN_HAND, link);
        player.setPos(Vec3.atCenterOf(controller.getBlockPos()).add(10, 0, 0));
        var access = new CargoLinkItem.Access(InteractionHand.MAIN_HAND, target, 32);
        if (!access.stillValid(player)) helper.fail("10 blocks away is in range");
        player.setPos(Vec3.atCenterOf(controller.getBlockPos()).add(40, 0, 0));
        if (access.stillValid(player)) helper.fail("40 blocks away is out of a 32-block range");
        player.setPos(Vec3.atCenterOf(controller.getBlockPos()).add(5, 0, 0));
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        if (access.stillValid(player)) helper.fail("putting the Link away closes the terminal");
        helper.succeed();
    }

    /** An Induction Pylon charges the Link's battery. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void pylonChargesTheLink(GameTestHelper helper) {
        var pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, ModContent.INDUCTION_PYLON.get().defaultBlockState());
        helper.setBlock(pos.above(), ModContent.INDUCTION_PYLON.get().defaultBlockState().setValue(InductionPylonBlock.HALF, DoubleBlockHalf.UPPER));
        var pylon = (InductionPylonBlockEntity) helper.getBlockEntity(pos);
        pylon.receive(100_000, false);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(helper.absoluteVec(new Vec3(3.5, 1, 2.5)));
        player.getInventory().setItem(3, new ItemStack(ModContent.CARGO_LINK.get()));
        pylon.charge(player, 20_000);
        int energy = CargoLinkItem.energy(player.getInventory().getItem(3));
        if (energy <= 0) helper.fail("the pylon should charge the Link");
        helper.succeed();
    }

    /** Upgrades in the mast: Infinite Range ignores distance, Dimensional also other dimensions; they cost more energy. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void rangeUpgrades(GameTestHelper helper) {
        var controller = wall(helper);
        var level = helper.getLevel();
        helper.setBlock(new BlockPos(3, 1, 2), ModContent.UPLINK_MAST.get());
        var mast = (io.github.keellam4.oritechextensions.wireless.UplinkMastBlockEntity) helper.getBlockEntity(new BlockPos(3, 1, 2));
        if (LinkRange.upgrade(level, controller) != 0) helper.fail("no upgrade yet");
        mast.upgrade().setItem(0, new ItemStack(ModContent.INFINITE_RANGE_UPGRADE.get()));
        if (LinkRange.upgrade(level, controller) != io.github.keellam4.oritechextensions.wireless.LinkUpgradeItem.INFINITE) helper.fail("the mast's upgrade applies to the wall");
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var link = new ItemStack(ModContent.CARGO_LINK.get());
        var target = GlobalPos.of(level.dimension(), controller.getBlockPos());
        CargoLinkItem.bind(link, target);
        player.setItemInHand(InteractionHand.MAIN_HAND, link);
        player.setPos(Vec3.atCenterOf(controller.getBlockPos()).add(5000, 0, 0));
        if (!new CargoLinkItem.Access(InteractionHand.MAIN_HAND, target, Integer.MAX_VALUE, 1).stillValid(player)) helper.fail("infinite range works 5000 blocks away");
        var elsewhere = GlobalPos.of(net.minecraft.world.level.Level.NETHER, controller.getBlockPos());
        CargoLinkItem.bind(link, elsewhere);
        if (new CargoLinkItem.Access(InteractionHand.MAIN_HAND, elsewhere, Integer.MAX_VALUE, 1).stillValid(player)) helper.fail("infinite range stays in its dimension");
        if (!new CargoLinkItem.Access(InteractionHand.MAIN_HAND, elsewhere, Integer.MAX_VALUE, 2).stillValid(player)) helper.fail("dimensional works across dimensions");
        var cheap = new ItemStack(ModContent.CARGO_LINK.get());
        var dear = new ItemStack(ModContent.CARGO_LINK.get());
        CargoLinkItem.setEnergy(cheap, 10_000);
        CargoLinkItem.setEnergy(dear, 10_000);
        CargoLinkItem.spend(cheap, 10, 0);
        CargoLinkItem.spend(dear, 10, 2);
        if (10_000 - CargoLinkItem.energy(dear) != 4 * (10_000 - CargoLinkItem.energy(cheap))) helper.fail("dimensional transfers cost four times as much");
        helper.succeed();
    }

    /** Survival players pay for transfers; creative players don't. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void survivalPaysCreativeDoesnt(GameTestHelper helper) {
        var survivor = helper.makeMockPlayer(GameType.SURVIVAL);
        var used = new ItemStack(ModContent.CARGO_LINK.get());
        CargoLinkItem.setEnergy(used, 1000);
        survivor.setItemInHand(InteractionHand.MAIN_HAND, used);
        new CargoLinkItem.Access(InteractionHand.MAIN_HAND, GlobalPos.of(helper.getLevel().dimension(), BlockPos.ZERO), 32).pay(survivor, 10);
        if (CargoLinkItem.energy(survivor.getMainHandItem()) >= 1000) helper.fail("a survival player's Link should use energy");
        var player = helper.makeMockPlayer(GameType.CREATIVE);
        var link = new ItemStack(ModContent.CARGO_LINK.get());
        CargoLinkItem.setEnergy(link, 1000);
        player.setItemInHand(InteractionHand.MAIN_HAND, link);
        var access = new CargoLinkItem.Access(InteractionHand.MAIN_HAND, GlobalPos.of(helper.getLevel().dimension(), BlockPos.ZERO), 32);
        access.pay(player, 10);
        if (CargoLinkItem.energy(player.getMainHandItem()) != 1000) helper.fail("a creative player's Link shouldn't use energy");
        helper.succeed();
    }

    /** A Network Anchor keeps the network's chunks loaded, and lets go when removed. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void networkAnchorLoadsChunks(GameTestHelper helper) {
        var controller = wall(helper);
        var level = helper.getLevel();
        controller.setAnchored(true);
        CargoControllerBlockEntity.serverTick(level, controller.getBlockPos(), controller.getBlockState(), controller);
        long chunk = net.minecraft.world.level.ChunkPos.asLong(controller.getBlockPos());
        if (!controller.forcedChunks().contains(chunk) || !level.getForcedChunks().contains(chunk)) helper.fail("the controller's chunk should be force-loaded");
        controller.setAnchored(false);
        if (level.getForcedChunks().contains(chunk)) helper.fail("removing the anchor should release the chunk");
        helper.succeed();
    }
}
