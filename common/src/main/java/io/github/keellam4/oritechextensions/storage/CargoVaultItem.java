package io.github.keellam4.oritechextensions.storage;

import io.github.keellam4.oritechextensions.ModContent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

/** The Cargo Vault's item: keeps the vault's contents, and says what they are. */
public class CargoVaultItem extends BlockItem {

    public CargoVaultItem(Block block, Properties properties) {
        super(block, properties);
    }

    private String slotWord() {
        if (!(getBlock() instanceof CargoVaultBlock vault)) return "single";
        if (vault.layout() == VaultLayout.COMPACTING) return "compacting";
        return vault.slots() > 1 ? "multi" : "single";
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        var contents = stack.get(ModContent.VAULT_CONTENTS.get());
        if (contents == null || contents.isEmpty()) {
            lines.add(Component.translatable("tooltip.oritech_extensions.cargo_vault.empty." + slotWord()).withStyle(ChatFormatting.GRAY));
        } else {
            int shown = 0;
            int more = 0;
            for (var slot : contents.slots()) {
                if (slot.isEmpty()) continue;
                if (shown < 4) {
                    lines.add(Component.translatable("tooltip.oritech_extensions.cargo_vault.contains",
                            String.format("%,d", slot.count()), slot.item().getHoverName()).withStyle(ChatFormatting.GRAY));
                    shown++;
                } else {
                    more++;
                }
            }
            if (more > 0) lines.add(Component.translatable("tooltip.oritech_extensions.cargo_vault.more", more).withStyle(ChatFormatting.GRAY));
            if (contents.locked()) lines.add(Component.translatable("tooltip.oritech_extensions.cargo_vault.locked").withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.translatable("tooltip.oritech_extensions.cargo_vault.help").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
