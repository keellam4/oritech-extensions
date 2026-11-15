package io.github.keellam4.oritechextensions.storage;

import io.github.keellam4.oritechextensions.ModContent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

/** The Silo Controller's item: shows the fluid it carries when mined from a full silo. */
public class SiloControllerItem extends BlockItem {

    public SiloControllerItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        var fluid = stack.get(ModContent.FLUID_CONTENTS.get());
        if (fluid != null && !fluid.isEmpty()) {
            lines.add(Component.translatable("tooltip.oritech_extensions.silo.holds",
                    fluid.fluid().defaultFluidState().createLegacyBlock().getBlock().getName(),
                    String.format("%,d", fluid.amount() / 1000)).withStyle(ChatFormatting.AQUA));
        }
        lines.add(Component.translatable("tooltip.oritech_extensions.silo.help").withStyle(ChatFormatting.GRAY));
    }
}
