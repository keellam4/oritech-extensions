package io.github.keellam4.oritechextensions.client;

import io.github.keellam4.oritechextensions.OritechExtensions;
import io.github.keellam4.oritechextensions.storage.VaultUpgrade;
import io.github.keellam4.oritechextensions.storage.VaultUpgradeItem;
import io.github.keellam4.oritechextensions.storage.VaultUpgradeMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Locale;

/** The vault upgrade screen: four upgrade slots and a line saying what they do together. */
public class VaultUpgradeScreen extends AbstractContainerScreen<VaultUpgradeMenu> {

    private static final ResourceLocation TEXTURE = OritechExtensions.id("textures/gui/vault_upgrades.png");
    private static final int TEXT_COLOUR = 0xFFDDE3E8;
    private static final int ACTIVE_COLOUR = 0xFF9CFFF5;

    public VaultUpgradeScreen(VaultUpgradeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, TEXT_COLOUR, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT_COLOUR, false);

        int multiplier = 0;
        var active = EnumSet.noneOf(VaultUpgrade.class);
        var upgrades = menu.upgrades();
        for (int i = 0; i < upgrades.getContainerSize(); i++) {
            if (upgrades.getItem(i).getItem() instanceof VaultUpgradeItem item) {
                multiplier += item.upgrade().multiplier();
                if (!item.upgrade().isCapacity()) active.add(item.upgrade());
            }
        }
        var parts = new ArrayList<String>();
        parts.add(Component.translatable("screen.oritech_extensions.vault_upgrades.capacity", Math.max(1, multiplier)).getString());
        for (var upgrade : active) {
            parts.add(Component.translatable("screen.oritech_extensions.vault_upgrades." + upgrade.name().toLowerCase(Locale.ROOT)).getString());
        }
        var line = String.join("  ·  ", parts);
        graphics.drawString(font, line, (imageWidth - font.width(line)) / 2, 46, active.isEmpty() && multiplier == 0 ? TEXT_COLOUR : ACTIVE_COLOUR, false);
        if (multiplier == 0 && active.isEmpty()) {
            var hint = Component.translatable("screen.oritech_extensions.vault_upgrades.hint");
            graphics.drawString(font, hint, (imageWidth - font.width(hint)) / 2, 58, 0xFF8A949E, false);
        }
    }
}
