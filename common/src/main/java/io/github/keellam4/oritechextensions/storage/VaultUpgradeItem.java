package io.github.keellam4.oritechextensions.storage;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.Locale;

/** An upgrade card for a vault's upgrade screen (right-click a vault's side or top). */
public class VaultUpgradeItem extends Item {

    private final VaultUpgrade upgrade;

    public VaultUpgradeItem(VaultUpgrade upgrade, Properties properties) {
        super(properties);
        this.upgrade = upgrade;
    }

    public VaultUpgrade upgrade() {
        return upgrade;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        String key = upgrade.isCapacity() ? "tooltip.oritech_extensions.upgrade.capacity"
                : "tooltip.oritech_extensions.upgrade." + upgrade.name().toLowerCase(Locale.ROOT);
        lines.add(upgrade.isCapacity() ? Component.translatable(key, upgrade.multiplier()).withStyle(ChatFormatting.GRAY)
                : Component.translatable(key).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.oritech_extensions.upgrade.help").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
