package dev.garciajg.oritechextensions;

import dev.garciajg.oritechextensions.hangar.HangarDoorExtensionBlock;
import dev.garciajg.oritechextensions.hangar.HangarDoorExtensionBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModContent {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(OritechExtensions.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(OritechExtensions.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, OritechExtensions.MODID);

    // Same material properties as Oritech's hangar door blocks.
    public static final DeferredBlock<HangarDoorExtensionBlock> HANGAR_DOOR_EXTENSION = BLOCKS.register(
            "hangar_door_extension",
            () -> new HangarDoorExtensionBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_DOOR).strength(8f).forceSolidOn()));

    public static final DeferredItem<BlockItem> HANGAR_DOOR_EXTENSION_ITEM =
            ITEMS.registerSimpleBlockItem("hangar_door_extension", HANGAR_DOOR_EXTENSION);

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HangarDoorExtensionBlockEntity>> HANGAR_DOOR_EXTENSION_ENTITY =
            BLOCK_ENTITY_TYPES.register("hangar_door_extension",
                    () -> BlockEntityType.Builder.of(HangarDoorExtensionBlockEntity::new, HANGAR_DOOR_EXTENSION.get()).build(null));

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
    }
}
