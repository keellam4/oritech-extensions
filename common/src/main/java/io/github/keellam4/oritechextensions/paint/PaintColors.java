package io.github.keellam4.oritechextensions.paint;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Locale;

/** Helpers around the paint colours ({@link Paint}). ORANGE is Oritech's unpainted default. */
public final class PaintColors {

    public static final Codec<Paint> CODEC = Codec.STRING.xmap(
            name -> {
                try {
                    return Paint.valueOf(name.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    return Paint.ORANGE; // colour removed in a future Oritech version: fall back to unpainted
                }
            },
            variant -> variant.name().toLowerCase(Locale.ROOT));

    public static final StreamCodec<ByteBuf, Paint> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(PaintColors::fromId, PaintColors::toId);

    private PaintColors() {
    }

    public static boolean isPainted(Paint variant) {
        return variant != null && variant != Paint.ORANGE;
    }

    public static int toId(Paint variant) {
        return variant.ordinal();
    }

    public static Paint fromId(int id) {
        var values = Paint.values();
        return id >= 0 && id < values.length ? values[id] : Paint.ORANGE;
    }

    /** Suffix of the generated painted sprites, e.g. {@code oritech:block/machine_speed_addon_on_paint_white}. */
    public static String spriteSuffix(Paint variant) {
        return "_paint_" + variant.name().toLowerCase(Locale.ROOT);
    }

    /** Oritech's own name for the paint item, e.g. "White Machine Paint". */
    public static String paintItemTranslationKey(Paint variant) {
        if (variant == Paint.RAINBOW) return "item.oritech_extensions.rainbow_cartridge";
        return "item.oritech." + variant.name().toLowerCase(Locale.ROOT) + "_paint";
    }
}
