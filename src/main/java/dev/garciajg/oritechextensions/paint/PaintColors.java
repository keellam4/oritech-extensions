package dev.garciajg.oritechextensions.paint;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import rearth.oritech.util.ColorableMachine.ColorVariant;

import java.util.Locale;

/** Helpers around Oritech's paint colours (ColorableMachine.ColorVariant). ORANGE is Oritech's unpainted default. */
public final class PaintColors {

    public static final Codec<ColorVariant> CODEC = Codec.STRING.xmap(
            name -> {
                try {
                    return ColorVariant.valueOf(name.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    return ColorVariant.ORANGE; // colour removed in a future Oritech version: fall back to unpainted
                }
            },
            variant -> variant.name().toLowerCase(Locale.ROOT));

    public static final StreamCodec<ByteBuf, ColorVariant> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(PaintColors::fromId, PaintColors::toId);

    private PaintColors() {
    }

    public static boolean isPainted(ColorVariant variant) {
        return variant != null && variant != ColorVariant.ORANGE;
    }

    public static int toId(ColorVariant variant) {
        return variant.ordinal();
    }

    public static ColorVariant fromId(int id) {
        var values = ColorVariant.values();
        return id >= 0 && id < values.length ? values[id] : ColorVariant.ORANGE;
    }

    /** Suffix of the generated painted sprites, e.g. {@code oritech:block/machine_speed_addon_on_paint_white}. */
    public static String spriteSuffix(ColorVariant variant) {
        return "_paint_" + variant.name().toLowerCase(Locale.ROOT);
    }

    /** Oritech's own name for the paint item, e.g. "White Machine Paint". */
    public static String paintItemTranslationKey(ColorVariant variant) {
        return "item.oritech." + variant.name().toLowerCase(Locale.ROOT) + "_paint";
    }
}
