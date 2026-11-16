package io.github.keellam4.oritechextensions.storage;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * One fluid in a Fluid Silo, with its rules: {@code limit} (the most it may use, as a % of the silo; 0 = no
 * limit), {@code reserve} (space kept for it even when empty, % of the silo), {@code locked} (it keeps its place
 * when it runs empty) and {@code voids} (fluid that doesn't fit is deleted). A limit or reserve can instead be an
 * exact amount in millibuckets ({@code limitMb}, {@code reserveMb}; 0 = use the percentage).
 */
public final class SiloTank {

    public static final int[] LIMIT_STEPS = {0, 10, 25, 50, 75, 90};
    public static final int[] RESERVE_STEPS = {0, 10, 25, 50};

    public Fluid fluid;
    public long amount;
    public int limit;
    public int reserve;
    public long limitMb;
    public long reserveMb;
    public boolean locked;
    public boolean voids;

    public SiloTank(Fluid fluid) {
        this.fluid = fluid;
    }

    public SiloTank copy() {
        var copy = new SiloTank(fluid);
        copy.amount = amount;
        copy.limit = limit;
        copy.reserve = reserve;
        copy.limitMb = limitMb;
        copy.reserveMb = reserveMb;
        copy.locked = locked;
        copy.voids = voids;
        return copy;
    }

    /** Whether it can go once it's empty: nothing about it was set by the player. */
    public boolean disposable() {
        return amount <= 0 && !locked && limit == 0 && reserve == 0 && limitMb == 0 && reserveMb == 0 && !voids;
    }

    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putString("fluid", BuiltInRegistries.FLUID.getKey(fluid).toString());
        tag.putLong("amount", amount);
        if (limit > 0) tag.putInt("limit", limit);
        if (reserve > 0) tag.putInt("reserve", reserve);
        if (limitMb > 0) tag.putLong("limit_mb", limitMb);
        if (reserveMb > 0) tag.putLong("reserve_mb", reserveMb);
        if (locked) tag.putBoolean("locked", true);
        if (voids) tag.putBoolean("void", true);
        return tag;
    }

    public static SiloTank load(CompoundTag tag) {
        var id = ResourceLocation.tryParse(tag.getString("fluid"));
        var tank = new SiloTank(id == null ? Fluids.EMPTY : BuiltInRegistries.FLUID.get(id));
        tank.amount = tag.getLong("amount");
        tank.limit = tag.getInt("limit");
        tank.reserve = tag.getInt("reserve");
        tank.limitMb = tag.getLong("limit_mb");
        tank.reserveMb = tag.getLong("reserve_mb");
        tank.locked = tag.getBoolean("locked");
        tank.voids = tag.getBoolean("void");
        return tank;
    }

    static int step(int[] steps, int current, boolean back) {
        int index = 0;
        for (int i = 0; i < steps.length; i++) if (steps[i] == current) index = i;
        return steps[Math.floorMod(index + (back ? -1 : 1), steps.length)];
    }
}
