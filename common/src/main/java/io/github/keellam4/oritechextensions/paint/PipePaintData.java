package io.github.keellam4.oritechextensions.paint;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;
import rearth.oritech.util.ColorableMachine.ColorVariant;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Per-dimension map of painted positions for blocks that keep their paint in the world rather than on a block
 * entity: pipes, Industrial Glass, reactor blocks and particle accelerator blocks. Absent = unpainted.
 * <p>
 * The class and file name still say "pipe" because pipes came first and saved worlds use that name.
 */
public final class PipePaintData extends SavedData {

    public static final String DATA_NAME = "oritech_extensions_pipe_paint";

    private final Map<BlockPos, ColorVariant> paints = new HashMap<>();
    /** The same positions grouped by chunk, so sending a chunk to a player doesn't walk the whole map. */
    private final Map<Long, Set<BlockPos>> byChunk = new HashMap<>();

    public PipePaintData() {
    }

    public static PipePaintData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PipePaintData::new, PipePaintData::load, null), // no data fixer (vanilla constructor; NeoForge also has a 2-arg one)
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
                data.put(pos.immutable(), variant);
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

    /** Painted positions inside one chunk. */
    public Set<BlockPos> inChunk(ChunkPos chunk) {
        var positions = byChunk.get(chunk.toLong());
        return positions != null ? Collections.unmodifiableSet(positions) : Set.of();
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
            put(pos.immutable(), variant);
            setDirty();
        } else {
            remove(pos);
        }
    }

    public void remove(BlockPos pos) {
        if (paints.remove(pos) == null) return;
        long key = ChunkPos.asLong(pos);
        var positions = byChunk.get(key);
        if (positions != null) {
            positions.remove(pos);
            if (positions.isEmpty()) byChunk.remove(key);
        }
        setDirty();
    }

    public boolean has(BlockPos pos) {
        return paints.containsKey(pos);
    }

    private void put(BlockPos pos, ColorVariant variant) {
        paints.put(pos, variant);
        byChunk.computeIfAbsent(ChunkPos.asLong(pos), key -> new HashSet<>()).add(pos);
    }
}
