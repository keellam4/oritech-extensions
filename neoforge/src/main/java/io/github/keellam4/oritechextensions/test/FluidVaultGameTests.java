package io.github.keellam4.oritechextensions.test;

import io.github.keellam4.oritechextensions.Config;
import io.github.keellam4.oritechextensions.ModContent;
import io.github.keellam4.oritechextensions.OritechExtensions;
import io.github.keellam4.oritechextensions.storage.FluidVaultBlockEntity;
import io.github.keellam4.oritechextensions.storage.VaultUpgrade;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The Fluid Vault: one fluid, capacity and upgrades, keeping its fluid when broken, and Push into another tank. */
@GameTestHolder(OritechExtensions.MODID)
@PrefixGameTestTemplate(false)
public class FluidVaultGameTests {

    private static final String TEMPLATE = "empty_tall";

    private static FluidVaultBlockEntity tank(GameTestHelper helper, BlockPos relative) {
        var pos = helper.absolutePos(relative);
        helper.getLevel().setBlockAndUpdate(pos, ModContent.FLUID_VAULT.get().defaultBlockState());
        return (FluidVaultBlockEntity) helper.getLevel().getBlockEntity(pos);
    }

    /** Holds one fluid up to its capacity; capacity upgrades and the lock work as on the item vaults. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void holdsOneFluid(GameTestHelper helper) {
        var tank = tank(helper, new BlockPos(2, 1, 2));
        long capacity = Config.fluidVaultBuckets() * 1000L;
        if (tank.fluidCapacity() != capacity) helper.fail("capacity " + tank.fluidCapacity() + ", expected " + capacity);
        if (tank.fill(Fluids.WATER, 5000, false) != 5000 || tank.amount() != 5000) helper.fail("didn't take 5 buckets of water");
        if (tank.fill(Fluids.LAVA, 1000, true) != 0) helper.fail("took lava into a water tank");
        var drained = tank.drain(null, 2000, false);
        if (drained.fluid() != Fluids.WATER || drained.amount() != 2000 || tank.amount() != 3000) helper.fail("draining 2 buckets went wrong");
        if (tank.fill(Fluids.WATER, capacity, false) != capacity - 3000) helper.fail("went over capacity");

        tank.installUpgrade(new ItemStack(ModContent.VAULT_UPGRADES.get(VaultUpgrade.STEEL_CAPACITY).get(), 2));
        if (tank.fluidCapacity() != capacity * 4) helper.fail("two Steel upgrades should make ×4");

        tank.drain(null, tank.amount(), false);
        if (tank.fluid() != Fluids.EMPTY) helper.fail("an empty unlocked tank kept its fluid");
        tank.fill(Fluids.LAVA, 1000, false);
        tank.setLocked(true);
        tank.drain(null, 1000, false);
        if (tank.fluid() != Fluids.LAVA || tank.fill(Fluids.WATER, 1000, true) != 0) helper.fail("a locked empty tank should keep lava and refuse water");
        helper.succeed();
    }

    /** Void: a full tank keeps taking its fluid and deletes the overflow. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void voidDeletesOverflow(GameTestHelper helper) {
        var tank = tank(helper, new BlockPos(2, 1, 2));
        tank.installUpgrade(new ItemStack(ModContent.VAULT_UPGRADES.get(VaultUpgrade.VOID).get()));
        tank.fill(Fluids.WATER, tank.fluidCapacity(), false);
        if (tank.fill(Fluids.WATER, 4000, false) != 4000 || tank.amount() != tank.fluidCapacity()) helper.fail("Void didn't swallow the overflow");
        helper.succeed();
    }

    /** Breaking the tank keeps its fluid on the item; placing it again brings it back. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void keepsFluidWhenBroken(GameTestHelper helper) {
        var level = helper.getLevel();
        var tank = tank(helper, new BlockPos(2, 1, 2));
        tank.fill(Fluids.LAVA, 7000, false);
        var pos = tank.getBlockPos();
        level.destroyBlock(pos, true);
        var dropped = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2)).stream()
                .map(ItemEntity::getItem).filter(s -> s.is(ModContent.FLUID_VAULT_ITEM.get())).findFirst();
        if (dropped.isEmpty()) {
            helper.fail("the Fluid Vault didn't drop");
            return;
        }
        var contents = dropped.get().get(ModContent.FLUID_CONTENTS.get());
        if (contents == null || contents.fluid() != Fluids.LAVA || contents.amount() != 7000) helper.fail("the dropped tank lost its lava: " + contents);
        var again = tank(helper, new BlockPos(2, 1, 4));
        again.applyComponentsFromItemStack(dropped.get());
        if (again.amount() != 7000 || again.fluid() != Fluids.LAVA) helper.fail("the placed tank didn't get its lava back");
        helper.succeed();
    }

    /** Push moves fluid into the tank behind (another Fluid Vault). */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void pushIntoTankBehind(GameTestHelper helper) {
        var front = tank(helper, new BlockPos(2, 1, 2));
        var behind = tank(helper, new BlockPos(2, 1, 3));
        front.fill(Fluids.WATER, 3000, false);
        front.installUpgrade(new ItemStack(ModContent.VAULT_UPGRADES.get(VaultUpgrade.PUSH).get()));
        helper.succeedWhen(() -> {
            if (behind.amount() < 1000 || behind.fluid() != Fluids.WATER) helper.fail("no water pushed yet");
        });
    }
}
