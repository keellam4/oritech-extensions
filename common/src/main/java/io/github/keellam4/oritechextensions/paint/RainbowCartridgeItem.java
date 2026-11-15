package io.github.keellam4.oritechextensions.paint;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Rainbow Color Cartridge: used like Oritech's Color Cartridges; the painting itself is in {@link PaintTargets}. */
public class RainbowCartridgeItem extends Item {

    public RainbowCartridgeItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.oritech_extensions.rainbow_cartridge").withStyle(ChatFormatting.GRAY));
    }
}
