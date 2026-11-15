package io.github.keellam4.oritechextensions.tool;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Locale;

/** What one click of the Detailing Tool paints. */
public enum DetailingMode {
    /** The clicked block and everything painted with it: a pipe network, a glass wall, a reactor. */
    GROUP,
    /** Only the clicked block, even inside a pipe network or a glass wall. */
    SINGLE,
    /** Two clicks mark opposite corners; every paintable block in the box is painted. */
    AREA;

    public static final Codec<DetailingMode> CODEC = Codec.STRING.xmap(
            name -> {
                try {
                    return valueOf(name.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    return GROUP;
                }
            },
            mode -> mode.name().toLowerCase(Locale.ROOT));

    public static final StreamCodec<ByteBuf, DetailingMode> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(id -> values()[Math.floorMod(id, values().length)], Enum::ordinal);

    public DetailingMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public Component displayName() {
        return Component.translatable("mode.oritech_extensions.detailing_tool." + name().toLowerCase(Locale.ROOT));
    }
}
