package io.github.keellam4.oritechextensions.wireless;

/** Who an Induction Pylon charges. */
public enum PylonAccess {
    /** The player who placed it. */
    OWNER,
    /** The owner and players on the owner's team. */
    TEAM,
    /** Anyone in range. */
    EVERYONE;

    public PylonAccess next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
