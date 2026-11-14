package io.github.keellam4.oritechextensions;

import io.github.keellam4.oritechextensions.guide.GuideBookItem;
import io.github.keellam4.oritechextensions.hangar.HangarDoorExtensionBlock;
import io.github.keellam4.oritechextensions.hangar.HangarDoorExtensionBlockEntity;
import io.github.keellam4.oritechextensions.paint.PaintColors;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import rearth.oritech.util.ColorableMachine;

import java.util.function.Supplier;

public class ModContent {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(OritechExtensions.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(OritechExtensions.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, OritechExtensions.MODID);

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, OritechExtensions.MODID);
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, OritechExtensions.MODID);

    // Same material properties as Oritech's hangar door blocks.
    public static final DeferredBlock<HangarDoorExtensionBlock> HANGAR_DOOR_EXTENSION = BLOCKS.register(
            "hangar_door_extension",
            () -> new HangarDoorExtensionBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_DOOR).strength(8f).forceSolidOn()));

    public static final DeferredItem<BlockItem> HANGAR_DOOR_EXTENSION_ITEM =
            ITEMS.registerSimpleBlockItem("hangar_door_extension", HANGAR_DOOR_EXTENSION);

    /** Opens this mod's wiki in Oracle Index (or posts a link to the website). Crafted from a book + nickel ingot. */
    public static final DeferredItem<GuideBookItem> GUIDE = ITEMS.registerItem(
            "guide", GuideBookItem::new, props -> props.stacksTo(1));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HangarDoorExtensionBlockEntity>> HANGAR_DOOR_EXTENSION_ENTITY =
            BLOCK_ENTITY_TYPES.register("hangar_door_extension",
                    () -> new BlockEntityType<>(HangarDoorExtensionBlockEntity::new, HANGAR_DOOR_EXTENSION.get()));

    /**
     * Paint colour stored on a block entity that Oritech itself can't paint (e.g. machine addons).
     * Saved with the block; absent means unpainted (Oritech's default orange).
     */
    public static final Supplier<AttachmentType<ColorableMachine.ColorVariant>> PAINT = ATTACHMENT_TYPES.register(
            "paint", () -> AttachmentType.builder(() -> ColorableMachine.ColorVariant.ORANGE).serialize(PaintColors.CODEC.fieldOf("paint")).build());

    /** Paint colour carried by a dropped block item, so a painted addon keeps its colour when moved. */
    public static final Supplier<DataComponentType<ColorableMachine.ColorVariant>> PAINT_COMPONENT = DATA_COMPONENTS.register(
            "paint", () -> DataComponentType.<ColorableMachine.ColorVariant>builder()
                    .persistent(PaintColors.CODEC)
                    .networkSynchronized(PaintColors.STREAM_CODEC)
                    .build());

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        ATTACHMENT_TYPES.register(modEventBus);
        DATA_COMPONENTS.register(modEventBus);
    }
}
