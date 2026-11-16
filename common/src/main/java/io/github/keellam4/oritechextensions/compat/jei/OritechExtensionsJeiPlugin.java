package io.github.keellam4.oritechextensions.compat.jei;

import io.github.keellam4.oritechextensions.ModContent;
import io.github.keellam4.oritechextensions.OritechExtensions;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * JEI: the Crafting Terminal shows as a crafting station, and JEI's "+" fills its grid from the network and the
 * player's inventory. Loaded only when JEI is installed (Fabric: the jei_mod_plugin entrypoint; NeoForge: @JeiPlugin).
 */
@JeiPlugin
public class OritechExtensionsJeiPlugin implements IModPlugin {

    @Override
    public ResourceLocation getPluginUid() {
        return OritechExtensions.id("jei");
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(ModContent.CRAFTING_TERMINAL.get()), RecipeTypes.CRAFTING);
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(new TerminalTransferHandler(registration.getTransferHelper()), RecipeTypes.CRAFTING);
    }
}
