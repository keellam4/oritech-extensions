package io.github.keellam4.oritechextensions.paint;

import net.minecraft.core.BlockPos;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.keellam4.oritechextensions.OritechExtensions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.Nullable;
import rearth.oritech.util.ColorableMachine.ColorVariant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Per-dimension map of painted positions for blocks that keep their paint in the world rather than on a block
 * entity: pipes, Industrial Glass, reactor blocks and particle accelerator blocks. Absent = unpainted.
 * <p>
 * The class and file name still say "pipe" because pipes came first.
 */
public final class PipePaintData extends SavedData {

    /** One painted position as saved to disk. */
    private record Entry(long pos, String color) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("pos").forGetter(Entry::pos),
                Codec.STRING.fieldOf("color").forGetter(Entry::color)
        ).apply(instance, Entry::new));
    }

    public static final Codec<PipePaintData> CODEC = Entry.CODEC.listOf().fieldOf("paints").codec()
            .xmap(PipePaintData::fromEntries, PipePaintData::toEntries);

    public static final SavedDataType<PipePaintData> TYPE =
            new SavedDataType<>(OritechExtensions.id("pipe_paint"), PipePaintData::new, CODEC, null);

    private final Map<BlockPos, ColorVariant> paints = new HashMap<>();
    /** The same positions grouped by chunk, so sending a chunk to a player doesn't walk the whole map. */
    private final Map<Long, Set<BlockPos>> byChunk = new HashMap<>();

    public PipePaintData() {
    }

    public static PipePaintData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    private static PipePaintData fromEntries(List<Entry> entries) {
        var data = new PipePaintData();
        for (var entry : entries) {
            ColorVariant variant;
            try {
                variant = ColorVariant.valueOf(entry.color().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (PaintColors.isPainted(variant)) {
                data.put(BlockPos.of(entry.pos()), variant);
            }
        }
        return data;
    }

    private List<Entry> toEntries() {
        var entries = new ArrayList<Entry>(paints.size());
        for (var entry : paints.entrySet()) {
            if (!PaintColors.isPainted(entry.getValue())) continue;
            entries.add(new Entry(entry.getKey().asLong(), entry.getValue().name().toLowerCase(Locale.ROOT)));
        }
        return entries;
    }

    public Map<BlockPos, ColorVariant> all() {
        return Collections.unmodifiableMap(paints);
    }

    /** Painted positions inside one chunk. */
    public Set<BlockPos> inChunk(ChunkPos chunk) {
        var positions = byChunk.get(chunk.pack());
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
        long key = ChunkPos.containing(pos).pack();
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
        byChunk.computeIfAbsent(ChunkPos.containing(pos).pack(), key -> new HashSet<>()).add(pos);
    }
}
