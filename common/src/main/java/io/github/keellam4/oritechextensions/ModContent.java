package io.github.keellam4.oritechextensions;

import io.github.keellam4.oritechextensions.guide.GuideBookItem;
import io.github.keellam4.oritechextensions.paint.RainbowCartridgeItem;
import io.github.keellam4.oritechextensions.tool.DetailingToolItem;
import io.github.keellam4.oritechextensions.storage.CargoVaultBlock;
import io.github.keellam4.oritechextensions.storage.CargoVaultBlockEntity;
import io.github.keellam4.oritechextensions.storage.CargoVaultItem;
import io.github.keellam4.oritechextensions.storage.VaultContents;
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
    public static final Supplier<CargoVaultBlock> CARGO_VAULT = PLATFORM.register(BuiltInRegistries.BLOCK, "cargo_vault",
            () -> new CargoVaultBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(3.5f, 6f).sound(SoundType.METAL)));

    public static final Supplier<CargoVaultItem> CARGO_VAULT_ITEM = PLATFORM.register(BuiltInRegistries.ITEM,
            "cargo_vault", () -> new CargoVaultItem(CARGO_VAULT.get(), new Item.Properties().stacksTo(1)));

    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<CargoVaultBlockEntity>> CARGO_VAULT_ENTITY = PLATFORM.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, "cargo_vault",
            () -> BlockEntityType.Builder.of(CargoVaultBlockEntity::new, CARGO_VAULT.get()).build(null));

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

    private ModContent() {
    }

    /** Forces the fields above to register. */
    public static void init() {
    }
}
