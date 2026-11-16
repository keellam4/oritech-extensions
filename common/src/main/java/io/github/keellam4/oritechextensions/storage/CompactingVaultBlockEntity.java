package io.github.keellam4.oritechextensions.storage;

import io.github.keellam4.oritechextensions.Config;
import io.github.keellam4.oritechextensions.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The Compacting Vault: one item in up to three forms, found from crafting recipes the first time an item goes in
 * (nugget ↔ ingot ↔ block, or 2 × 2 ones like clay ball ↔ clay). The stock is kept once, in units of the smallest
 * form, and every window shows how much of its form that is. Put in or take out any form; the vault converts.
 * <p>
 * Slot 0 (the top window) is the biggest form, then smaller ones. The Compacting Vault has three slots, the Dual
 * Compacting Vault two. A shorter chain leaves the last slots empty; an item with no compacting recipes works like a
 * Cargo Vault in slot 0.
 */
public class CompactingVaultBlockEntity extends CargoVaultBlockEntity {

    private final int slotCount;

    /** Count-1 templates, biggest form first; empty where the chain is shorter. */
    private final ItemStack[] forms;
    /** How many of the smallest form one item of each slot's form is worth. */
    private final int[] ratios;
    /** Stock in units of the smallest form. */
    private int units;

    public CompactingVaultBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.COMPACTING_VAULT_ENTITY.get(), pos, state);
        slotCount = state.getBlock() instanceof CargoVaultBlock vault ? vault.slots() : 3;
        forms = new ItemStack[slotCount];
        java.util.Arrays.fill(forms, ItemStack.EMPTY);
        ratios = new int[slotCount];
    }

    private boolean hasForms() {
        return !forms[0].isEmpty();
    }

    /** Units the vault holds: as many as a Cargo Vault holds of the second form (the middle one, or the smaller of two). */
    private long capacityUnits() {
        int reference = !forms[1].isEmpty() ? 1 : 0;
        return capacityUnits(multiplier());
    }

    private long capacityUnits(int multiplier) {
        int reference = !forms[1].isEmpty() ? 1 : 0;
        int perStack = forms[reference].isEmpty() ? 64 : forms[reference].getMaxStackSize();
        return (long) vaultStacks() * perStack * Math.max(1, ratios[reference]) * multiplier;
    }

    @Override
    protected boolean fitsWith(int multiplier) {
        return units <= capacityUnits(multiplier);
    }

    @Override
    public int slots() {
        return slotCount;
    }

    @Override
    public ItemStack item(int slot) {
        return forms[slot];
    }

    @Override
    public int count(int slot) {
        return forms[slot].isEmpty() || ratios[slot] <= 0 ? 0 : units / ratios[slot];
    }

    @Override
    public int capacity(int slot, ItemStack type) {
        if (forms[slot].isEmpty()) return hasForms() ? 0 : vaultStacks() * (type.isEmpty() ? 64 : type.getMaxStackSize()) * multiplier();
        return (int) Math.min(Integer.MAX_VALUE, capacityUnits() / ratios[slot]);
    }

    @Override
    public boolean accepts(int slot, ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (!hasForms()) return takesNewType();
        return slotOf(stack) >= 0;
    }

    @Override
    public int insert(int slot, ItemStack stack, boolean simulate) {
        return insert(stack, simulate); // any window: the vault puts it in the right form
    }

    @Override
    public int insert(ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return 0;
        if (!hasForms()) {
            if (!takesNewType() || !(level instanceof ServerLevel server)) return 0;
            var chain = Chain.of(server, stack, slotCount);
            int index = chain.forms().indexOf(chain.forms().stream().filter(f -> ItemStack.isSameItemSameComponents(f, stack)).findFirst().orElseThrow());
            long capacity = chainCapacity(chain);
            int ratio = chain.ratios().get(index);
            int moved = (int) Math.min(stack.getCount(), capacity / ratio);
            if (moved > 0 && !simulate) {
                for (int i = 0; i < slotCount; i++) {
                    forms[i] = i < chain.forms().size() ? chain.forms().get(i).copyWithCount(1) : ItemStack.EMPTY;
                    ratios[i] = i < chain.ratios().size() ? chain.ratios().get(i) : 0;
                }
                units = moved * ratio;
                changed();
            }
            return has(VaultUpgrade.VOID) ? stack.getCount() : moved;
        }
        int slot = slotOf(stack);
        if (slot < 0) return 0;
        long room = (capacityUnits() - units) / ratios[slot];
        int moved = (int) Math.min(stack.getCount(), Math.max(0, room));
        if (moved > 0 && !simulate) {
            units += moved * ratios[slot];
            changed();
        }
        return has(VaultUpgrade.VOID) ? stack.getCount() : moved;
    }

    private long chainCapacity(Chain chain) {
        int reference = chain.forms().size() > 1 ? 1 : 0;
        return (long) vaultStacks() * chain.forms().get(reference).getMaxStackSize() * chain.ratios().get(reference) * multiplier();
    }

    @Override
    public int roomFor(ItemStack stack) {
        int slot = slotOf(stack);
        if (slot < 0) return 0;
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, (capacityUnits() - units) / ratios[slot]));
    }

    @Override
    public ItemStack extract(int slot, int amount, boolean simulate) {
        if (forms[slot].isEmpty() || amount <= 0) return ItemStack.EMPTY;
        int moved = Math.min(Math.min(amount, count(slot)), forms[slot].getMaxStackSize());
        if (moved <= 0) return ItemStack.EMPTY;
        var out = forms[slot].copyWithCount(moved);
        if (!simulate) {
            units -= moved * ratios[slot];
            if (units <= 0 && !isLocked()) clear();
            changed();
        }
        return out;
    }

    private void clear() {
        units = 0;
        for (int i = 0; i < slotCount; i++) {
            forms[i] = ItemStack.EMPTY;
            ratios[i] = 0;
        }
    }

    @Override
    public void setSlot(int slot, ItemStack type, int newCount) {
        // Tests and creative tools: set the stock as this many of the slot's form.
        if (!forms[slot].isEmpty()) {
            units = Math.max(0, newCount) * ratios[slot];
            if (units == 0 && !isLocked()) clear();
            changed();
        } else if (!type.isEmpty() && newCount > 0) {
            insert(type.copyWithCount(newCount), false);
        }
    }

    @Override
    public void setSlotQuietly(int slot, ItemStack type, int newCount) {
        if (!forms[slot].isEmpty()) units = Math.max(0, newCount) * ratios[slot];
    }

    @Override
    public void setLocked(boolean locked) {
        setLockedQuietly(locked);
        if (!locked && units == 0) clear();
        changed();
    }

    @Override
    public boolean canClearTypes() {
        return isLocked() && units == 0 && hasForms();
    }

    @Override
    public void clearTypes() {
        if (!canClearTypes()) return;
        clear();
        changed();
    }

    @Override
    public VaultContents contents() {
        var slots = new ArrayList<VaultContents.Slot>(slotCount);
        var ratioList = new ArrayList<Integer>(slotCount);
        for (int i = 0; i < slotCount; i++) {
            slots.add(new VaultContents.Slot(forms[i], count(i)));
            ratioList.add(ratios[i]);
        }
        return new VaultContents(slots, isLocked(), ratioList);
    }

    @Override
    protected void load(VaultContents contents) {
        setLockedQuietly(contents.locked());
        units = 0;
        for (int i = 0; i < slotCount; i++) {
            var slot = contents.slot(i);
            forms[i] = slot.item();
            ratios[i] = i < contents.ratios().size() ? contents.ratios().get(i) : (slot.isEmpty() ? 0 : 1);
            // The smallest form is worth one unit, so its count is the exact stock.
            if (ratios[i] == 1) units = slot.count();
        }
        if (!hasForms()) units = 0;
    }

    /** The forms of an item, biggest first, and what each is worth in the smallest form. */
    record Chain(List<ItemStack> forms, List<Integer> ratios) {

        /**
         * Up to {@code maxForms} forms around {@code start}. With three, one step down from the starting item and
         * then up (ingot: nugget, ingot, block). With two, up first (ingot, block), down only if there's no up.
         */
        static Chain of(ServerLevel level, ItemStack start, int maxForms) {
            var smallestFirst = new ArrayList<ItemStack>();
            var factors = new ArrayList<Integer>(); // factors.get(i): how many of form i make form i + 1
            smallestFirst.add(start.copyWithCount(1));

            boolean downFirst = maxForms >= 3;
            for (int pass = 0; pass < 2; pass++) {
                boolean goDown = (pass == 0) == downFirst;
                while (smallestFirst.size() < maxForms) {
                    if (goDown) {
                        var down = down(level, smallestFirst.get(0));
                        if (down.isEmpty() || contains(smallestFirst, down.get().item())) break;
                        smallestFirst.add(0, down.get().item());
                        factors.add(0, down.get().factor());
                        if (downFirst) break; // one step down, then the rest up
                    } else {
                        var up = up(level, smallestFirst.get(smallestFirst.size() - 1));
                        if (up.isEmpty() || contains(smallestFirst, up.get().item())) break;
                        smallestFirst.add(up.get().item());
                        factors.add(up.get().factor());
                    }
                }
            }
            // Three forms but only one step up was possible: try further down too (block, ingot, nugget from a block).
            while (smallestFirst.size() < maxForms) {
                var down = down(level, smallestFirst.get(0));
                if (down.isEmpty() || contains(smallestFirst, down.get().item())) break;
                smallestFirst.add(0, down.get().item());
                factors.add(0, down.get().factor());
            }

            var ratiosSmallestFirst = new ArrayList<Integer>();
            int ratio = 1;
            for (int i = 0; i < smallestFirst.size(); i++) {
                ratiosSmallestFirst.add(ratio);
                if (i < factors.size()) ratio *= factors.get(i);
            }
            var forms = new ArrayList<>(smallestFirst);
            java.util.Collections.reverse(forms);
            java.util.Collections.reverse(ratiosSmallestFirst);
            return new Chain(forms, ratiosSmallestFirst);
        }

        private static boolean contains(List<ItemStack> list, ItemStack item) {
            return list.stream().anyMatch(other -> ItemStack.isSameItemSameComponents(other, item));
        }

        private record Step(ItemStack item, int factor) {
        }

        /** 9 (3 × 3) or 4 (2 × 2) of {@code item} craft one bigger item that crafts back into exactly as many. */
        private static Optional<Step> up(ServerLevel level, ItemStack item) {
            for (int size : new int[]{3, 2}) {
                var bigger = craft(level, size, item);
                if (bigger.isEmpty() || bigger.get().getCount() != 1) continue;
                var back = craft(level, 1, bigger.get());
                if (back.isPresent() && back.get().getCount() == size * size && ItemStack.isSameItemSameComponents(back.get(), item)) {
                    return Optional.of(new Step(bigger.get().copyWithCount(1), size * size));
                }
            }
            return Optional.empty();
        }

        /** One {@code item} crafts 9 or 4 smaller items that craft back into exactly one. */
        private static Optional<Step> down(ServerLevel level, ItemStack item) {
            var smaller = craft(level, 1, item);
            if (smaller.isEmpty()) return Optional.empty();
            int count = smaller.get().getCount();
            if (count != 9 && count != 4) return Optional.empty();
            var back = craft(level, count == 9 ? 3 : 2, smaller.get());
            if (back.isPresent() && back.get().getCount() == 1 && ItemStack.isSameItemSameComponents(back.get(), item)) {
                return Optional.of(new Step(smaller.get().copyWithCount(1), count));
            }
            return Optional.empty();
        }

        private static Optional<ItemStack> craft(ServerLevel level, int size, ItemStack item) {
            var items = NonNullList.withSize(size * size, item.copyWithCount(1));
            var input = CraftingInput.of(size, size, items);
            return level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
                    .map(recipe -> recipe.value().assemble(input, level.registryAccess()))
                    .filter(result -> !result.isEmpty());
        }
    }
}
