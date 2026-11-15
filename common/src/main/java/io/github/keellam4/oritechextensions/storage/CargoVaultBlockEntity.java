package io.github.keellam4.oritechextensions.storage;

import io.github.keellam4.oritechextensions.Config;
import io.github.keellam4.oritechextensions.ModContent;
import io.github.keellam4.oritechextensions.paint.Paint;
import io.github.keellam4.oritechextensions.paint.PaintColors;
import io.github.keellam4.oritechextensions.paint.PipePaintData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Bulk storage for one item type. Loader code wraps this in an item handler (NeoForge) or item storage (Fabric)
 * so pipes can reach it; both go through {@link #insert} and {@link #extract}, or the raw setters inside
 * transactions.
 */
public class CargoVaultBlockEntity extends BlockEntity {

    private ItemStack item = ItemStack.EMPTY;
    private int count;
    private boolean locked;

    /** Paint the vault was placed with (from its item), picked up by PaintTargets.onBlockPlaced. */
    @Nullable
    private Paint placedPaint;

    /** Paint it had when a player broke it, remembered so the dropped item keeps it. */
    @Nullable
    private Paint brokenPaint;

    /** Capacity as the server sees it; synced so the client's fill gauge matches the server config. */
    private int syncedCapacity = -1;

    public CargoVaultBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.CARGO_VAULT_ENTITY.get(), pos, state);
    }

    // ---------------------------------------------------------------- contents

    public ItemStack item() {
        return item;
    }

    public int count() {
        return count;
    }

    public boolean isLocked() {
        return locked;
    }

    /** Items of one type this vault holds: {@code cargoVaultStacks} stacks of the stored (or offered) item. */
    public int capacity(ItemStack type) {
        if (level != null && level.isClientSide() && syncedCapacity >= 0) return syncedCapacity;
        int perStack = type.isEmpty() ? 64 : type.getMaxStackSize();
        return Config.cargoVaultStacks() * perStack;
    }

    public int capacity() {
        return capacity(item);
    }

    /** 0 to 1, for the fill gauge and comparators. */
    public float fill() {
        int capacity = capacity();
        return capacity <= 0 ? 0 : Math.min(1f, (float) count / capacity);
    }

    public boolean accepts(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (item.isEmpty()) return true;
        return ItemStack.isSameItemSameComponents(item, stack);
    }

    /** Puts up to {@code stack.getCount()} items in. Returns how many fit (and were taken unless {@code simulate}). */
    public int insert(ItemStack stack, boolean simulate) {
        if (!accepts(stack)) return 0;
        int moved = Math.min(stack.getCount(), capacity(item.isEmpty() ? stack : item) - count);
        if (moved <= 0) return 0;
        if (!simulate) setContents(item.isEmpty() ? stack : item, count + moved);
        return moved;
    }

    /** Takes up to {@code amount} items out (at most one stack). */
    public ItemStack extract(int amount, boolean simulate) {
        if (item.isEmpty() || count <= 0 || amount <= 0) return ItemStack.EMPTY;
        int moved = Math.min(Math.min(amount, count), item.getMaxStackSize());
        var out = item.copyWithCount(moved);
        if (!simulate) setContents(item, count - moved);
        return out;
    }

    /** Sets the contents and tells clients. An unlocked vault forgets its item type when it runs empty. */
    public void setContents(ItemStack type, int newCount) {
        setContentsQuietly(type, newCount);
        changed();
    }

    /** Sets the contents without syncing; for transactions that sync once they commit ({@link #changed}). */
    public void setContentsQuietly(ItemStack type, int newCount) {
        count = Math.max(0, newCount);
        if (count == 0 && !locked) {
            item = ItemStack.EMPTY;
        } else if (!type.isEmpty()) {
            item = type.copyWithCount(1);
        }
    }

    /** Puts back contents saved before a transaction that was rolled back (Fabric), keeping the item type exactly. */
    public void restoreContents(ItemStack type, int restoredCount) {
        item = type.isEmpty() ? ItemStack.EMPTY : type.copyWithCount(1);
        count = Math.max(0, restoredCount);
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
        if (!locked && count == 0) item = ItemStack.EMPTY;
        changed();
    }

    public VaultContents contents() {
        return new VaultContents(item, count, locked);
    }

    /** Saves and tells nearby clients (count text, fill gauge, comparators). */
    public void changed() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // ---------------------------------------------------------------- paint on the item

    @Nullable
    public Paint takePlacedPaint() {
        var paint = placedPaint;
        placedPaint = null;
        return paint;
    }

    public void rememberPaint(Paint paint) {
        brokenPaint = paint;
    }

    private Paint currentPaint() {
        if (brokenPaint != null) return brokenPaint;
        if (level instanceof ServerLevel server) return PipePaintData.get(server).getOrDefault(worldPosition);
        return Paint.ORANGE;
    }

    // ---------------------------------------------------------------- saving and syncing

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeContents(tag, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        var contents = VaultContents.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag.get("vault"))
                .result().orElse(VaultContents.EMPTY);
        item = contents.item();
        count = contents.count();
        locked = contents.locked();
        syncedCapacity = tag.contains("capacity", Tag.TAG_INT) ? tag.getInt("capacity") : -1;
    }

    private void writeContents(CompoundTag tag, HolderLookup.Provider registries) {
        VaultContents.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), contents())
                .result().ifPresent(encoded -> tag.put("vault", encoded));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        writeContents(tag, registries);
        tag.putInt("capacity", capacity());
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        var contents = input.getOrDefault(ModContent.VAULT_CONTENTS.get(), VaultContents.EMPTY);
        item = contents.item();
        count = contents.count();
        locked = contents.locked();
        placedPaint = input.get(ModContent.PAINT_COMPONENT.get());
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (!item.isEmpty() || locked) components.set(ModContent.VAULT_CONTENTS.get(), contents());
        var paint = currentPaint();
        if (PaintColors.isPainted(paint)) components.set(ModContent.PAINT_COMPONENT.get(), paint);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove("vault");
    }
}
