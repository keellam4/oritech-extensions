package dev.garciajg.oritechextensions;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {

    /** Hard upper bound baked into the extension block state (index property). */
    public static final int ABSOLUTE_MAX_DOOR_LENGTH = 32;

    /** Oritech's Industrial Hangar Door is always 3 blocks long (anchor + 2 helpers). */
    public static final int BASE_DOOR_LENGTH = 3;

    public enum PipePaintCostMode {
        /** One Color Cartridge paints or clears the whole connected pipe network. */
        NETWORK,
        /** One Color Cartridge per pipe block, spreading from the clicked block. */
        PER_BLOCK
    }

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue MAX_HANGAR_DOOR_LENGTH = BUILDER
            .comment("Maximum total length of an Industrial Hangar Door in blocks, including Oritech's original 3 blocks.",
                    "The default of 16 allows 13 extension blocks. Range: 4 to " + ABSOLUTE_MAX_DOOR_LENGTH + ".")
            .defineInRange("maxHangarDoorLength", 16, BASE_DOOR_LENGTH + 1, ABSOLUTE_MAX_DOOR_LENGTH);

    public static final ModConfigSpec.EnumValue<PipePaintCostMode> PIPE_PAINT_COST_MODE = BUILDER
            .comment("How Color Cartridges are consumed when painting standard item/fluid pipes.",
                    "NETWORK = one cartridge for the whole connected network.",
                    "PER_BLOCK = one cartridge per pipe block (spreads until the stack runs out).")
            .defineEnum("pipePaintCostMode", PipePaintCostMode.NETWORK);

    public static final ModConfigSpec.BooleanValue PIPE_PAINT_ISOLATION = BUILDER
            .comment("Reserved for a future release. When enabled, painted pipes will only connect to the same colour.",
                    "Currently unused — paint is cosmetic only.")
            .define("pipePaintIsolation", false);

    static final ModConfigSpec SPEC = BUILDER.build();

    /** How many extension blocks may be attached to one door. Safe to call before the config has loaded. */
    public static int maxExtensions() {
        int total = SPEC.isLoaded() ? MAX_HANGAR_DOOR_LENGTH.get() : MAX_HANGAR_DOOR_LENGTH.getDefault();
        return total - BASE_DOOR_LENGTH;
    }

    public static int maxTotalLength() {
        return maxExtensions() + BASE_DOOR_LENGTH;
    }

    public static PipePaintCostMode pipePaintCostMode() {
        return SPEC.isLoaded() ? PIPE_PAINT_COST_MODE.get() : PIPE_PAINT_COST_MODE.getDefault();
    }
}
