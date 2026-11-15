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
}
