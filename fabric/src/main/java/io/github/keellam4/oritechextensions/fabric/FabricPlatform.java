package io.github.keellam4.oritechextensions.fabric;

import io.github.keellam4.oritechextensions.OritechExtensions;
import io.github.keellam4.oritechextensions.paint.PaintColors;
import io.github.keellam4.oritechextensions.platform.Platform;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import io.github.keellam4.oritechextensions.paint.Paint;

import java.util.function.Supplier;

/** Fabric side of {@link Platform}. */
public final class FabricPlatform implements Platform {

    /** Paint on a machine addon block entity. Saved with the block entity under "fabric:attachments". */
    public static final AttachmentType<Paint> PAINT = AttachmentRegistry.<Paint>builder()
            .persistent(PaintColors.CODEC)
            .buildAndRegister(OritechExtensions.id("paint"));

    @Override
    @SuppressWarnings("unchecked")
    public <T> Supplier<T> register(Registry<? super T> registry, String name, Supplier<T> factory) {
        // Fabric registers straight away (registries are still open while mods initialise).
        T value = (T) Registry.register((Registry<Object>) registry, OritechExtensions.id(name), factory.get());
        return () -> value;
    }

    @Override
    public Paint getAddonPaint(BlockEntity blockEntity) {
        var paint = ((AttachmentTarget) blockEntity).getAttached(PAINT);
        return paint != null ? paint : Paint.ORANGE;
    }

    @Override
    public boolean hasAddonPaint(BlockEntity blockEntity) {
        return ((AttachmentTarget) blockEntity).hasAttached(PAINT);
    }

    @Override
    public void setAddonPaint(BlockEntity blockEntity, Paint variant) {
        var target = (AttachmentTarget) blockEntity;
        if (PaintColors.isPainted(variant)) {
            target.setAttached(PAINT, variant);
        } else {
            target.removeAttached(PAINT);
        }
        blockEntity.setChanged();
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    @Override
    public net.minecraft.world.item.CreativeModeTab creativeTab(net.minecraft.network.chat.Component title,
            java.util.function.Supplier<net.minecraft.world.item.ItemStack> icon, net.minecraft.world.item.CreativeModeTab.DisplayItemsGenerator items) {
        return net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup.builder().title(title).icon(icon).displayItems(items).build();
    }

    @Override
    public <T extends net.minecraft.world.inventory.AbstractContainerMenu> net.minecraft.world.inventory.MenuType<T> menuType(
            java.util.function.BiFunction<Integer, net.minecraft.world.entity.player.Inventory, T> factory) {
        return new net.minecraft.world.inventory.MenuType<>(factory::apply, net.minecraft.world.flag.FeatureFlags.VANILLA_SET);
    }

    @Override
    public int insertItem(ServerLevel level, net.minecraft.core.BlockPos pos, net.minecraft.core.Direction side,
                          net.minecraft.world.item.ItemStack stack, boolean simulate) {
        var storage = net.fabricmc.fabric.api.transfer.v1.item.ItemStorage.SIDED.find(level, pos, side);
        if (storage == null || stack.isEmpty()) return 0;
        try (var transaction = net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
            long moved = storage.insert(net.fabricmc.fabric.api.transfer.v1.item.ItemVariant.of(stack), stack.getCount(), transaction);
            if (!simulate) transaction.commit();
            return (int) moved;
        }
    }

    @Override
    public net.minecraft.world.item.ItemStack extractItem(ServerLevel level, net.minecraft.core.BlockPos pos, net.minecraft.core.Direction side,
                                                          java.util.function.Predicate<net.minecraft.world.item.ItemStack> filter, int max, boolean simulate) {
        var storage = net.fabricmc.fabric.api.transfer.v1.item.ItemStorage.SIDED.find(level, pos, side);
        if (storage == null || max <= 0) return net.minecraft.world.item.ItemStack.EMPTY;
        try (var transaction = net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
            for (var view : storage.nonEmptyViews()) {
                var resource = view.getResource();
                if (!filter.test(resource.toStack())) continue;
                long taken = view.extract(resource, max, transaction);
                if (taken > 0) {
                    if (!simulate) transaction.commit();
                    return resource.toStack((int) taken);
                }
            }
        }
        return net.minecraft.world.item.ItemStack.EMPTY;
    }

    @Override
    public boolean interactWithFluidItem(net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand,
                                         io.github.keellam4.oritechextensions.storage.NetworkTank vault) {
        if (player.level().isClientSide()) {
            return net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext.forPlayerInteraction(player, hand)
                    .find(net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage.ITEM) != null;
        }
        return net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorageUtil.interactWithFluidStorage(new VaultFluidStorage(vault), player, hand);
    }

    @Override
    public long insertFluid(ServerLevel level, net.minecraft.core.BlockPos pos, net.minecraft.core.Direction side,
                            net.minecraft.world.level.material.Fluid fluid, long max, boolean simulate) {
        var storage = net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage.SIDED.find(level, pos, side);
        if (storage == null || max <= 0) return 0;
        try (var transaction = net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
            long droplets = storage.insert(net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant.of(fluid), max * VaultFluidStorage.DROPLETS_PER_MB, transaction);
            long mb = droplets / VaultFluidStorage.DROPLETS_PER_MB;
            if (!simulate && droplets % VaultFluidStorage.DROPLETS_PER_MB == 0) transaction.commit();
            return droplets % VaultFluidStorage.DROPLETS_PER_MB == 0 ? mb : 0;
        }
    }

    @Override
    public io.github.keellam4.oritechextensions.storage.FluidAmount extractFluid(ServerLevel level, net.minecraft.core.BlockPos pos,
                                                                               net.minecraft.core.Direction side,
                                                                               java.util.function.Predicate<net.minecraft.world.level.material.Fluid> filter,
                                                                               long max, boolean simulate) {
        var none = io.github.keellam4.oritechextensions.storage.FluidAmount.EMPTY;
        var storage = net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage.SIDED.find(level, pos, side);
        if (storage == null || max <= 0) return none;
        try (var transaction = net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
            for (var view : storage.nonEmptyViews()) {
                var resource = view.getResource();
                if (!filter.test(resource.getFluid())) continue;
                long droplets = view.extract(resource, max * VaultFluidStorage.DROPLETS_PER_MB, transaction);
                if (droplets <= 0 || droplets % VaultFluidStorage.DROPLETS_PER_MB != 0) return none;
                if (!simulate) transaction.commit();
                return new io.github.keellam4.oritechextensions.storage.FluidAmount(resource.getFluid(), droplets / VaultFluidStorage.DROPLETS_PER_MB);
            }
        }
        return none;
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        // Only called on the client; the class is only loaded when this runs.
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(payload);
    }

    @Override
    public void sendToPlayersTrackingChunk(ServerLevel level, ChunkPos chunk, CustomPacketPayload payload) {
        for (var player : PlayerLookup.tracking(level, chunk)) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public boolean isPhysicalClient() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    }

    @Override
    public long chargeInventorySlot(net.minecraft.world.entity.player.Player player, int slot, long max) {
        if (max <= 0 || player.getInventory().getItem(slot).isEmpty()) return 0;
        var storage = net.fabricmc.fabric.api.transfer.v1.item.PlayerInventoryStorage.of(player);
        var context = net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext.ofPlayerSlot(player, storage.getSlots().get(slot));
        var energy = context.find(team.reborn.energy.api.EnergyStorage.ITEM);
        if (energy == null || !energy.supportsInsertion()) return 0;
        try (var transaction = net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
            long moved = energy.insert(max, transaction);
            transaction.commit();
            return moved;
        }
    }

    @Override
    public long chargeAccessories(net.minecraft.world.entity.player.Player player, long max) {
        return 0; // Trinkets / Accessories support comes later.
    }

    @Override
    public long itemEnergy(net.minecraft.world.item.ItemStack stack) {
        var energy = net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext.withConstant(stack).find(team.reborn.energy.api.EnergyStorage.ITEM);
        return energy == null ? -1 : energy.getAmount();
    }
}
