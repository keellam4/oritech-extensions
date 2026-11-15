package io.github.keellam4.oritechextensions.tool;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.keellam4.oritechextensions.paint.Paint;
import io.github.keellam4.oritechextensions.paint.PaintColors;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What a Detailing Tool holds: paint charges per colour (one charge = one Color Cartridge), the selected colour,
 * the mode, and the first corner of an area that is being marked.
 */
public record ToolContents(Map<Paint, Integer> charges, Paint selected, DetailingMode mode, Optional<BlockPos> corner) {

    /** Most charges of one colour the tool holds: a stack of cartridges. */
    public static final int CAPACITY = 64;

    public static final ToolContents EMPTY = new ToolContents(Map.of(), Paint.ORANGE, DetailingMode.GROUP, Optional.empty());

    public static final Codec<ToolContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(PaintColors.CODEC, Codec.INT).optionalFieldOf("charges", Map.of()).forGetter(ToolContents::charges),
            PaintColors.CODEC.optionalFieldOf("selected", Paint.ORANGE).forGetter(ToolContents::selected),
            DetailingMode.CODEC.optionalFieldOf("mode", DetailingMode.GROUP).forGetter(ToolContents::mode),
            BlockPos.CODEC.optionalFieldOf("corner").forGetter(ToolContents::corner)
    ).apply(instance, ToolContents::new));

    private static final StreamCodec<ByteBuf, Map<Paint, Integer>> CHARGES_STREAM_CODEC =
            ByteBufCodecs.<ByteBuf, Paint, Integer, Map<Paint, Integer>>map(HashMap::new, PaintColors.STREAM_CODEC, ByteBufCodecs.VAR_INT);

    public static final StreamCodec<ByteBuf, ToolContents> STREAM_CODEC = StreamCodec.composite(
            CHARGES_STREAM_CODEC, ToolContents::charges,
            PaintColors.STREAM_CODEC, ToolContents::selected,
            DetailingMode.STREAM_CODEC, ToolContents::mode,
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC), ToolContents::corner,
            ToolContents::new);

    public ToolContents {
        var kept = new HashMap<Paint, Integer>();
        charges.forEach((paint, count) -> {
            if (count != null && count > 0) kept.put(paint, Math.min(count, CAPACITY));
        });
        charges = Map.copyOf(kept);
    }

    public int count(Paint paint) {
        return charges.getOrDefault(paint, 0);
    }

    public int selectedCount() {
        return count(selected);
    }

    public boolean isEmpty() {
        return charges.isEmpty();
    }

    /** Loaded colours in Oritech's order. */
    public List<Paint> loaded() {
        var list = new ArrayList<>(charges.keySet());
        list.sort(Comparator.naturalOrder());
        return list;
    }

    public ToolContents withCount(Paint paint, int count) {
        var copy = new HashMap<>(charges);
        copy.put(paint, count);
        return new ToolContents(copy, selected, mode, corner);
    }

    public ToolContents withSelected(Paint paint) {
        return new ToolContents(charges, paint, mode, corner);
    }

    /** Switching mode forgets a half-marked area. */
    public ToolContents withMode(DetailingMode newMode) {
        return new ToolContents(charges, selected, newMode, Optional.empty());
    }

    public ToolContents withCorner(Optional<BlockPos> newCorner) {
        return new ToolContents(charges, selected, mode, newCorner.map(BlockPos::immutable));
    }

    /** The next ({@code direction} > 0) or previous loaded colour; unchanged when nothing is loaded. */
    public ToolContents cycle(int direction) {
        var loaded = loaded();
        if (loaded.isEmpty()) return this;
        int index = loaded.indexOf(selected);
        int next;
        if (index < 0) {
            next = direction > 0 ? 0 : loaded.size() - 1;
        } else {
            next = Math.floorMod(index + Integer.signum(direction), loaded.size());
        }
        return withSelected(loaded.get(next));
    }
}
