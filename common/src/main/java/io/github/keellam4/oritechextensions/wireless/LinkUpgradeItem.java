package io.github.keellam4.oritechextensions.wireless;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * A Cargo Link upgrade. Click it onto the Link in your inventory to install it. Infinite Range: the Link works
 * anywhere in its wall's dimension. Dimensional: anywhere, in any dimension (it includes Infinite Range).
 */
public class LinkUpgradeItem extends Item {

    public static final int INFINITE = 1;
    public static final int DIMENSIONAL = 2;

    private final int level;

    public LinkUpgradeItem(int level, Properties properties) {
        super(properties);
        this.level = level;
    }

    public int level() {
        return level;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable(level == DIMENSIONAL ? "tooltip.oritech_extensions.link_upgrade.dimensional"
                : "tooltip.oritech_extensions.link_upgrade.infinite").withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable("tooltip.oritech_extensions.link_upgrade.help").withStyle(ChatFormatting.GRAY));
    }
}
