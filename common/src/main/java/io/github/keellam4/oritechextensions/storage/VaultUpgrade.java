package io.github.keellam4.oritechextensions.storage;

import java.util.Locale;

/** What an upgrade in a vault's upgrade screen does. */
public enum VaultUpgrade {
    /** Capacity upgrades add up: two Electrum (×4 each) make a vault hold 8 times as much. Up to five, of one tier. */
    STEEL_CAPACITY(2),
    ELECTRUM_CAPACITY(4),
    ENERGITE_CAPACITY(8),
    DURATIUM_CAPACITY(16),
    PROMETHEUM_CAPACITY(32),
    /** Items that don't fit any more are deleted instead of backing up (only items the vault already holds). */
    VOID(0),
    /** Pulls the vault's items out of the inventories on the sides picked in the upgrade screen. */
    PULL(0),
    /** Pushes the vault's items into the inventories on the sides picked in the upgrade screen. */
    PUSH(0),
    /** The vault gives off a redstone signal as strong as it is full, and a redstone signal pauses Pull and Push. */
    REDSTONE(0);

    /** The upgrade screen has one slot per kind; capacity upgrades share one slot that takes up to five. */
    public enum Kind {
        CAPACITY(5), VOID(1), PULL(1), PUSH(1), REDSTONE(1);

        private final int limit;

        Kind(int limit) {
            this.limit = limit;
        }

        /** How many upgrades this kind's slot takes. */
        public int limit() {
            return limit;
        }

        /** The slot in the upgrade screen. */
        public int slot() {
            return ordinal();
        }
    }

    private final int multiplier;

    VaultUpgrade(int multiplier) {
        this.multiplier = multiplier;
    }

    public Kind kind() {
        return switch (this) {
            case VOID -> Kind.VOID;
            case PULL -> Kind.PULL;
            case PUSH -> Kind.PUSH;
            case REDSTONE -> Kind.REDSTONE;
            default -> Kind.CAPACITY;
        };
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
