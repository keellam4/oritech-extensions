package io.github.keellam4.oritechextensions.guide;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import io.github.keellam4.oritechextensions.platform.Platform;

import java.util.List;

/**
 * The Oritech Extensions guide. Right-click opens this mod's wiki in Oracle Index (the in-game wiki viewer
 * Oritech itself uses). Pages come from {@code wiki/} in the repo, the same files the website is built from.
 * Without Oracle Index installed it posts a link to the online guide instead.
 */
public class GuideBookItem extends Item {

    public GuideBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // Only the client has a screen to open. GuideBookClient is never loaded on a dedicated server.
        if (level.isClientSide && Platform.INSTANCE.isPhysicalClient()) {
            GuideBookClient.open(player);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.oritech_extensions.guide").withStyle(ChatFormatting.GRAY));
    }
}
