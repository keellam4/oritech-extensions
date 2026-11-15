package io.github.keellam4.oritechextensions.storage;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** A side of a vault relative to its front, for picking where Pull and Push work. */
public enum VaultSide {
    FRONT, BACK, LEFT, RIGHT, TOP, BOTTOM;

    /** The world direction of this side for a vault facing {@code facing}. Left and right as seen from the front. */
    public Direction toDirection(Direction facing) {
        return switch (this) {
            case FRONT -> facing;
            case BACK -> facing.getOpposite();
            case LEFT -> facing.getClockWise();
            case RIGHT -> facing.getCounterClockWise();
            case TOP -> Direction.UP;
            case BOTTOM -> Direction.DOWN;
        };
    }

    public int bit() {
        return 1 << ordinal();
    }

    public Component displayName() {
        return Component.translatable("side.oritech_extensions." + name().toLowerCase(Locale.ROOT));
    }
}
