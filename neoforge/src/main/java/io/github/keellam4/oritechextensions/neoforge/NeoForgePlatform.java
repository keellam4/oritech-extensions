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
    public boolean interactWithFluidItem(net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand,
                                         io.github.keellam4.oritechextensions.storage.NetworkTank vault) {
        var held = player.getItemInHand(hand);
        if (player.level().isClientSide()) return net.neoforged.neoforge.fluids.FluidUtil.getFluidHandler(held).isPresent();
        return net.neoforged.neoforge.fluids.FluidUtil.interactWithFluidHandler(player, hand, new VaultFluidHandler(vault));
    }

    @Override
    public long insertFluid(ServerLevel level, net.minecraft.core.BlockPos pos, net.minecraft.core.Direction side,
                            net.minecraft.world.level.material.Fluid fluid, long max, boolean simulate) {
        var handler = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, pos, side);
        if (handler == null || max <= 0) return 0;
        var action = simulate ? net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE
                : net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
        return handler.fill(new net.neoforged.neoforge.fluids.FluidStack(fluid, (int) Math.min(Integer.MAX_VALUE, max)), action);
    }

    @Override
    public io.github.keellam4.oritechextensions.storage.FluidAmount extractFluid(ServerLevel level, net.minecraft.core.BlockPos pos,
                                                                               net.minecraft.core.Direction side,
                                                                               java.util.function.Predicate<net.minecraft.world.level.material.Fluid> filter,
                                                                               long max, boolean simulate) {
        var none = io.github.keellam4.oritechextensions.storage.FluidAmount.EMPTY;
        var handler = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, pos, side);
        if (handler == null || max <= 0) return none;
        var action = simulate ? net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE
                : net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            var inTank = handler.getFluidInTank(tank);
            if (inTank.isEmpty() || !filter.test(inTank.getFluid())) continue;
            var drained = handler.drain(inTank.copyWithAmount((int) Math.min(Integer.MAX_VALUE, max)), action);
            if (!drained.isEmpty()) return new io.github.keellam4.oritechextensions.storage.FluidAmount(drained.getFluid(), drained.getAmount());
        }
        return none;
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

    @Override
    public long chargeInventorySlot(net.minecraft.world.entity.player.Player player, int slot, long max) {
        return chargeStack(player.getInventory().getItem(slot), max);
    }

    /** Charges an item stack through its energy capability (the stack keeps its energy in a data component). */
    static long chargeStack(net.minecraft.world.item.ItemStack stack, long max) {
        if (stack.isEmpty() || max <= 0) return 0;
        var energy = stack.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.ITEM);
        if (energy == null || !energy.canReceive()) return 0;
        return energy.receiveEnergy((int) Math.min(max, Integer.MAX_VALUE), false);
    }

    @Override
    public long chargeAccessories(net.minecraft.world.entity.player.Player player, long max) {
        return ModList.get().isLoaded("curios") ? CuriosCharging.charge(player, max) : 0;
    }

    @Override
    public long itemEnergy(net.minecraft.world.item.ItemStack stack) {
        var energy = stack.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.ITEM);
        return energy == null ? -1 : energy.getEnergyStored();
    }
}
