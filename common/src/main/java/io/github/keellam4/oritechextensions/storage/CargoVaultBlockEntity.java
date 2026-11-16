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

    /** The vault's size in stacks as the server sees it; synced so the client's gauges and readout match the server config. */
    private int syncedVaultStacks = -1;

    /** The upgrade screen's slots, one per {@link VaultUpgrade.Kind}. */
    private final net.minecraft.world.SimpleContainer upgrades = new net.minecraft.world.SimpleContainer(UPGRADE_SLOTS) {
        @Override
        public int getMaxStackSize() {
            return VaultUpgrade.Kind.CAPACITY.limit();
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return stack.getItem() instanceof VaultUpgradeItem item && item.upgrade().kind().slot() == slot;
        }
    };
    /** Sides (bits of {@link VaultSide}) the Pull and Push upgrades work on. */
    private int pullSides = VaultSide.BACK.bit();
    private int pushSides = VaultSide.BACK.bit();
    private int multiplier = 1;
    private final java.util.EnumSet<VaultUpgrade> installed = java.util.EnumSet.noneOf(VaultUpgrade.class);
    private int lastSignal;

    public static final int UPGRADE_SLOTS = VaultUpgrade.Kind.values().length;

    public CargoVaultBlockEntity(BlockPos pos, BlockState state) {
        this(ModContent.CARGO_VAULT_ENTITY.get(), pos, state);
    }

    protected CargoVaultBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        int slots = state.getBlock() instanceof CargoVaultBlock vault ? vault.slots() : 1;
        items = new ItemStack[slots];
        java.util.Arrays.fill(items, ItemStack.EMPTY);
        counts = new int[slots];
        upgrades.addListener(container -> upgradesChanged(true));
    }

    // ---------------------------------------------------------------- slots

    public int slots() {
        return items.length;
    }

    /** Whole contents, for transactions that may roll back (Fabric). */
    public VaultContents saveState() {
        return contents();
    }

    public void restoreState(VaultContents state) {
        load(state);
        changed();
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

    // ---------------------------------------------------------------- upgrades

    public net.minecraft.world.SimpleContainer upgrades() {
        return upgrades;
    }

    public boolean has(VaultUpgrade upgrade) {
        return installed.contains(upgrade);
    }

    /** Capacity multiplier from the capacity upgrades: their multipliers add up, so five Electrum make ×20 (1 with none). */
    public int multiplier() {
        return multiplier;
    }

    private static int multiplierOf(net.minecraft.world.Container container, int skipSlot) {
        int sum = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            var stack = container.getItem(i);
            if (i != skipSlot && stack.getItem() instanceof VaultUpgradeItem item) sum += item.upgrade().multiplier() * stack.getCount();
        }
        return Math.max(1, sum);
    }

    private void upgradesChanged(boolean apply) {
        multiplier = multiplierOf(upgrades, -1);
        installed.clear();
        for (int i = 0; i < upgrades.getContainerSize(); i++) {
            if (upgrades.getItem(i).getItem() instanceof VaultUpgradeItem item) installed.add(item.upgrade());
        }
        if (!apply) return;
        if (level != null && !level.isClientSide()) {
            var state = getBlockState();
            boolean redstone = has(VaultUpgrade.REDSTONE);
            if (state.hasProperty(CargoVaultBlock.REDSTONE) && state.getValue(CargoVaultBlock.REDSTONE) != redstone) {
                level.setBlock(worldPosition, state.setValue(CargoVaultBlock.REDSTONE, redstone), Block.UPDATE_ALL);
            }
        }
        changed();
    }

    public int pullSides() {
        return pullSides;
    }

    public int pushSides() {
        return pushSides;
    }

    /** Turns Pull ({@code push} false) or Push on or off for one side. */
    public void toggleSide(boolean push, VaultSide side) {
        if (push) pushSides ^= side.bit();
        else pullSides ^= side.bit();
        setChanged();
    }

    /** Puts an upgrade straight into its slot (right-clicking the vault with it). Returns how many went in. */
    public int installUpgrade(ItemStack stack) {
        if (!(stack.getItem() instanceof VaultUpgradeItem item)) return 0;
        var kind = item.upgrade().kind();
        var current = upgrades.getItem(kind.slot());
        if (!current.isEmpty() && !ItemStack.isSameItemSameComponents(current, stack)) return 0;
        int moved = Math.min(stack.getCount(), kind.limit() - current.getCount());
        if (moved <= 0) return 0;
        upgrades.setItem(kind.slot(), stack.copyWithCount(current.getCount() + moved));
        return moved;
    }

    /** Older saves kept upgrades in any slot: move each to its kind's slot. */
    private void sortUpgrades() {
        for (int i = 0; i < UPGRADE_SLOTS; i++) {
            var stack = upgrades.getItem(i);
            if (!(stack.getItem() instanceof VaultUpgradeItem item)) continue;
            int target = item.upgrade().kind().slot();
            if (target != i && upgrades.getItem(target).isEmpty()) {
                upgrades.getItems().set(target, stack);
                upgrades.getItems().set(i, ItemStack.EMPTY);
            }
        }
    }

    /** Whether taking the upgrades out of {@code slot} still leaves room for everything inside. */
    public boolean canRemoveUpgrade(int slot) {
        if (!(upgrades.getItem(slot).getItem() instanceof VaultUpgradeItem item) || !item.upgrade().isCapacity()) return true;
        return fitsWith(multiplierOf(upgrades, slot));
    }

    /** Whether the contents fit with capacity upgrades adding up to {@code multiplier}. */
    protected boolean fitsWith(int multiplier) {
        for (int i = 0; i < slots(); i++) {
            int perStack = items[i].isEmpty() ? 64 : items[i].getMaxStackSize();
            if ((long) counts[i] > (long) stacksPerSlot() * perStack * multiplier) return false;
        }
        return true;
    }

    // ---------------------------------------------------------------- capacity

    /** The vault's size in stacks before upgrades (from the config; the server's value on clients). */
    public int vaultStacks() {
        if (level != null && level.isClientSide() && syncedVaultStacks > 0) return syncedVaultStacks;
        return Config.cargoVaultStacks();
    }

    /** Stacks one slot holds before upgrades: the vault's total split evenly between its slots (at least one stack). */
    public int stacksPerSlot() {
        return Math.max(1, vaultStacks() / slots());
    }

    /** Items of {@code type} (or of the slot's own item) one slot holds. */
    public int capacity(int slot, ItemStack type) {
        var item = items[slot].isEmpty() ? type : items[slot];
        int perStack = item.isEmpty() ? 64 : item.getMaxStackSize();
        return (int) Math.min(Integer.MAX_VALUE, (long) stacksPerSlot() * perStack * multiplier);
    }

    public int capacity(int slot) {
        return capacity(slot, ItemStack.EMPTY);
    }

    /** 0 to 1, for the slot's gauge. */
    public float fill(int slot) {
        int capacity = capacity(slot);
        return capacity <= 0 ? 0 : Math.min(1f, (float) count(slot) / capacity);
    }

    /** 0 to 1 over all slots, for comparators. */
    public float fill() {
        long count = 0;
        long capacity = 0;
        for (int i = 0; i < slots(); i++) {
            count += count(i);
            capacity += capacity(i);
        }
        return capacity <= 0 ? 0 : Math.min(1f, (float) count / capacity);
    }

    public boolean isEmpty() {
        for (int i = 0; i < slots(); i++) {
            if (count(i) > 0) return false;
        }
        return true;
    }

    /** The slot holding {@code stack}'s item, or -1. */
    public int slotOf(ItemStack stack) {
        for (int i = 0; i < slots(); i++) {
            if (!item(i).isEmpty() && ItemStack.isSameItemSameComponents(item(i), stack)) return i;
        }
        return -1;
    }

    /**
     * Whether {@code slot} takes {@code stack}: its own item, or (when it has no item type) an item no other slot
     * holds, if the vault is unlocked or a player is putting it in by hand ({@link #byHand}).
     */
    public boolean accepts(int slot, ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (!items[slot].isEmpty()) return ItemStack.isSameItemSameComponents(items[slot], stack);
        return takesNewType() && slotOf(stack) < 0;
    }

    /** True while a player puts something in by hand: a locked slot with no type then takes it as its type. */
    private boolean byHand;

    /** Runs a player's own insert: a locked, empty vault takes the first item (or fluid) as its locked type. */
    public <T> T byHand(java.util.function.Supplier<T> insert) {
        boolean before = byHand;
        byHand = true;
        try {
            return insert.get();
        } finally {
            byHand = before;
        }
    }

    /** Whether a slot with no type may take a new one: unlocked, or a player putting it in by hand. */
    protected boolean takesNewType() {
        return !locked || byHand;
    }

    /** Whether Clear has something to do: the vault is locked and some empty slot still has a type. */
    public boolean canClearTypes() {
        if (!locked) return false;
        for (int i = 0; i < slots(); i++) {
            if (counts[i] == 0 && !items[i].isEmpty()) return true;
        }
        return false;
    }

    /** Clear: empty slots forget their type and the vault stays locked, waiting for a new item by hand. */
    public void clearTypes() {
        if (!canClearTypes()) return;
        for (int i = 0; i < slots(); i++) {
            if (counts[i] == 0) items[i] = ItemStack.EMPTY;
        }
        changed();
    }

    /** Whether any slot takes {@code stack}. */
    public boolean accepts(ItemStack stack) {
        for (int i = 0; i < slots(); i++) {
            if (accepts(i, stack)) return true;
        }
        return false;
    }

    /**
     * Puts up to {@code stack.getCount()} items into {@code slot}. Returns how many were taken: with a Void upgrade,
     * all of them (what doesn't fit is deleted).
     */
    public int insert(int slot, ItemStack stack, boolean simulate) {
        if (!accepts(slot, stack)) return 0;
        int moved = Math.max(0, Math.min(stack.getCount(), capacity(slot, stack) - counts[slot]));
        if (moved > 0 && !simulate) setSlot(slot, items[slot].isEmpty() ? stack : items[slot], counts[slot] + moved);
        return has(VaultUpgrade.VOID) ? stack.getCount() : moved;
    }

    /** How many more of {@code stack} fit (ignoring the Void upgrade). */
    public int roomFor(ItemStack stack) {
        int slot = slotOf(stack);
        for (int i = 0; i < slots() && slot < 0; i++) {
            if (accepts(i, stack)) slot = i;
        }
        return slot < 0 ? 0 : Math.max(0, capacity(slot, stack) - count(slot));
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

    protected void setLockedQuietly(boolean locked) {
        this.locked = locked;
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

    /** Whether there's something to lock to: an item type, or a Fluid Vault's fluid. */
    public boolean hasContentType() {
        return hasAnyItemType();
    }

    public boolean hasAnyItemType() {
        for (int i = 0; i < slots(); i++) {
            if (!item(i).isEmpty()) return true;
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
            if (count(i) > 0) return extract(i, amount, simulate);
        }
        return ItemStack.EMPTY;
    }

    // ---------------------------------------------------------------- contents as a whole

    public VaultContents contents() {
        var slots = new ArrayList<VaultContents.Slot>(slots());
        for (int i = 0; i < slots(); i++) slots.add(new VaultContents.Slot(items[i], counts[i]));
        return new VaultContents(slots, locked);
    }

    protected void load(VaultContents contents) {
        for (int i = 0; i < slots(); i++) {
            var slot = contents.slot(i);
            items[i] = slot.item();
            counts[i] = slot.count();
        }
        locked = contents.locked();
    }

    /** Saves and tells nearby clients (counts, gauges, comparators, the Redstone upgrade's signal). */
    public void changed() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            int signal = signal();
            if (signal != lastSignal) {
                lastSignal = signal;
                level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
            }
        }
    }

    /** Comparator strength: 0 when empty, 1 to 15 as it fills. Also the Redstone upgrade's signal. */
    public int signal() {
        return isEmpty() ? 0 : 1 + (int) Math.floor(fill() * 14);
    }

    // ---------------------------------------------------------------- Pull and Push

    /** Whether a left-click on the front takes something out by hand (items; not a Fluid Vault's fluid). */
    public boolean takesByHand() {
        return !isEmpty();
    }

    /** Server tick: Pull and Push upgrades move a stack with the inventories on their picked sides, once a second. */
    public static void tick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, CargoVaultBlockEntity vault) {
        if (!(level instanceof ServerLevel server) || (level.getGameTime() + pos.asLong()) % 20 != 0) return;
        boolean pull = vault.has(VaultUpgrade.PULL);
        boolean push = vault.has(VaultUpgrade.PUSH);
        if (!pull && !push) return;
        if (vault.has(VaultUpgrade.REDSTONE) && level.hasNeighborSignal(pos)) return;
        vault.moveWithNeighbours(server, pos, state, pull, push);
    }

    /** Pull and Push for items; the Fluid Vault moves fluid instead. */
    protected void moveWithNeighbours(ServerLevel server, BlockPos pos, BlockState state, boolean pull, boolean push) {
        var vault = this;

        var facing = state.getValue(CargoVaultBlock.FACING);
        var platform = io.github.keellam4.oritechextensions.platform.Platform.INSTANCE;
        if (push) {
            push:
            for (var side : VaultSide.values()) {
                if ((vault.pushSides & side.bit()) == 0) continue;
                var direction = side.toDirection(facing);
                var target = pos.relative(direction);
                for (int i = 0; i < vault.slots(); i++) {
                    var offer = vault.extract(i, 64, true);
                    if (offer.isEmpty()) continue;
                    int moved = platform.insertItem(server, target, direction.getOpposite(), offer, false);
                    if (moved > 0) {
                        vault.extract(i, moved, false);
                        break push;
                    }
                }
            }
        }
        if (pull && vault.hasAnyItemType()) {
            for (var side : VaultSide.values()) {
                if ((vault.pullSides & side.bit()) == 0) continue;
                var direction = side.toDirection(facing);
                var target = pos.relative(direction);
                var taken = platform.extractItem(server, target, direction.getOpposite(),
                        stack -> vault.slotOf(stack) >= 0 && vault.roomFor(stack) > 0, 64, true);
                if (taken.isEmpty()) continue;
                int fits = Math.min(taken.getCount(), vault.roomFor(taken));
                if (fits <= 0) continue;
                var real = platform.extractItem(server, target, direction.getOpposite(), stack -> ItemStack.isSameItemSameComponents(stack, taken), fits, false);
                if (!real.isEmpty()) {
                    vault.insert(real, false);
                    break;
                }
            }
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
        tag.put("upgrades", upgrades.createTag(registries));
        tag.putInt("pull_sides", pullSides);
        tag.putInt("push_sides", pushSides);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        load(VaultContents.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag.get("vault"))
                .result().orElse(VaultContents.EMPTY));
        syncedVaultStacks = tag.contains("vault_stacks", Tag.TAG_INT) ? tag.getInt("vault_stacks") : -1;
        if (tag.contains("pull_sides", Tag.TAG_INT)) pullSides = tag.getInt("pull_sides");
        if (tag.contains("push_sides", Tag.TAG_INT)) pushSides = tag.getInt("push_sides");
        if (tag.contains("upgrades", Tag.TAG_LIST)) {
            upgrades.fromTag(tag.getList("upgrades", Tag.TAG_COMPOUND), registries);
            sortUpgrades();
            upgradesChanged(false);
        }
    }

    private void writeContents(CompoundTag tag, HolderLookup.Provider registries) {
        VaultContents.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), contents())
                .result().ifPresent(encoded -> tag.put("vault", encoded));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        writeContents(tag, registries);
        tag.putInt("vault_stacks", vaultStacks());
        tag.put("upgrades", upgrades.createTag(registries));
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
        var stored = input.get(net.minecraft.core.component.DataComponents.CONTAINER);
        if (stored != null) {
            var list = net.minecraft.core.NonNullList.withSize(UPGRADE_SLOTS, ItemStack.EMPTY);
            stored.copyInto(list);
            for (int i = 0; i < UPGRADE_SLOTS; i++) upgrades.setItem(i, list.get(i));
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (hasAnyItemType() || locked) components.set(ModContent.VAULT_CONTENTS.get(), contents());
        var paint = currentPaint();
        if (PaintColors.isPainted(paint)) components.set(ModContent.PAINT_COMPONENT.get(), paint);
        if (!upgrades.isEmpty()) {
            var list = new ArrayList<ItemStack>(UPGRADE_SLOTS);
            for (int i = 0; i < UPGRADE_SLOTS; i++) list.add(upgrades.getItem(i).copy());
            components.set(net.minecraft.core.component.DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.fromItems(list));
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove("vault");
        tag.remove("upgrades");
    }
}
