package io.github.keellam4.oritechextensions.client;

import io.github.keellam4.oritechextensions.wireless.LinkUpgradeItem;
import io.github.keellam4.oritechextensions.wireless.UplinkMastMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** The Uplink Mast's screen: one upgrade slot, and what the upgrade in it does for the wall's Cargo Links. */
public class UplinkMastScreen extends AbstractContainerScreen<UplinkMastMenu> {

    private static final int PANEL = 0xFF2B2F36, EDGE = 0xFF0E1013, LIGHT = 0xFF4E545E, DARK = 0xFF16191E;
    private static final int ORANGE = 0xFFC36C2B, ORANGE_DARK = 0xFF9C3215, TEAL = 0xFF3EC9D5, TEXT = 0xFFDDE3E8, MUTED = 0xFF8A949E;

    public UplinkMastScreen(UplinkMastMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = UplinkMastMenu.INVENTORY_Y + 82;
        inventoryLabelY = UplinkMastMenu.INVENTORY_Y - 11;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, EDGE);
        graphics.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, PANEL);
        graphics.fill(x + 1, y + 1, x + imageWidth - 2, y + 2, LIGHT);
        graphics.fill(x + 6, y + 16, x + imageWidth - 6, y + 17, ORANGE);
        graphics.fill(x + 6, y + UplinkMastMenu.INVENTORY_Y - 14, x + imageWidth - 6, y + UplinkMastMenu.INVENTORY_Y - 13, ORANGE);
        // The upgrade slot, with a teal glow when something is in it.
        int sx = x + UplinkMastMenu.SLOT_X - 1, sy = y + UplinkMastMenu.SLOT_Y - 1;
        boolean filled = menu.getSlot(0).hasItem();
        graphics.fill(sx - 2, sy - 2, sx + 20, sy + 20, filled ? TEAL : ORANGE_DARK);
        graphics.fill(sx, sy, sx + 18, sy + 18, 0xFF1E2228);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) slot(graphics, x + 7 + col * 18, y + UplinkMastMenu.INVENTORY_Y - 1 + row * 18);
        }
        for (int col = 0; col < 9; col++) slot(graphics, x + 7 + col * 18, y + UplinkMastMenu.INVENTORY_Y + 57);
    }

    private void slot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, DARK);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF1E2228);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, TEXT, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
        var stack = menu.getSlot(0).getItem();
        String key = !(stack.getItem() instanceof LinkUpgradeItem upgrade) ? "screen.oritech_extensions.mast.none"
                : upgrade.level() == LinkUpgradeItem.DIMENSIONAL ? "screen.oritech_extensions.mast.dimensional" : "screen.oritech_extensions.mast.infinite";
        var line = Component.translatable(key);
        graphics.drawString(font, line, (imageWidth - font.width(line)) / 2, UplinkMastMenu.SLOT_Y + 24, stack.isEmpty() ? MUTED : TEAL, false);
    }
}
