package io.github.keellam4.oritechextensions.paint;

import org.jetbrains.annotations.Nullable;
import rearth.oritech.util.ColorableMachine.ColorVariant;

import java.util.Locale;

/**
 * The colours this mod stores: Oritech's paint colours, in Oritech's order (so ids match {@link ColorVariant}
 * ordinals and old saves and packets still read the same), plus {@link #RAINBOW}, which only this mod has.
 * {@link #ORANGE} is Oritech's unpainted default.
 */
public enum Paint {
    ORANGE, DIAMOND, CAMO, FLUXITE, WHITE, INDUSTRIAL, NETHERITE, REDSTONE, SCULK,
    /** Cycles through the colours (an animated texture). Only blocks painted by this mod can be rainbow. */
    RAINBOW;

    /** The same colour as Oritech's. */
    public static Paint of(ColorVariant variant) {
        return valueOf(variant.name());
    }

    /** Oritech's colour for this paint, or null for {@link #RAINBOW} (Oritech's own machines can't be rainbow). */
    @Nullable
    public ColorVariant variant() {
        return this == RAINBOW ? null : ColorVariant.valueOf(name());
    }

    /** Lower-case name, as saved and as used in sprite names ({@code _paint_white}). */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
