package io.github.keellam4.oritechextensions;

import io.github.keellam4.oritechextensions.guide.GuideBookItem;
import io.github.keellam4.oritechextensions.paint.RainbowCartridgeItem;
import io.github.keellam4.oritechextensions.tool.DetailingToolItem;
import io.github.keellam4.oritechextensions.storage.CargoVaultBlock;
import io.github.keellam4.oritechextensions.storage.CargoVaultBlockEntity;
import io.github.keellam4.oritechextensions.storage.CargoVaultItem;
import io.github.keellam4.oritechextensions.storage.VaultContents;
import io.github.keellam4.oritechextensions.storage.VaultLayout;
import io.github.keellam4.oritechextensions.storage.CompactingVaultBlockEntity;
import net.minecraft.world.level.block.SoundType;
import io.github.keellam4.oritechextensions.tool.ToolContents;
import io.github.keellam4.oritechextensions.hangar.HangarDoorExtensionBlock;
import io.github.keellam4.oritechextensions.hangar.HangarDoorExtensionBlockEntity;
import io.github.keellam4.oritechextensions.paint.PaintColors;
import io.github.keellam4.oritechextensions.platform.Platform;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import io.github.keellam4.oritechextensions.paint.Paint;

import java.util.function.Supplier;

/** Everything this mod registers. Loaded by each loader's entry point before registries freeze. */
public final class ModContent {

    private static final Platform PLATFORM = Platform.INSTANCE;

    // Same material properties as Oritech's hangar door blocks.
    public static final Supplier<HangarDoorExtensionBlock> HANGAR_DOOR_EXTENSION = PLATFORM.register(BuiltInRegistries.BLOCK,
            "hangar_door_extension",
            () -> new HangarDoorExtensionBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_DOOR).strength(8f).forceSolidOn()));

    public static final Supplier<BlockItem> HANGAR_DOOR_EXTENSION_ITEM = PLATFORM.register(BuiltInRegistries.ITEM,
            "hangar_door_extension", () -> new BlockItem(HANGAR_DOOR_EXTENSION.get(), new Item.Properties()));

    public static final Supplier<GuideBookItem> GUIDE = PLATFORM.register(BuiltInRegistries.ITEM,
            "guide", () -> new GuideBookItem(new Item.Properties().stacksTo(1)));

    /** Paints like a Color Cartridge, but the block keeps cycling through the colours. */
    public static final Supplier<RainbowCartridgeItem> RAINBOW_CARTRIDGE = PLATFORM.register(BuiltInRegistries.ITEM,
            "rainbow_cartridge", () -> new RainbowCartridgeItem(new Item.Properties()));

    /** Holds Color Cartridges and paints everything this mod and Oritech can paint. */
    public static final Supplier<DetailingToolItem> DETAILING_TOOL = PLATFORM.register(BuiltInRegistries.ITEM,
            "detailing_tool", () -> new DetailingToolItem(new Item.Properties().stacksTo(1)));

    /** Bulk storage for one item type. */
    public static final Supplier<CargoVaultBlock> CARGO_VAULT = vault("cargo_vault", VaultLayout.SINGLE);
    /** Two item types, half the space each. */
    public static final Supplier<CargoVaultBlock> DUAL_VAULT = vault("dual_vault", VaultLayout.DUAL);
    /** Four item types, a quarter of the space each. */
    public static final Supplier<CargoVaultBlock> QUAD_VAULT = vault("quad_vault", VaultLayout.QUAD);
    /** Sixteen item types, a sixteenth of the space each. */
    public static final Supplier<CargoVaultBlock> GRID_VAULT = vault("grid_vault", VaultLayout.GRID);
    /** One item in up to three forms (nugget, ingot, block): stored once, taken out in any form. */
    public static final Supplier<CargoVaultBlock> COMPACTING_VAULT = vault("compacting_vault", VaultLayout.COMPACTING);
    /** One item in two forms, for items that compact only once. */
    public static final Supplier<CargoVaultBlock> DUAL_COMPACTING_VAULT = vault("dual_compacting_vault", VaultLayout.COMPACTING_DUAL);
    /** A tank for one fluid. */
    public static final Supplier<CargoVaultBlock> FLUID_VAULT = vault("fluid_vault", VaultLayout.FLUID);

    public static final Supplier<CargoVaultItem> CARGO_VAULT_ITEM = vaultItem("cargo_vault", CARGO_VAULT);
    public static final Supplier<CargoVaultItem> DUAL_VAULT_ITEM = vaultItem("dual_vault", DUAL_VAULT);
    public static final Supplier<CargoVaultItem> QUAD_VAULT_ITEM = vaultItem("quad_vault", QUAD_VAULT);
    public static final Supplier<CargoVaultItem> GRID_VAULT_ITEM = vaultItem("grid_vault", GRID_VAULT);
    public static final Supplier<CargoVaultItem> COMPACTING_VAULT_ITEM = vaultItem("compacting_vault", COMPACTING_VAULT);
    public static final Supplier<CargoVaultItem> DUAL_COMPACTING_VAULT_ITEM = vaultItem("dual_compacting_vault", DUAL_COMPACTING_VAULT);
    public static final Supplier<CargoVaultItem> FLUID_VAULT_ITEM = vaultItem("fluid_vault", FLUID_VAULT);

    /** Upgrade cards for the vaults' upgrade screens, one per {@link VaultUpgrade}. */
    public static final java.util.Map<io.github.keellam4.oritechextensions.storage.VaultUpgrade, Supplier<io.github.keellam4.oritechextensions.storage.VaultUpgradeItem>> VAULT_UPGRADES = upgrades();

    public static final Supplier<net.minecraft.world.inventory.MenuType<io.github.keellam4.oritechextensions.storage.VaultUpgradeMenu>> VAULT_UPGRADE_MENU =
            PLATFORM.register(BuiltInRegistries.MENU, "vault_upgrades",
                    () -> PLATFORM.menuType(io.github.keellam4.oritechextensions.storage.VaultUpgradeMenu::new));

    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<CargoVaultBlockEntity>> CARGO_VAULT_ENTITY = PLATFORM.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, "cargo_vault",
            () -> BlockEntityType.Builder.of(CargoVaultBlockEntity::new, CARGO_VAULT.get(), DUAL_VAULT.get(), QUAD_VAULT.get(), GRID_VAULT.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<CompactingVaultBlockEntity>> COMPACTING_VAULT_ENTITY = PLATFORM.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, "compacting_vault",
            () -> BlockEntityType.Builder.of(CompactingVaultBlockEntity::new, COMPACTING_VAULT.get(), DUAL_COMPACTING_VAULT.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<io.github.keellam4.oritechextensions.storage.FluidVaultBlockEntity>> FLUID_VAULT_ENTITY = PLATFORM.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, "fluid_vault",
            () -> BlockEntityType.Builder.of(io.github.keellam4.oritechextensions.storage.FluidVaultBlockEntity::new, FLUID_VAULT.get()).build(null));

    /** A Fluid Vault's fluid, kept on its item. */
    public static final Supplier<DataComponentType<io.github.keellam4.oritechextensions.storage.FluidContents>> FLUID_CONTENTS = PLATFORM.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE, "fluid_contents", () -> DataComponentType.<io.github.keellam4.oritechextensions.storage.FluidContents>builder()
                    .persistent(io.github.keellam4.oritechextensions.storage.FluidContents.CODEC)
                    .networkSynchronized(io.github.keellam4.oritechextensions.storage.FluidContents.STREAM_CODEC)
                    .build());

    /** Links touching vaults into one inventory, with a terminal screen. */
    public static final Supplier<io.github.keellam4.oritechextensions.storage.CargoControllerBlock> CARGO_CONTROLLER = PLATFORM.register(
            BuiltInRegistries.BLOCK, "cargo_controller", () -> new io.github.keellam4.oritechextensions.storage.CargoControllerBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(3.5f, 6f).sound(SoundType.METAL)));

    public static final Supplier<BlockItem> CARGO_CONTROLLER_ITEM = PLATFORM.register(BuiltInRegistries.ITEM, "cargo_controller",
            () -> new BlockItem(CARGO_CONTROLLER.get(), new Item.Properties()));

    /** A terminal with a crafting grid, placed against any vault of a Cargo Wall. */
    public static final Supplier<io.github.keellam4.oritechextensions.storage.CraftingTerminalBlock> CRAFTING_TERMINAL = PLATFORM.register(
            BuiltInRegistries.BLOCK, "crafting_terminal", () -> new io.github.keellam4.oritechextensions.storage.CraftingTerminalBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(3.5f, 6f).sound(SoundType.METAL)));

    public static final Supplier<BlockItem> CRAFTING_TERMINAL_ITEM = PLATFORM.register(BuiltInRegistries.ITEM, "crafting_terminal",
            () -> new BlockItem(CRAFTING_TERMINAL.get(), new Item.Properties()));

    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<io.github.keellam4.oritechextensions.storage.CargoControllerBlockEntity>> CARGO_CONTROLLER_ENTITY = PLATFORM.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, "cargo_controller",
            () -> BlockEntityType.Builder.of(io.github.keellam4.oritechextensions.storage.CargoControllerBlockEntity::new, CARGO_CONTROLLER.get(), CRAFTING_TERMINAL.get()).build(null));

    // ---------------------------------------------------------------- Fluid Silo

    public static final Supplier<io.github.keellam4.oritechextensions.storage.SiloParts.Casing> SILO_CASING = PLATFORM.register(BuiltInRegistries.BLOCK, "silo_casing",
            () -> new io.github.keellam4.oritechextensions.storage.SiloParts.Casing(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(3.5f, 6f).sound(SoundType.METAL)));

    public static final Supplier<BlockItem> SILO_CASING_ITEM = PLATFORM.register(BuiltInRegistries.ITEM, "silo_casing",
            () -> new BlockItem(SILO_CASING.get(), new Item.Properties()));

    public static final Supplier<io.github.keellam4.oritechextensions.storage.SiloParts.Glass> SILO_GLASS = PLATFORM.register(BuiltInRegistries.BLOCK, "silo_glass",
            () -> new io.github.keellam4.oritechextensions.storage.SiloParts.Glass(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(3.5f, 6f)
                    .sound(SoundType.GLASS).noOcclusion().isViewBlocking((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false).isRedstoneConductor((state, level, pos) -> false)));

    public static final Supplier<BlockItem> SILO_GLASS_ITEM = PLATFORM.register(BuiltInRegistries.ITEM, "silo_glass",
            () -> new BlockItem(SILO_GLASS.get(), new Item.Properties()));

    public static final Supplier<io.github.keellam4.oritechextensions.storage.SiloControllerBlock> SILO_CONTROLLER = PLATFORM.register(
            BuiltInRegistries.BLOCK, "silo_controller", () -> new io.github.keellam4.oritechextensions.storage.SiloControllerBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(3.5f, 6f).sound(SoundType.METAL)));

    public static final Supplier<io.github.keellam4.oritechextensions.storage.SiloControllerItem> SILO_CONTROLLER_ITEM = PLATFORM.register(
            BuiltInRegistries.ITEM, "silo_controller",
            () -> new io.github.keellam4.oritechextensions.storage.SiloControllerItem(SILO_CONTROLLER.get(), new Item.Properties().stacksTo(1)));

    public static final Supplier<BlockEntityType<io.github.keellam4.oritechextensions.storage.SiloControllerBlockEntity>> SILO_CONTROLLER_ENTITY = PLATFORM.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, "silo_controller",
            () -> BlockEntityType.Builder.of(io.github.keellam4.oritechextensions.storage.SiloControllerBlockEntity::new, SILO_CONTROLLER.get()).build(null));

    public static final Supplier<net.minecraft.world.inventory.MenuType<io.github.keellam4.oritechextensions.storage.SiloMenu>> SILO_MENU =
            PLATFORM.register(BuiltInRegistries.MENU, "silo_controller",
                    () -> PLATFORM.menuType(io.github.keellam4.oritechextensions.storage.SiloMenu::new));

    private static Supplier<io.github.keellam4.oritechextensions.storage.PartitionUpgradeItem> partition(String name, int partitions) {
        return PLATFORM.register(BuiltInRegistries.ITEM, name,
                () -> new io.github.keellam4.oritechextensions.storage.PartitionUpgradeItem(partitions, new Item.Properties().stacksTo(16)));
    }

    public static final Supplier<io.github.keellam4.oritechextensions.storage.PartitionUpgradeItem> PARTITION_UPGRADE_1 = partition("partition_upgrade", 4);
    public static final Supplier<io.github.keellam4.oritechextensions.storage.PartitionUpgradeItem> PARTITION_UPGRADE_2 = partition("advanced_partition_upgrade", 9);
    public static final Supplier<io.github.keellam4.oritechextensions.storage.PartitionUpgradeItem> PARTITION_MATRIX =
            partition("partition_matrix", io.github.keellam4.oritechextensions.storage.PartitionUpgradeItem.UNLIMITED);

    // ---------------------------------------------------------------- Induction Pylon

    private static Supplier<io.github.keellam4.oritechextensions.wireless.InductionPylonBlock> pylon(String name, io.github.keellam4.oritechextensions.wireless.PylonStyle style) {
        return PLATFORM.register(BuiltInRegistries.BLOCK, name, () -> new io.github.keellam4.oritechextensions.wireless.InductionPylonBlock(style,
                BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(3.5f, 6f).sound(SoundType.METAL).noOcclusion()
                        .lightLevel(state -> 7)));
    }

    public static final Supplier<io.github.keellam4.oritechextensions.wireless.InductionPylonBlock> INDUCTION_PYLON =
            pylon("induction_pylon", io.github.keellam4.oritechextensions.wireless.PylonStyle.SPIRE);
    public static final Supplier<io.github.keellam4.oritechextensions.wireless.InductionPylonBlock> INDUCTION_PYLON_OBELISK =
            pylon("induction_pylon_obelisk", io.github.keellam4.oritechextensions.wireless.PylonStyle.OBELISK);
    public static final Supplier<io.github.keellam4.oritechextensions.wireless.InductionPylonBlock> INDUCTION_PYLON_RINGS =
            pylon("induction_pylon_rings", io.github.keellam4.oritechextensions.wireless.PylonStyle.RINGS);

    public static final Supplier<BlockItem> INDUCTION_PYLON_ITEM = PLATFORM.register(BuiltInRegistries.ITEM, "induction_pylon",
            () -> new net.minecraft.world.item.DoubleHighBlockItem(INDUCTION_PYLON.get(), new Item.Properties()));
    public static final Supplier<BlockItem> INDUCTION_PYLON_OBELISK_ITEM = PLATFORM.register(BuiltInRegistries.ITEM, "induction_pylon_obelisk",
            () -> new net.minecraft.world.item.DoubleHighBlockItem(INDUCTION_PYLON_OBELISK.get(), new Item.Properties()));
    public static final Supplier<BlockItem> INDUCTION_PYLON_RINGS_ITEM = PLATFORM.register(BuiltInRegistries.ITEM, "induction_pylon_rings",
            () -> new net.minecraft.world.item.DoubleHighBlockItem(INDUCTION_PYLON_RINGS.get(), new Item.Properties()));

    public static final Supplier<BlockEntityType<io.github.keellam4.oritechextensions.wireless.InductionPylonBlockEntity>> INDUCTION_PYLON_ENTITY = PLATFORM.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, "induction_pylon",
            () -> BlockEntityType.Builder.of(io.github.keellam4.oritechextensions.wireless.InductionPylonBlockEntity::new,
                    INDUCTION_PYLON.get(), INDUCTION_PYLON_OBELISK.get(), INDUCTION_PYLON_RINGS.get()).build(null));

    public static final Supplier<net.minecraft.world.inventory.MenuType<io.github.keellam4.oritechextensions.storage.TerminalMenu>> TERMINAL_MENU =
            PLATFORM.register(BuiltInRegistries.MENU, "cargo_terminal",
                    () -> PLATFORM.menuType(io.github.keellam4.oritechextensions.storage.TerminalMenu::new));

    public static final Supplier<net.minecraft.world.inventory.MenuType<io.github.keellam4.oritechextensions.storage.TerminalMenu>> CRAFTING_TERMINAL_MENU =
            PLATFORM.register(BuiltInRegistries.MENU, "crafting_terminal",
                    () -> PLATFORM.menuType(io.github.keellam4.oritechextensions.storage.TerminalMenu::crafting));

    /** A Cargo Vault's contents, kept on its item. */
    public static final Supplier<DataComponentType<VaultContents>> VAULT_CONTENTS = PLATFORM.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE, "vault_contents", () -> DataComponentType.<VaultContents>builder()
                    .persistent(VaultContents.CODEC)
                    .networkSynchronized(VaultContents.STREAM_CODEC)
                    .build());

    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<HangarDoorExtensionBlockEntity>> HANGAR_DOOR_EXTENSION_ENTITY = PLATFORM.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, "hangar_door_extension",
            () -> BlockEntityType.Builder.of(HangarDoorExtensionBlockEntity::new, HANGAR_DOOR_EXTENSION.get()).build(null));

    /** Paint remembered on a machine addon item, so a picked-up addon is placed back in its colour. */
    public static final Supplier<DataComponentType<Paint>> PAINT_COMPONENT = PLATFORM.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE, "paint", () -> DataComponentType.<Paint>builder()
                    .persistent(PaintColors.CODEC)
                    .networkSynchronized(PaintColors.STREAM_CODEC)
                    .build());

    /** What a Detailing Tool holds: paint charges, selected colour, mode, first corner of an area. */
    public static final Supplier<DataComponentType<ToolContents>> TOOL_CONTENTS = PLATFORM.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE, "tool_contents", () -> DataComponentType.<ToolContents>builder()
                    .persistent(ToolContents.CODEC)
                    .networkSynchronized(ToolContents.STREAM_CODEC)
                    .build());

    private static Supplier<CargoVaultBlock> vault(String name, VaultLayout layout) {
        return PLATFORM.register(BuiltInRegistries.BLOCK, name,
                () -> new CargoVaultBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(3.5f, 6f).sound(SoundType.METAL), layout));
    }

    private static Supplier<CargoVaultItem> vaultItem(String name, Supplier<CargoVaultBlock> block) {
        return PLATFORM.register(BuiltInRegistries.ITEM, name, () -> new CargoVaultItem(block.get(), new Item.Properties().stacksTo(1)));
    }

    private static java.util.Map<io.github.keellam4.oritechextensions.storage.VaultUpgrade, Supplier<io.github.keellam4.oritechextensions.storage.VaultUpgradeItem>> upgrades() {
        var map = new java.util.EnumMap<io.github.keellam4.oritechextensions.storage.VaultUpgrade, Supplier<io.github.keellam4.oritechextensions.storage.VaultUpgradeItem>>(
                io.github.keellam4.oritechextensions.storage.VaultUpgrade.class);
        for (var upgrade : io.github.keellam4.oritechextensions.storage.VaultUpgrade.values()) {
            map.put(upgrade, PLATFORM.register(BuiltInRegistries.ITEM, upgrade.id(),
                    () -> new io.github.keellam4.oritechextensions.storage.VaultUpgradeItem(upgrade, new Item.Properties().stacksTo(16))));
        }
        return java.util.Collections.unmodifiableMap(map);
    }

    private ModContent() {
    }

    /** Forces the fields above to register. */
    public static void init() {
    }

    // ---------------------------------------------------------------- Transparent Fluid Pipe

    /** Like Oritech's pipes. (Copying Oritech's block here would load Oritech's blocks before its config on Fabric.) */
    private static BlockBehaviour.Properties pipeProperties() {
        return BlockBehaviour.Properties.of().strength(0.6f, 1.5f).sound(SoundType.GLASS).noOcclusion()
                .isViewBlocking((state, level, pos) -> false).isSuffocating((state, level, pos) -> false);
    }

    public static final Supplier<io.github.keellam4.oritechextensions.pipes.TransparentFluidPipeBlock> TRANSPARENT_FLUID_PIPE = PLATFORM.register(
            BuiltInRegistries.BLOCK, "transparent_fluid_pipe", () -> new io.github.keellam4.oritechextensions.pipes.TransparentFluidPipeBlock(
                    pipeProperties()));

    public static final Supplier<io.github.keellam4.oritechextensions.pipes.TransparentFluidPipeConnectionBlock> TRANSPARENT_FLUID_PIPE_CONNECTION = PLATFORM.register(
            BuiltInRegistries.BLOCK, "transparent_fluid_pipe_connection", () -> new io.github.keellam4.oritechextensions.pipes.TransparentFluidPipeConnectionBlock(
                    pipeProperties()));

    public static final Supplier<BlockItem> TRANSPARENT_FLUID_PIPE_ITEM = PLATFORM.register(BuiltInRegistries.ITEM, "transparent_fluid_pipe",
            () -> new BlockItem(TRANSPARENT_FLUID_PIPE.get(), new Item.Properties()));

    // ---------------------------------------------------------------- creative tab

    /** The mod's own creative tab (JEI and EMI list what's in it). */
    public static final Supplier<net.minecraft.world.item.CreativeModeTab> CREATIVE_TAB = PLATFORM.register(BuiltInRegistries.CREATIVE_MODE_TAB, "main",
            () -> PLATFORM.creativeTab(net.minecraft.network.chat.Component.translatable("itemGroup.oritech_extensions"),
                    () -> new net.minecraft.world.item.ItemStack(CARGO_VAULT_ITEM.get()), (parameters, output) -> {
                        output.accept(GUIDE.get());
                        output.accept(DETAILING_TOOL.get());
                        output.accept(RAINBOW_CARTRIDGE.get());
                        output.accept(HANGAR_DOOR_EXTENSION_ITEM.get());
                        output.accept(TRANSPARENT_FLUID_PIPE_ITEM.get());
                        output.accept(CARGO_VAULT_ITEM.get());
                        output.accept(DUAL_VAULT_ITEM.get());
                        output.accept(QUAD_VAULT_ITEM.get());
                        output.accept(GRID_VAULT_ITEM.get());
                        output.accept(COMPACTING_VAULT_ITEM.get());
                        output.accept(DUAL_COMPACTING_VAULT_ITEM.get());
                        output.accept(FLUID_VAULT_ITEM.get());
                        output.accept(CARGO_CONTROLLER_ITEM.get());
                        output.accept(CRAFTING_TERMINAL_ITEM.get());
                        VAULT_UPGRADES.values().forEach(upgrade -> output.accept(upgrade.get()));
                        output.accept(SILO_CONTROLLER_ITEM.get());
                        output.accept(SILO_CASING_ITEM.get());
                        output.accept(SILO_GLASS_ITEM.get());
                        output.accept(PARTITION_UPGRADE_1.get());
                        output.accept(PARTITION_UPGRADE_2.get());
                        output.accept(PARTITION_MATRIX.get());
                        output.accept(INDUCTION_PYLON_ITEM.get());
                        output.accept(INDUCTION_PYLON_OBELISK_ITEM.get());
                        output.accept(INDUCTION_PYLON_RINGS_ITEM.get());
                    }));
}
