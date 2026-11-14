package io.github.keellam4.oritechextensions.test;

import io.github.keellam4.oritechextensions.ModContent;
import io.github.keellam4.oritechextensions.OritechExtensions;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rearth.oritech.init.ItemContent;

import java.util.List;

/**
 * Checks the guide book can be crafted from a book and any nickel ingot (the {@code c:ingots/nickel} tag),
 * so it still works in packs like ATM10 that swap Oritech's nickel for AllTheOres'.
 */
@GameTestHolder(OritechExtensions.MODID)
@PrefixGameTestTemplate(false)
public class GuideGameTests {

    private static final TagKey<Item> NICKEL_INGOTS =
            TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "ingots/nickel"));

    @GameTest(template = "empty_tall")
    public static void guideRecipeAcceptsAnyNickelIngot(GameTestHelper helper) {
        var level = helper.getLevel();
        var nickel = BuiltInRegistries.ITEM.getTag(NICKEL_INGOTS).orElse(null);
        helper.assertTrue(nickel != null && nickel.size() > 0, "c:ingots/nickel is empty");
        for (var holder : nickel) {
            var input = CraftingInput.of(2, 1, List.of(new ItemStack(Items.BOOK), new ItemStack(holder.value())));
            var match = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
            helper.assertTrue(match.isPresent()
                            && match.get().value().assemble(input, level.registryAccess()).is(ModContent.GUIDE.get()),
                    "Book + " + BuiltInRegistries.ITEM.getKey(holder.value()) + " doesn't make the guide");
        }
        helper.succeed();
    }

    @GameTest(template = "empty_tall")
    public static void guideRecipe(GameTestHelper helper) {
        var level = helper.getLevel();
        var input = CraftingInput.of(2, 1, List.of(new ItemStack(Items.BOOK), new ItemStack(ItemContent.NICKEL_INGOT)));
        var match = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
        helper.assertTrue(match.isPresent(), "No crafting recipe for book + nickel ingot");
        ItemStack result = match.get().value().assemble(input, level.registryAccess());
        helper.assertTrue(result.is(ModContent.GUIDE.get()), "Book + nickel ingot made " + result + ", expected the guide");
        helper.assertTrue(result.getMaxStackSize() == 1, "Guide should not stack");
        helper.succeed();
    }
}
