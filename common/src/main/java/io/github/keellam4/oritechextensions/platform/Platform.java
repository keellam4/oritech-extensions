package io.github.keellam4.oritechextensions.platform;

import net.minecraft.core.Registry;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import io.github.keellam4.oritechextensions.paint.Paint;

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
    Paint getAddonPaint(BlockEntity blockEntity);

    boolean hasAddonPaint(BlockEntity blockEntity);

    /** Stores paint on a machine addon block entity; ORANGE removes it. */
    void setAddonPaint(BlockEntity blockEntity, Paint variant);

    void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);

    void sendToPlayersTrackingChunk(ServerLevel level, ChunkPos chunk, CustomPacketPayload payload);

    /** Client only: sends {@code payload} to the server. */
    void sendToServer(CustomPacketPayload payload);

    /** A menu type whose client side is made with {@code factory} (no extra data). */
    <T extends net.minecraft.world.inventory.AbstractContainerMenu> net.minecraft.world.inventory.MenuType<T> menuType(
            java.util.function.BiFunction<Integer, net.minecraft.world.entity.player.Inventory, T> factory);

    /**
     * Inserts {@code stack} into the inventory at {@code pos}, reached from {@code side}. Returns how many fit
     * (moved unless {@code simulate}).
     */
    int insertItem(ServerLevel level, net.minecraft.core.BlockPos pos, net.minecraft.core.Direction side,
                   net.minecraft.world.item.ItemStack stack, boolean simulate);

    /**
     * Takes up to {@code max} items of the first type matching {@code filter} out of the inventory at {@code pos},
     * reached from {@code side}. Returns them (taken unless {@code simulate}), or an empty stack.
     */
    net.minecraft.world.item.ItemStack extractItem(ServerLevel level, net.minecraft.core.BlockPos pos, net.minecraft.core.Direction side,
                                                   java.util.function.Predicate<net.minecraft.world.item.ItemStack> filter, int max, boolean simulate);

    /** Fills or empties a held fluid container (buckets, tanks) from or into the Fluid Vault. Returns whether anything happened. */
    boolean interactWithFluidItem(net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand,
                                  io.github.keellam4.oritechextensions.storage.NetworkTank vault);

    /** Fills the fluid handler at {@code pos} (reached from {@code side}) with up to {@code max} millibuckets. Returns how much went in. */
    long insertFluid(ServerLevel level, net.minecraft.core.BlockPos pos, net.minecraft.core.Direction side,
                     net.minecraft.world.level.material.Fluid fluid, long max, boolean simulate);

    /** Drains up to {@code max} millibuckets of the first fluid matching {@code filter} from the handler at {@code pos}. */
    io.github.keellam4.oritechextensions.storage.FluidAmount extractFluid(ServerLevel level, net.minecraft.core.BlockPos pos,
                                                                        net.minecraft.core.Direction side,
                                                                        java.util.function.Predicate<net.minecraft.world.level.material.Fluid> filter,
                                                                        long max, boolean simulate);

    boolean isModLoaded(String modId);

    /** Charges the energy item in a slot of the player's inventory (0-35 main, 36-39 armour, 40 offhand). Returns RF given. */
    long chargeInventorySlot(net.minecraft.world.entity.player.Player player, int slot, long max);

    /** Charges energy items in accessory slots (Curios), when that mod is installed. Returns RF given. */
    long chargeAccessories(net.minecraft.world.entity.player.Player player, long max);

    /** The energy an item holds, or -1 if it doesn't hold energy. */
    long itemEnergy(net.minecraft.world.item.ItemStack stack);

    /** True on the game client (also while it hosts a singleplayer world), false on a dedicated server. */
    boolean isPhysicalClient();
}
