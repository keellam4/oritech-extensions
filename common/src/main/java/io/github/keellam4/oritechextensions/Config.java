package io.github.keellam4.oritechextensions;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {

    /** Hard upper bound baked into the extension block state (index property). */
    public static final int ABSOLUTE_MAX_DOOR_LENGTH = 32;

    /** Oritech's Industrial Hangar Door is always 3 blocks long (anchor + 2 helpers). */
    public static final int BASE_DOOR_LENGTH = 3;

    public enum PipePaintCostMode {
        /** One Color Cartridge paints or clears the whole connected group (a pipe network, a reactor, ...). */
        NETWORK,
        /** One Color Cartridge per block, spreading from the clicked block. */
        PER_BLOCK
    }

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue MAX_HANGAR_DOOR_LENGTH = BUILDER
            .comment("Maximum total length of an Industrial Hangar Door in blocks, including Oritech's original 3 blocks.",
                    "The default of 16 allows 13 extension blocks. Range: 4 to " + ABSOLUTE_MAX_DOOR_LENGTH + ".")
            .defineInRange("maxHangarDoorLength", 16, BASE_DOOR_LENGTH + 1, ABSOLUTE_MAX_DOOR_LENGTH);

    public static final ModConfigSpec.EnumValue<PipePaintCostMode> PIPE_PAINT_COST_MODE = BUILDER
            .comment("How Color Cartridges are consumed when painting blocks that are painted as a group:",
                    "pipe networks, Industrial Glass, reactors and particle accelerators.",
                    "NETWORK = one cartridge for the whole connected group.",
                    "PER_BLOCK = one cartridge per block (spreads until the stack runs out).")
            .defineEnum("pipePaintCostMode", PipePaintCostMode.NETWORK);

    public static final ModConfigSpec.IntValue MAX_PAINT_GROUP_SIZE = BUILDER
            .comment("The most Industrial Glass, reactor or particle accelerator blocks painted by one click.",
                    "Bigger builds take more than one click. Pipe networks have no limit.")
            .defineInRange("maxPaintGroupSize", 4096, 16, 65536);

    public static final ModConfigSpec.BooleanValue PIPE_PAINT_ISOLATION = BUILDER
            .comment("Colour-isolated pipes: painted item and fluid pipes only connect to pipes of the same colour.",
                    "Unpainted pipes still connect to every colour. Turning this on or off changes pipes the next time",
                    "they are painted or placed.")
            .define("pipePaintIsolation", true);

    public static final ModConfigSpec.IntValue DETAILING_TOOL_AREA_SIZE = BUILDER
            .comment("The longest side, in blocks, of an area the Detailing Tool paints in Area mode.")
            .defineInRange("detailingToolAreaSize", 32, 1, 128);

    public static final ModConfigSpec.IntValue CARGO_VAULT_STACKS = BUILDER
            .comment("How many stacks one vault holds (64 stacks of 64 = 4,096 items by default).",
                    "Quad and Grid Vaults split this evenly between their 4 or 16 windows.")
            .defineInRange("cargoVaultStacks", 64, 1, 1024);

    public static final ModConfigSpec.IntValue FLUID_VAULT_BUCKETS = BUILDER
            .comment("How many buckets one Fluid Vault holds before capacity upgrades.")
            .defineInRange("fluidVaultBuckets", 256, 1, 100000);

    public static final ModConfigSpec.IntValue PYLON_RANGE = BUILDER
            .comment("How far an Induction Pylon reaches, in blocks.")
            .defineInRange("pylonRange", 8, 1, 64);

    public static final ModConfigSpec.IntValue PYLON_RATE = BUILDER
            .comment("How much energy (RF) per tick an Induction Pylon can hand out, shared by everyone it charges.")
            .defineInRange("pylonRate", 2000, 1, 1_000_000);

    public static final ModConfigSpec.IntValue PYLON_BUFFER = BUILDER
            .comment("How much energy (RF) an Induction Pylon stores.")
            .defineInRange("pylonBuffer", 1_000_000, 1000, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue SILO_BUCKETS_PER_BLOCK = BUILDER
            .comment("How many buckets a Fluid Silo holds per block of its size (a 3 x 3 x 5 silo is 45 blocks).")
            .defineInRange("siloBucketsPerBlock", 1000, 1, 100000);

    public static final ModConfigSpec SPEC = BUILDER.build();

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

    public static boolean pipePaintIsolation() {
        return SPEC.isLoaded() ? PIPE_PAINT_ISOLATION.get() : PIPE_PAINT_ISOLATION.getDefault();
    }

    public static int maxPaintGroupSize() {
        return SPEC.isLoaded() ? MAX_PAINT_GROUP_SIZE.get() : MAX_PAINT_GROUP_SIZE.getDefault();
    }

    public static int detailingToolAreaSize() {
        return SPEC.isLoaded() ? DETAILING_TOOL_AREA_SIZE.get() : DETAILING_TOOL_AREA_SIZE.getDefault();
    }

    public static int cargoVaultStacks() {
        return SPEC.isLoaded() ? CARGO_VAULT_STACKS.get() : CARGO_VAULT_STACKS.getDefault();
    }

    public static int fluidVaultBuckets() {
        return SPEC.isLoaded() ? FLUID_VAULT_BUCKETS.get() : FLUID_VAULT_BUCKETS.getDefault();
    }

    public static int siloBucketsPerBlock() {
        return SPEC.isLoaded() ? SILO_BUCKETS_PER_BLOCK.get() : SILO_BUCKETS_PER_BLOCK.getDefault();
    }

    public static int pylonRange() {
        return SPEC.isLoaded() ? PYLON_RANGE.get() : PYLON_RANGE.getDefault();
    }

    public static int pylonRate() {
        return SPEC.isLoaded() ? PYLON_RATE.get() : PYLON_RATE.getDefault();
    }

    public static int pylonBuffer() {
        return SPEC.isLoaded() ? PYLON_BUFFER.get() : PYLON_BUFFER.getDefault();
    }
}
