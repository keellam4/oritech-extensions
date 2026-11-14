package io.github.keellam4.oritechextensions.platform;

import net.minecraft.core.Registry;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import rearth.oritech.util.ColorableMachine.ColorVariant;

import java.util.ServiceLoader;
import java.util.function.Supplier;

/**
 * The few things each mod loader does its own way. Shared code calls {@link #INSTANCE}; each loader module
 * ships one implementation, found through {@code META-INF/services}.
 */
public interface Platform {

    Platform INSTANCE = ServiceLoader.load(Platform.class, Platform.class.getClassLoader()).findFirst()
            .orElseThrow(() -> new IllegalStateException("No Oritech Extensions platform implementation found"));

    /** Registers {@code factory} under {@code oritech_extensions:name}; the value is ready once registries are. */
    <T> Supplier<T> register(Registry<? super T> registry, String name, Supplier<T> factory);

    /** Paint stored on a machine addon block entity; ORANGE when it has none. */
    ColorVariant getAddonPaint(BlockEntity blockEntity);

    boolean hasAddonPaint(BlockEntity blockEntity);

    /** Stores paint on a machine addon block entity; ORANGE removes it. */
    void setAddonPaint(BlockEntity blockEntity, ColorVariant variant);

    void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);

    void sendToPlayersTrackingChunk(ServerLevel level, ChunkPos chunk, CustomPacketPayload payload);

    boolean isModLoaded(String modId);

    /** True on the game client (also while it hosts a singleplayer world), false on a dedicated server. */
    boolean isPhysicalClient();
}
