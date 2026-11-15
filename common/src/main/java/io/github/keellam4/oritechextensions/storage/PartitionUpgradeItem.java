package io.github.keellam4.oritechextensions.storage;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Goes in a Silo Controller's upgrade slot: lets the silo hold more fluids at once. */
public class PartitionUpgradeItem extends Item {

    public static final int UNLIMITED = Integer.MAX_VALUE;

    private final int partitions;

    public PartitionUpgradeItem(int partitions, Properties properties) {
        super(properties);
        this.partitions = partitions;
    }

    public int partitions() {
        return partitions;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(partitions == UNLIMITED
                ? Component.translatable("tooltip.oritech_extensions.partition.unlimited").withStyle(ChatFormatting.AQUA)
                : Component.translatable("tooltip.oritech_extensions.partition.count", partitions).withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable("tooltip.oritech_extensions.partition.help").withStyle(ChatFormatting.GRAY));
    }
}
