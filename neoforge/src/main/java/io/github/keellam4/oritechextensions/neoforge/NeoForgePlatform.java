package io.github.keellam4.oritechextensions.neoforge;

import io.github.keellam4.oritechextensions.OritechExtensions;
import io.github.keellam4.oritechextensions.paint.PaintColors;
import io.github.keellam4.oritechextensions.platform.Platform;
import net.minecraft.core.Registry;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import io.github.keellam4.oritechextensions.paint.Paint;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** NeoForge side of {@link Platform}. */
public final class NeoForgePlatform implements Platform {

    private static final Map<ResourceKey<?>, DeferredRegister<?>> REGISTERS = new LinkedHashMap<>();

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, OritechExtensions.MODID);

    /** Paint on a machine addon block entity. Saved with the block entity under "neoforge:attachments". */
    public static final Supplier<AttachmentType<Paint>> PAINT = ATTACHMENT_TYPES.register(
            "paint", () -> AttachmentType.builder(() -> Paint.ORANGE).serialize(PaintColors.CODEC).build());

    /** Adds every register to the mod bus. Call after {@code ModContent.init()}. */
    static void registerAll(IEventBus modBus) {
        REGISTERS.values().forEach(register -> register.register(modBus));
        ATTACHMENT_TYPES.register(modBus);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Supplier<T> register(Registry<? super T> registry, String name, Supplier<T> factory) {
        var register = (DeferredRegister<Object>) REGISTERS.computeIfAbsent(registry.key(),
                key -> DeferredRegister.create((ResourceKey<Registry<Object>>) key, OritechExtensions.MODID));
        var holder = register.register(name, factory::get);
        return () -> (T) holder.get();
    }

    @Override
    public Paint getAddonPaint(BlockEntity blockEntity) {
        return blockEntity.hasData(PAINT) ? blockEntity.getData(PAINT) : Paint.ORANGE;
    }

    @Override
    public boolean hasAddonPaint(BlockEntity blockEntity) {
        return blockEntity.hasData(PAINT);
    }

    @Override
    public void setAddonPaint(BlockEntity blockEntity, Paint variant) {
        if (PaintColors.isPainted(variant)) {
            blockEntity.setData(PAINT, variant);
        } else {
            blockEntity.removeData(PAINT);
        }
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    @Override
    public <T extends net.minecraft.world.inventory.AbstractContainerMenu> net.minecraft.world.inventory.MenuType<T> menuType(
            java.util.function.BiFunction<Integer, net.minecraft.world.entity.player.Inventory, T> factory) {
        return net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create((id, inventory, data) -> factory.apply(id, inventory));
    }

    @Override
    public int insertItem(ServerLevel level, net.minecraft.core.BlockPos pos, net.minecraft.core.Direction side,
                          net.minecraft.world.item.ItemStack stack, boolean simulate) {
        var handler = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, pos, side);
        if (handler == null || stack.isEmpty()) return 0;
        var rest = net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(handler, stack.copy(), simulate);
        return stack.getCount() - rest.getCount();
    }

    @Override
    public net.minecraft.world.item.ItemStack extractItem(ServerLevel level, net.minecraft.core.BlockPos pos, net.minecraft.core.Direction side,
                                                          java.util.function.Predicate<net.minecraft.world.item.ItemStack> filter, int max, boolean simulate) {
        var handler = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, pos, side);
        if (handler == null || max <= 0) return net.minecraft.world.item.ItemStack.EMPTY;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            var inSlot = handler.getStackInSlot(slot);
            if (inSlot.isEmpty() || !filter.test(inSlot)) continue;
            var taken = handler.extractItem(slot, max, simulate);
            if (!taken.isEmpty()) return taken;
        }
        return net.minecraft.world.item.ItemStack.EMPTY;
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    @Override
    public void sendToPlayersTrackingChunk(ServerLevel level, ChunkPos chunk, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayersTrackingChunk(level, chunk, payload);
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public boolean isPhysicalClient() {
        return FMLEnvironment.dist.isClient();
    }
}
