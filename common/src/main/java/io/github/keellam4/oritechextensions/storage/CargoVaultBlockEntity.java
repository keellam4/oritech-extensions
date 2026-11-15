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

import java.util.ArrayList;

/**
 * Bulk storage in one or more slots, each holding one item type: 1 for the Cargo Vault, 4 for the Quad Vault, 16
 * for the Grid Vault. All slots together hold {@code cargoVaultStacks} stacks, split evenly.
 * <p>
 * An item type lives in one slot only: an empty slot doesn't take an item another slot already holds. Loader code
 * wraps this in an item handler (NeoForge) or item storage (Fabric) for pipes.
 */
public class CargoVaultBlockEntity extends BlockEntity {

    private ItemStack[] items;
    private int[] counts;
    private boolean locked;

    /** Paint the vault was placed with (from its item), picked up by PaintTargets.onBlockPlaced. */
    @Nullable
    private Paint placedPaint;

    /** Paint it had when a player broke it, remembered so the dropped item keeps it. */
    @Nullable
    private Paint brokenPaint;

    /** Stacks per slot as the server sees them; synced so the client's gauges and counter match the server config. */
    private int syncedStacksPerSlot = -1;

    public CargoVaultBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.CARGO_VAULT_ENTITY.get(), pos, state);
        int slots = state.getBlock() instanceof CargoVaultBlock vault ? vault.slots() : 1;
        items = new ItemStack[slots];
        java.util.Arrays.fill(items, ItemStack.EMPTY);
        counts = new int[slots];
    }

    // ---------------------------------------------------------------- slots

    public int slots() {
        return items.length;
    }

    public ItemStack item(int slot) {
        return items[slot];
    }

    public int count(int slot) {
        return counts[slot];
    }

    public boolean isLocked() {
        return locked;
    }

    /** Stacks one slot holds: the vault's total split evenly between its slots (at least one stack). */
    public int stacksPerSlot() {
        if (level != null && level.isClientSide() && syncedStacksPerSlot > 0) return syncedStacksPerSlot;
        return Math.max(1, Config.cargoVaultStacks() / slots());
    }

    /** Items of {@code type} (or of the slot's own item) one slot holds. */
    public int capacity(int slot, ItemStack type) {
        var item = items[slot].isEmpty() ? type : items[slot];
        int perStack = item.isEmpty() ? 64 : item.getMaxStackSize();
        return stacksPerSlot() * perStack;
    }

    public int capacity(int slot) {
        return capacity(slot, ItemStack.EMPTY);
    }

    /** 0 to 1, for the slot's gauge. */
    public float fill(int slot) {
        int capacity = capacity(slot);
        return capacity <= 0 ? 0 : Math.min(1f, (float) counts[slot] / capacity);
    }

    /** 0 to 1 over all slots, for comparators. */
    public float fill() {
        long count = 0;
        long capacity = 0;
        for (int i = 0; i < slots(); i++) {
            count += counts[i];
            capacity += capacity(i);
        }
        return capacity <= 0 ? 0 : Math.min(1f, (float) count / capacity);
    }

    public boolean isEmpty() {
        for (int count : counts) {
            if (count > 0) return false;
        }
        return true;
    }

    /** The slot holding {@code stack}'s item, or -1. */
    public int slotOf(ItemStack stack) {
        for (int i = 0; i < slots(); i++) {
            if (!items[i].isEmpty() && ItemStack.isSameItemSameComponents(items[i], stack)) return i;
        }
        return -1;
    }

    /** Whether {@code slot} takes {@code stack}: its own item, or (when empty and unlocked) an item no other slot holds. */
    public boolean accepts(int slot, ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (!items[slot].isEmpty()) return ItemStack.isSameItemSameComponents(items[slot], stack);
        return !locked && slotOf(stack) < 0;
    }

    /** Whether any slot takes {@code stack}. */
    public boolean accepts(ItemStack stack) {
        for (int i = 0; i < slots(); i++) {
            if (accepts(i, stack)) return true;
        }
        return false;
    }

    /** Puts up to {@code stack.getCount()} items into {@code slot}. Returns how many fit. */
    public int insert(int slot, ItemStack stack, boolean simulate) {
        if (!accepts(slot, stack)) return 0;
        int moved = Math.min(stack.getCount(), capacity(slot, stack) - counts[slot]);
        if (moved <= 0) return 0;
        if (!simulate) setSlot(slot, items[slot].isEmpty() ? stack : items[slot], counts[slot] + moved);
        return moved;
    }

    /** Puts items into the slot holding them, or else the first empty slot. Returns how many fit. */
    public int insert(ItemStack stack, boolean simulate) {
        int slot = slotOf(stack);
        if (slot < 0) {
            for (int i = 0; i < slots() && slot < 0; i++) {
                if (accepts(i, stack)) slot = i;
            }
        }
        return slot < 0 ? 0 : insert(slot, stack, simulate);
    }

    /** Takes up to {@code amount} items (at most one stack) out of {@code slot}. */
    public ItemStack extract(int slot, int amount, boolean simulate) {
        if (items[slot].isEmpty() || counts[slot] <= 0 || amount <= 0) return ItemStack.EMPTY;
        int moved = Math.min(Math.min(amount, counts[slot]), items[slot].getMaxStackSize());
        var out = items[slot].copyWithCount(moved);
        if (!simulate) setSlot(slot, items[slot], counts[slot] - moved);
        return out;
    }

    /** Sets a slot and tells clients. An unlocked slot forgets its item type when it runs empty. */
    public void setSlot(int slot, ItemStack type, int newCount) {
        setSlotQuietly(slot, type, newCount);
        changed();
    }

    /** Sets a slot without syncing; for transactions that sync once they commit ({@link #changed}). */
    public void setSlotQuietly(int slot, ItemStack type, int newCount) {
        counts[slot] = Math.max(0, newCount);
        if (counts[slot] == 0 && !locked) {
            items[slot] = ItemStack.EMPTY;
        } else if (!type.isEmpty()) {
            items[slot] = type.copyWithCount(1);
        }
    }

    /** Puts back a slot saved before a transaction that was rolled back (Fabric), keeping the item type exactly. */
    public void restoreSlot(int slot, ItemStack type, int restoredCount) {
        items[slot] = type.isEmpty() ? ItemStack.EMPTY : type.copyWithCount(1);
        counts[slot] = Math.max(0, restoredCount);
    }

    /** Locking keeps every slot's item type when it runs empty, so the vault only takes those items. */
    public void setLocked(boolean locked) {
        this.locked = locked;
        if (!locked) {
            for (int i = 0; i < slots(); i++) {
                if (counts[i] == 0) items[i] = ItemStack.EMPTY;
            }
        }
        changed();
    }

    public boolean hasAnyItemType() {
        for (var item : items) {
            if (!item.isEmpty()) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- single-slot shortcuts (the Cargo Vault)

    public ItemStack item() {
        return item(0);
    }

    public int count() {
        return count(0);
    }

    public int capacity() {
        return capacity(0);
    }

    public void setContents(ItemStack type, int newCount) {
        setSlot(0, type, newCount);
    }

    /** Takes from the first slot that has items. */
    public ItemStack extract(int amount, boolean simulate) {
        for (int i = 0; i < slots(); i++) {
            if (counts[i] > 0) return extract(i, amount, simulate);
        }
        return ItemStack.EMPTY;
    }

    // ---------------------------------------------------------------- contents as a whole

    public VaultContents contents() {
        var slots = new ArrayList<VaultContents.Slot>(slots());
        for (int i = 0; i < slots(); i++) slots.add(new VaultContents.Slot(items[i], counts[i]));
        return new VaultContents(slots, locked);
    }

    private void load(VaultContents contents) {
        for (int i = 0; i < slots(); i++) {
            var slot = contents.slot(i);
            items[i] = slot.item();
            counts[i] = slot.count();
        }
        locked = contents.locked();
    }

    /** Saves and tells nearby clients (counts, gauges, comparators). */
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
        load(VaultContents.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag.get("vault"))
                .result().orElse(VaultContents.EMPTY));
        syncedStacksPerSlot = tag.contains("stacks_per_slot", Tag.TAG_INT) ? tag.getInt("stacks_per_slot") : -1;
    }

    private void writeContents(CompoundTag tag, HolderLookup.Provider registries) {
        VaultContents.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), contents())
                .result().ifPresent(encoded -> tag.put("vault", encoded));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        writeContents(tag, registries);
        tag.putInt("stacks_per_slot", stacksPerSlot());
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        load(input.getOrDefault(ModContent.VAULT_CONTENTS.get(), VaultContents.EMPTY));
        placedPaint = input.get(ModContent.PAINT_COMPONENT.get());
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (hasAnyItemType() || locked) components.set(ModContent.VAULT_CONTENTS.get(), contents());
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
