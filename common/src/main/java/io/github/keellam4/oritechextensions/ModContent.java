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

    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<io.github.keellam4.oritechextensions.storage.CargoControllerBlockEntity>> CARGO_CONTROLLER_ENTITY = PLATFORM.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, "cargo_controller",
            () -> BlockEntityType.Builder.of(io.github.keellam4.oritechextensions.storage.CargoControllerBlockEntity::new, CARGO_CONTROLLER.get()).build(null));

    public static final Supplier<net.minecraft.world.inventory.MenuType<io.github.keellam4.oritechextensions.storage.TerminalMenu>> TERMINAL_MENU =
            PLATFORM.register(BuiltInRegistries.MENU, "cargo_terminal",
                    () -> PLATFORM.menuType(io.github.keellam4.oritechextensions.storage.TerminalMenu::new));

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
}
