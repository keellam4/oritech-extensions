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
import rearth.oritech.util.ColorableMachine.ColorVariant;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** NeoForge side of {@link Platform}. */
public final class NeoForgePlatform implements Platform {

    private static final Map<ResourceKey<?>, DeferredRegister<?>> REGISTERS = new LinkedHashMap<>();

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, OritechExtensions.MODID);

    /** Paint on a machine addon block entity. Saved with the block entity under "neoforge:attachments". */
    public static final Supplier<AttachmentType<ColorVariant>> PAINT = ATTACHMENT_TYPES.register(
            "paint", () -> AttachmentType.builder(() -> ColorVariant.ORANGE).serialize(PaintColors.CODEC).build());

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
    public ColorVariant getAddonPaint(BlockEntity blockEntity) {
        return blockEntity.hasData(PAINT) ? blockEntity.getData(PAINT) : ColorVariant.ORANGE;
    }

    @Override
    public boolean hasAddonPaint(BlockEntity blockEntity) {
        return blockEntity.hasData(PAINT);
    }

    @Override
    public void setAddonPaint(BlockEntity blockEntity, ColorVariant variant) {
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
