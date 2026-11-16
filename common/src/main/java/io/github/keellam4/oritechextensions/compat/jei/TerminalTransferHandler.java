package io.github.keellam4.oritechextensions.compat.jei;

import io.github.keellam4.oritechextensions.ModContent;
import io.github.keellam4.oritechextensions.client.TerminalScreen;
import io.github.keellam4.oritechextensions.platform.Platform;
import io.github.keellam4.oritechextensions.storage.TerminalFillPayload;
import io.github.keellam4.oritechextensions.storage.TerminalMenu;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/** JEI's "+" in the Crafting Terminal: asks the server to fill the grid (see {@link TerminalMenu#fill}). */
final class TerminalTransferHandler implements IRecipeTransferHandler<TerminalMenu, RecipeHolder<CraftingRecipe>> {

    private final IRecipeTransferHandlerHelper helper;

    TerminalTransferHandler(IRecipeTransferHandlerHelper helper) {
        this.helper = helper;
    }

    @Override
    public Class<? extends TerminalMenu> getContainerClass() {
        return TerminalMenu.class;
    }

    @Override
    public Optional<MenuType<TerminalMenu>> getMenuType() {
        return Optional.of(ModContent.CRAFTING_TERMINAL_MENU.get());
    }

    @Override
    public RecipeType<RecipeHolder<CraftingRecipe>> getRecipeType() {
        return RecipeTypes.CRAFTING;
    }

    @Override
    @Nullable
    public IRecipeTransferError transferRecipe(TerminalMenu menu, RecipeHolder<CraftingRecipe> recipe, IRecipeSlotsView slots,
                                               Player player, boolean maxTransfer, boolean doTransfer) {
        if (!menu.isCrafting() || TerminalMenu.layout(recipe.value()) == null) return helper.createInternalError();
        List<IRecipeSlotView> missing = slots.getSlotViews(RecipeIngredientRole.INPUT).stream()
                .filter(view -> !view.isEmpty() && view.getItemStacks().noneMatch(stack -> available(menu, player, stack)))
                .toList();
        if (!missing.isEmpty()) {
            return helper.createUserErrorForMissingSlots(Component.translatable("jei.oritech_extensions.terminal.missing"), missing);
        }
        if (doTransfer) Platform.INSTANCE.sendToServer(new TerminalFillPayload(menu.containerId, recipe.id()));
        return null;
    }

    private static boolean available(TerminalMenu menu, Player player, ItemStack stack) {
        if (TerminalScreen.networkHas(menu.containerId, stack)) return true;
        for (var held : player.getInventory().items) if (ItemStack.isSameItem(held, stack)) return true;
        return false;
    }
}
