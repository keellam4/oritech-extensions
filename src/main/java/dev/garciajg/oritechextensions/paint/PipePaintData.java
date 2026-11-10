package dev.garciajg.oritechextensions.paint;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;
import rearth.oritech.util.ColorableMachine.ColorVariant;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Per-dimension map of painted standard pipe positions. Absent = unpainted. */
public final class PipePaintData extends SavedData {

    public static final String DATA_NAME = "oritech_extensions_pipe_paint";

    private final Map<BlockPos, ColorVariant> paints = new HashMap<>();

    public PipePaintData() {
    }

    public static PipePaintData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PipePaintData::new, PipePaintData::load),
                DATA_NAME);
    }

    public static PipePaintData load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new PipePaintData();
        var list = tag.getList("paints", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            var entry = list.getCompound(i);
            var pos = BlockPos.of(entry.getLong("pos"));
            ColorVariant variant;
            try {
                variant = ColorVariant.valueOf(entry.getString("color").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                variant = ColorVariant.ORANGE;
            }
            if (PaintColors.isPainted(variant)) {
                data.paints.put(pos.immutable(), variant);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var list = new ListTag();
        for (var entry : paints.entrySet()) {
            if (!PaintColors.isPainted(entry.getValue())) continue;
            var compound = new CompoundTag();
            compound.putLong("pos", entry.getKey().asLong());
            compound.putString("color", entry.getValue().name().toLowerCase(Locale.ROOT));
            list.add(compound);
        }
        tag.put("paints", list);
        return tag;
    }

    public Map<BlockPos, ColorVariant> all() {
        return Collections.unmodifiableMap(paints);
    }

    @Nullable
    public ColorVariant get(BlockPos pos) {
        return paints.get(pos);
    }

    public ColorVariant getOrDefault(BlockPos pos) {
        var paint = paints.get(pos);
        return paint != null ? paint : ColorVariant.ORANGE;
    }

    public void set(BlockPos pos, ColorVariant variant) {
        if (PaintColors.isPainted(variant)) {
            paints.put(pos.immutable(), variant);
        } else {
            paints.remove(pos);
        }
        setDirty();
    }

    public void remove(BlockPos pos) {
        if (paints.remove(pos) != null) setDirty();
    }

    public boolean has(BlockPos pos) {
        return paints.containsKey(pos);
    }
}
