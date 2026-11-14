package io.github.keellam4.oritechextensions;

import io.github.keellam4.oritechextensions.guide.GuideBookItem;
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
import rearth.oritech.util.ColorableMachine.ColorVariant;

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

    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<HangarDoorExtensionBlockEntity>> HANGAR_DOOR_EXTENSION_ENTITY = PLATFORM.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, "hangar_door_extension",
            () -> BlockEntityType.Builder.of(HangarDoorExtensionBlockEntity::new, HANGAR_DOOR_EXTENSION.get()).build(null));

    /** Paint remembered on a machine addon item, so a picked-up addon is placed back in its colour. */
    public static final Supplier<DataComponentType<ColorVariant>> PAINT_COMPONENT = PLATFORM.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE, "paint", () -> DataComponentType.<ColorVariant>builder()
                    .persistent(PaintColors.CODEC)
                    .networkSynchronized(PaintColors.STREAM_CODEC)
                    .build());

    private ModContent() {
    }

    /** Forces the fields above to register. */
    public static void init() {
    }
}
