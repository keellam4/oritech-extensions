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
import rearth.oritech.util.ColorableMachine.ColorVariant;

import java.util.function.Supplier;

/** Fabric side of {@link Platform}. */
public final class FabricPlatform implements Platform {

    /** Paint on a machine addon block entity. Saved with the block entity under "fabric:attachments". */
    public static final AttachmentType<ColorVariant> PAINT = AttachmentRegistry.<ColorVariant>builder()
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
    public ColorVariant getAddonPaint(BlockEntity blockEntity) {
        var paint = ((AttachmentTarget) blockEntity).getAttached(PAINT);
        return paint != null ? paint : ColorVariant.ORANGE;
    }

    @Override
    public boolean hasAddonPaint(BlockEntity blockEntity) {
        return ((AttachmentTarget) blockEntity).hasAttached(PAINT);
    }

    @Override
    public void setAddonPaint(BlockEntity blockEntity, ColorVariant variant) {
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
