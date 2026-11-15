package io.github.keellam4.oritechextensions.wireless;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Right-click a Cargo Controller or Crafting Terminal with it: every block in that network stays chunk-loaded. */
public class NetworkAnchorItem extends Item {

    public NetworkAnchorItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.oritech_extensions.anchor").withStyle(ChatFormatting.GRAY));
    }
}
