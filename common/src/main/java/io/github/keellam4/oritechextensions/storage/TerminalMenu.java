package io.github.keellam4.oritechextensions.storage;

import io.github.keellam4.oritechextensions.ModContent;
import io.github.keellam4.oritechextensions.platform.Platform;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Cargo Controller's terminal: the player's inventory, plus a list of everything in the network that the
 * server sends while the screen is open ({@link TerminalSyncPayload}). Grid clicks come back as
 * {@link TerminalActionPayload}; shift-clicking an inventory slot puts it into the network.
 * <p>
 * The Crafting Terminal adds a 3 × 3 crafting grid: taking the result refills the grid from the network, so
 * shift-clicking the result crafts as many as the network has ingredients for. Whatever is left in the grid goes
 * back into the network when the screen closes.
 */
public class TerminalMenu extends AbstractContainerMenu {

    public static final int TAKE_STACK = 0;
    public static final int TAKE_ONE = 1;
    public static final int TAKE_TO_INVENTORY = 2;
    public static final int INSERT_CARRIED = 3;
    public static final int INSERT_ONE = 4;
    /** Fluid entry clicked: fill the empty bucket on the cursor, or empty a full one into the network. */
    public static final int BUCKET = 5;

    /** Crafting Terminal: put the crafting grid back into the network. */
    public static final int CLEAR_GRID = 6;

    public static final int INVENTORY_X = 8;
    /** Item grid rows: the plain terminal has room for more. */
    public static final int ROWS = 8;
    public static final int CRAFTING_ROWS = 5;
    public static final int CRAFT_X = 30;
    public static final int CRAFT_Y = 120;
    public static final int RESULT_X = 124;
    public static final int RESULT_Y = 138;

    /** Opened from a distance with a Cargo Link: who may keep using it, and the energy each action costs. */
    public interface RemoteAccess {
        boolean stillValid(Player player);

        /** Pays for moving up to {@code items} items (a bucket counts as a stack). False if the battery is too low. */
        boolean pay(Player player, int items);
    }

    @Nullable
    private final CargoControllerBlockEntity controller;
    @Nullable
    private RemoteAccess remote;
    private final Player player;
    private final boolean crafting;
    private final net.minecraft.world.inventory.TransientCraftingContainer craftSlots;
    private final net.minecraft.world.inventory.ResultContainer resultSlots = new net.minecraft.world.inventory.ResultContainer();
    private final int inventoryStart;
    private List<CargoNetwork.Entry> lastSent;
    private int ticks;

    /** Client side of the plain terminal. */
    public TerminalMenu(int id, Inventory inventory) {
        this(id, inventory, null, false);
    }

    /** Client side of the Crafting Terminal. */
    public static TerminalMenu crafting(int id, Inventory inventory) {
        return new TerminalMenu(id, inventory, null, true);
    }

    public TerminalMenu(int id, Inventory inventory, @Nullable CargoControllerBlockEntity controller, boolean crafting) {
        super(crafting ? ModContent.CRAFTING_TERMINAL_MENU.get() : ModContent.TERMINAL_MENU.get(), id);
        this.controller = controller;
        this.player = inventory.player;
        this.crafting = crafting;
        this.craftSlots = new net.minecraft.world.inventory.TransientCraftingContainer(this, 3, 3);
        if (crafting) {
            addSlot(new TerminalResultSlot(inventory.player, RESULT_X, RESULT_Y));
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) addSlot(new Slot(craftSlots, col + row * 3, CRAFT_X + col * 18, CRAFT_Y + row * 18));
            }
        }
        inventoryStart = slots.size();
        int y = inventoryY(crafting);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col + row * 9 + 9, INVENTORY_X + col * 18, y + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, INVENTORY_X + col * 18, y + 58));
    }

    /** Makes this a remote terminal (Cargo Link). */
    public TerminalMenu remote(RemoteAccess access) {
        this.remote = access;
        return this;
    }

    public static int inventoryY(boolean crafting) {
        return crafting ? 195 : 183;
    }

    public boolean isCrafting() {
        return crafting;
    }

    // ---------------------------------------------------------------- crafting

    @Override
    public void slotsChanged(net.minecraft.world.Container container) {
        super.slotsChanged(container);
        if (container != craftSlots || !(player.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
        var input = craftSlots.asCraftInput();
        var recipe = level.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, level);
        var result = recipe.map(holder -> holder.value().assemble(input, level.registryAccess())).orElse(ItemStack.EMPTY);
        resultSlots.setRecipeUsed(recipe.orElse(null));
        resultSlots.setItem(0, result);
    }

    /** The crafting result: taking it uses one of each ingredient, then refills the grid from the network. */
    private final class TerminalResultSlot extends net.minecraft.world.inventory.ResultSlot {

        TerminalResultSlot(Player player, int x, int y) {
            super(player, craftSlots, resultSlots, 0, x, y);
        }

        @Override
        public void onTake(Player taker, ItemStack stack) {
            var before = new ItemStack[craftSlots.getContainerSize()];
            for (int i = 0; i < before.length; i++) before[i] = craftSlots.getItem(i).copyWithCount(1);
            super.onTake(taker, stack);
            if (controller == null) return;
            var network = controller.network();
            for (int i = 0; i < before.length; i++) {
                if (before[i].isEmpty() || !craftSlots.getItem(i).isEmpty()) continue;
                var refill = network.extract(before[i], 1, false);
                if (!refill.isEmpty()) craftSlots.setItem(i, refill);
            }
            lastSent = null;
        }
    }

    /**
     * JEI's "+": empties the grid, then puts one of each ingredient of the recipe in it, taken from the network
     * first and then the player's inventory. Taking the result refills the grid from the network as usual.
     */
    public void fill(net.minecraft.resources.ResourceLocation recipeId) {
        if (!crafting || controller == null || !(player.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
        var holder = level.getRecipeManager().byKey(recipeId).orElse(null);
        if (holder == null || !(holder.value() instanceof net.minecraft.world.item.crafting.CraftingRecipe recipe)) return;
        var layout = layout(recipe);
        if (layout == null) return;
        if (remote != null && !remote.pay(player, (int) layout.stream().filter(i -> !i.isEmpty()).count())) return;
        clearGrid();
        var network = controller.network();
        for (int i = 0; i < layout.size(); i++) {
            if (layout.get(i).isEmpty()) continue;
            var stack = take(network, layout.get(i));
            if (!stack.isEmpty()) craftSlots.setItem(i, stack);
        }
        slotsChanged(craftSlots);
        lastSent = null;
        broadcastChanges();
    }

    /** A crafting recipe's ingredients as they sit in a 3 × 3 grid, or null if it doesn't fit. */
    @Nullable
    public static List<net.minecraft.world.item.crafting.Ingredient> layout(net.minecraft.world.item.crafting.CraftingRecipe recipe) {
        var ingredients = recipe.getIngredients();
        var grid = net.minecraft.core.NonNullList.withSize(9, net.minecraft.world.item.crafting.Ingredient.EMPTY);
        if (recipe instanceof net.minecraft.world.item.crafting.ShapedRecipe shaped) {
            int width = shaped.getWidth(), height = shaped.getHeight();
            if (width > 3 || height > 3 || ingredients.size() < width * height) return null;
            for (int row = 0; row < height; row++)
                for (int col = 0; col < width; col++) grid.set(row * 3 + col, ingredients.get(row * width + col));
        } else {
            if (ingredients.size() > 9) return null;
            for (int i = 0; i < ingredients.size(); i++) grid.set(i, ingredients.get(i));
        }
        return grid;
    }

    /** One item matching the ingredient: the network's most plentiful match, otherwise one from the player's inventory. */
    private ItemStack take(CargoNetwork network, net.minecraft.world.item.crafting.Ingredient ingredient) {
        CargoNetwork.Entry best = null;
        for (var entry : network.contents()) {
            if (entry.isFluid() || entry.count() <= 0 || !ingredient.test(entry.item())) continue;
            if (best == null || entry.count() > best.count()) best = entry;
        }
        if (best != null) {
            var taken = network.extract(best.item(), 1, false);
            if (!taken.isEmpty()) return taken;
        }
        var items = player.getInventory().items;
        for (var stack : items) if (!stack.isEmpty() && ingredient.test(stack)) return stack.split(1);
        return ItemStack.EMPTY;
    }

    private void clearGrid() {
        for (int i = 0; i < craftSlots.getContainerSize(); i++) {
            var stack = craftSlots.removeItemNoUpdate(i);
            if (stack.isEmpty()) continue;
            if (controller != null) stack.shrink(controller.network().insert(stack, false));
            if (!stack.isEmpty() && !player.getInventory().add(stack)) player.drop(stack, false);
        }
        slotsChanged(craftSlots);
        lastSent = null;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (crafting && !player.level().isClientSide()) clearGrid();
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (controller == null || !(player instanceof ServerPlayer serverPlayer)) return;
        if (lastSent != null && ticks++ % 10 != 0) return;
        var network = controller.network();
        var entries = network.contents();
        if (entries.equals(lastSent)) return;
        lastSent = entries;
        Platform.INSTANCE.sendToPlayer(serverPlayer, new TerminalSyncPayload(containerId, network.size(), entries));
    }

    /** A grid click from the client. */
    public void handle(int action, ItemStack item, net.minecraft.world.level.material.Fluid fluid) {
        if (controller == null) return;
        if (remote != null && action != CLEAR_GRID) {
            int items = action == TAKE_ONE || action == INSERT_ONE ? 1 : 64;
            if (!remote.pay(player, items)) return;
        }
        var network = controller.network();
        var carried = getCarried();
        switch (action) {
            case BUCKET -> bucket(network, fluid);
            case CLEAR_GRID -> clearGrid();
            case TAKE_STACK, TAKE_ONE, TAKE_TO_INVENTORY -> {
                if (item.isEmpty()) return;
                if (action == TAKE_TO_INVENTORY) {
                    var taken = network.extract(item, item.getMaxStackSize(), false);
                    if (taken.isEmpty()) return;
                    player.getInventory().add(taken);
                    if (!taken.isEmpty()) network.insert(taken, false); // whatever didn't fit goes back
                    return;
                }
                if (!carried.isEmpty() && !ItemStack.isSameItemSameComponents(carried, item)) return;
                int room = carried.isEmpty() ? item.getMaxStackSize() : carried.getMaxStackSize() - carried.getCount();
                var taken = network.extract(item, action == TAKE_ONE ? Math.min(1, room) : room, false);
                if (taken.isEmpty()) return;
                if (carried.isEmpty()) setCarried(taken);
                else carried.grow(taken.getCount());
            }
            case INSERT_CARRIED, INSERT_ONE -> {
                if (carried.isEmpty()) return;
                int moved = network.insert(carried.copyWithCount(action == INSERT_ONE ? 1 : carried.getCount()), false);
                carried.shrink(moved);
            }
            default -> {
            }
        }
        lastSent = null; // send the new totals right away
        broadcastChanges();
    }

    /** Older callers: an item click. */
    public void handle(int action, ItemStack item) {
        handle(action, item, net.minecraft.world.level.material.Fluids.EMPTY);
    }

    /** Empty bucket on the cursor: fill it from the network. A full bucket: empty it into the network. */
    private void bucket(CargoNetwork network, net.minecraft.world.level.material.Fluid clicked) {
        var carried = getCarried();
        if (carried.is(net.minecraft.world.item.Items.BUCKET)) {
            if (clicked == net.minecraft.world.level.material.Fluids.EMPTY) return;
            var filled = new ItemStack(clicked.getBucket());
            if (filled.isEmpty() || network.drain(clicked, 1000, true).amount() < 1000) return;
            network.drain(clicked, 1000, false);
            carried.shrink(1);
            if (carried.isEmpty()) setCarried(filled);
            else if (!player.getInventory().add(filled)) player.drop(filled, false);
            return;
        }
        var held = fluidInBucket(carried);
        if (held == null || network.fill(held, 1000, true) < 1000) return;
        network.fill(held, 1000, false);
        carried.shrink(1);
        var empty = new ItemStack(net.minecraft.world.item.Items.BUCKET);
        if (carried.isEmpty()) setCarried(empty);
        else if (!player.getInventory().add(empty)) player.drop(empty, false);
    }

    /** The fluid a filled bucket holds, or null. */
    @Nullable
    public static net.minecraft.world.level.material.Fluid fluidInBucket(ItemStack stack) {
        if (stack.isEmpty() || stack.is(net.minecraft.world.item.Items.BUCKET)) return null;
        for (var fluid : net.minecraft.core.registries.BuiltInRegistries.FLUID) {
            if (fluid != net.minecraft.world.level.material.Fluids.EMPTY && fluid.isSource(fluid.defaultFluidState()) && stack.is(fluid.getBucket())) return fluid;
        }
        return null;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        var slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        var stack = slot.getItem();
        if (crafting && index == 0) {
            // The result: into the inventory; vanilla repeats this while the result stays the same.
            var copy = stack.copy();
            stack.getItem().onCraftedBy(stack, player.level(), player);
            if (!moveItemStackTo(stack, inventoryStart, slots.size(), true)) return ItemStack.EMPTY;
            slot.onQuickCraft(stack, copy);
            if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
            else slot.setChanged();
            if (stack.getCount() == copy.getCount()) return ItemStack.EMPTY;
            slot.onTake(player, stack);
            return copy;
        }
        if (index < inventoryStart) {
            // A crafting grid slot: back into the network, or the inventory.
            if (controller != null) stack.shrink(controller.network().insert(stack, false));
            if (!stack.isEmpty() && !moveItemStackTo(stack, inventoryStart, slots.size(), false)) return ItemStack.EMPTY;
            slot.setChanged();
            lastSent = null;
            return ItemStack.EMPTY;
        }
        if (controller == null) return ItemStack.EMPTY;
        if (remote != null && !remote.pay(player, stack.getCount())) return ItemStack.EMPTY;
        int moved = controller.network().insert(stack, false);
        if (moved <= 0) return ItemStack.EMPTY;
        stack.shrink(moved);
        slot.setChanged();
        lastSent = null;
        return ItemStack.EMPTY; // don't repeat: one stack per shift-click
    }

    @Override
    public boolean stillValid(Player player) {
        if (controller == null) return true;
        if (remote != null) return !controller.isRemoved() && remote.stillValid(player);
        var level = controller.getLevel();
        return level != null && level.getBlockEntity(controller.getBlockPos()) == controller
                && player.distanceToSqr(controller.getBlockPos().getCenter()) <= 64;
    }
}
