package io.github.keellam4.oritechextensions.storage;

import java.util.Locale;

/** What an upgrade in a vault's upgrade screen does. */
public enum VaultUpgrade {
    /** Capacity upgrades add up: two Electrum (×4 each) make a vault hold 8 times as much. */
    STEEL_CAPACITY(2),
    ELECTRUM_CAPACITY(4),
    ENERGITE_CAPACITY(8),
    DURATIUM_CAPACITY(16),
    PROMETHEUM_CAPACITY(32),
    /** Items that don't fit any more are deleted instead of backing up (only items the vault already holds). */
    VOID(0),
    /** Pulls the vault's items out of the inventory behind it. */
    PULL(0),
    /** Pushes the vault's items into the inventory behind it. */
    PUSH(0),
    /** The vault gives off a redstone signal as strong as it is full, and a redstone signal pauses Pull and Push. */
    REDSTONE(0);

    private final int multiplier;

    VaultUpgrade(int multiplier) {
        this.multiplier = multiplier;
    }

    /** Capacity multiplier, or 0 for upgrades that don't change capacity. */
    public int multiplier() {
        return multiplier;
    }

    public boolean isCapacity() {
        return multiplier > 0;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT) + "_upgrade";
    }
}
