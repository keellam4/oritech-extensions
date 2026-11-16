package io.github.keellam4.oritechextensions.client;

import io.github.keellam4.oritechextensions.ModContent;
import io.github.keellam4.oritechextensions.OritechExtensions;
import io.github.keellam4.oritechextensions.storage.VaultSide;
import io.github.keellam4.oritechextensions.storage.VaultUpgrade;
import io.github.keellam4.oritechextensions.storage.VaultUpgradeItem;
import io.github.keellam4.oritechextensions.storage.VaultUpgradeMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The vault upgrade screen: a slot per kind of upgrade (an empty one shows a faded picture of what goes in it), the
 * lock, and for Pull and Push a small unfolded cube of the vault's sides to click on and off.
 */
public class VaultUpgradeScreen extends AbstractContainerScreen<VaultUpgradeMenu> {

    private static final ResourceLocation TEXTURE = OritechExtensions.id("textures/gui/vault_upgrades.png");
    private static final int TEXT = 0xFFDDE3E8;
    private static final int MUTED = 0xFF8A949E;
    private static final int TEAL = 0xFF3EC9D5;
    private static final int ORANGE = 0xFFE87F4A;

    private static final int CELL = 12;
    private static final int NET_Y = 58;
    private static final int PULL_X = 12;
    private static final int PUSH_X = 112;
    private static final int LOCK_X = 146;
    private static final int LOCK_Y = 19;
    /** Clear sits at the left end of the upgrade row, mirroring the lock on the right. */
    private static final int CLEAR_X = 14;
    /** Where each side sits in the unfolded cube: column, row. */
    private static final int[][] NET = {{1, 1}, {3, 1}, {0, 1}, {2, 1}, {1, 0}, {1, 2}};
    private static final String[] LETTERS = {"F", "B", "L", "R", "U", "D"};

    public VaultUpgradeScreen(VaultUpgradeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 200;
        inventoryLabelY = VaultUpgradeMenu.INVENTORY_Y - 11;
    }

    private boolean has(VaultUpgrade.Kind kind) {
        return !menu.upgrades().getItem(kind.slot()).isEmpty();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderSideTooltip(graphics, mouseX, mouseY);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        for (var kind : VaultUpgrade.Kind.values()) {
            int x = leftPos + VaultUpgradeMenu.UPGRADES_X + kind.slot() * 18;
            int y = topPos + VaultUpgradeMenu.UPGRADES_Y;
            if (has(kind)) {
                // Installed: a lit frame and a light under the slot.
                int colour = kind == VaultUpgrade.Kind.PUSH ? ORANGE : TEAL;
                graphics.renderOutline(x - 1, y - 1, 18, 18, colour);
                graphics.fill(x + 3, y + 18, x + 13, y + 20, colour);
            } else {
                // Empty: a very faint picture of what goes here, and a dark light.
                graphics.renderFakeItem(new ItemStack(ModContent.VAULT_UPGRADES.get(example(kind)).get()), x, y);
                graphics.fill(x, y, x + 16, y + 16, 0xE41E2126);
                graphics.fill(x + 3, y + 18, x + 13, y + 20, 0xFF2A2E34);
            }
        }
    }

    private static VaultUpgrade example(VaultUpgrade.Kind kind) {
        return switch (kind) {
            case CAPACITY -> VaultUpgrade.STEEL_CAPACITY;
            case VOID -> VaultUpgrade.VOID;
            case PULL -> VaultUpgrade.PULL;
            case PUSH -> VaultUpgrade.PUSH;
            case REDSTONE -> VaultUpgrade.REDSTONE;
        };
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, TEXT, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);

        drawLock(graphics, menu.locked());
        drawClear(graphics, menu.clearable());

        boolean pull = has(VaultUpgrade.Kind.PULL);
        boolean push = has(VaultUpgrade.Kind.PUSH);
        drawLabel(graphics, Component.translatable("screen.oritech_extensions.vault_upgrades.pull"), PULL_X, pull ? TEAL : MUTED);
        drawLabel(graphics, Component.translatable("screen.oritech_extensions.vault_upgrades.push"), PUSH_X, push ? ORANGE : MUTED);
        drawNet(graphics, PULL_X, pull, TEAL, false);
        drawNet(graphics, PUSH_X, push, ORANGE, true);

        // Capacity in the middle, between the two cubes.
        int multiplier = 0;
        var capacity = menu.upgrades().getItem(VaultUpgrade.Kind.CAPACITY.slot());
        if (capacity.getItem() instanceof VaultUpgradeItem item) multiplier = item.upgrade().multiplier() * capacity.getCount();
        var line1 = Component.translatable("screen.oritech_extensions.vault_upgrades.capacity_label");
        var line2 = Component.literal("×" + Math.max(1, multiplier));
        graphics.drawString(font, line1, 88 - font.width(line1) / 2, NET_Y + 6, MUTED, false);
        graphics.drawString(font, line2, 88 - font.width(line2) / 2, NET_Y + 18, multiplier > 0 ? 0xFF9CFFF5 : TEXT, false);
    }

    private void drawLabel(GuiGraphics graphics, Component text, int x, int colour) {
        graphics.drawString(font, text, x + (4 * (CELL + 1) - font.width(text)) / 2, NET_Y - 12, colour, false);
    }

    private void drawNet(GuiGraphics graphics, int x0, boolean enabled, int colour, boolean push) {
        for (var side : VaultSide.values()) {
            int x = x0 + NET[side.ordinal()][0] * (CELL + 1);
            int y = NET_Y + NET[side.ordinal()][1] * (CELL + 1);
            boolean on = push ? menu.pushes(side) : menu.pulls(side);
            int fill = !enabled ? 0xFF2A2E34 : on ? colour : 0xFF3A404A;
            graphics.fill(x, y, x + CELL, y + CELL, 0xFF0E1013);
            graphics.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, fill);
            var letter = LETTERS[side.ordinal()];
            graphics.drawString(font, letter, x + (CELL - font.width(letter)) / 2 + 1, y + 2,
                    !enabled ? 0xFF555B63 : on ? 0xFF101418 : 0xFFB8C0C8, false);
        }
    }

    private void drawLock(GuiGraphics graphics, boolean locked) {
        int x = LOCK_X;
        int y = LOCK_Y;
        graphics.fill(x, y, x + 18, y + 18, 0xFF0E1013);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, locked ? 0xFF6A4320 : 0xFF2B3038);
        int body = locked ? 0xFFFFC27A : 0xFF8A949E;
        // shackle: closed when locked, open (shifted up and right) when not
        int lift = locked ? 0 : 2;
        graphics.fill(x + 6, y + 4 - lift, x + 12, y + 5 - lift, body);
        graphics.fill(x + 5, y + 5 - lift, x + 6, y + 9, body);
        if (locked) graphics.fill(x + 12, y + 5, x + 13, y + 9, body);
        else graphics.fill(x + 12, y + 5 - lift, x + 13, y + 6, body);
        graphics.fill(x + 4, y + 9, x + 14, y + 15, body);
        graphics.fill(x + 8, y + 11, x + 10, y + 13, 0xFF101418);
    }

    /** Clear: a small eraser cross, lit when there's an empty type to forget. */
    private void drawClear(GuiGraphics graphics, boolean enabled) {
        int x = CLEAR_X;
        int y = LOCK_Y;
        graphics.fill(x, y, x + 18, y + 18, 0xFF0E1013);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, enabled ? 0xFF203A40 : 0xFF2B3038);
        int colour = enabled ? TEAL : 0xFF555B63;
        for (int i = 0; i < 8; i++) {
            graphics.fill(x + 5 + i, y + 5 + i, x + 6 + i, y + 6 + i, colour);
            graphics.fill(x + 12 - i, y + 5 + i, x + 13 - i, y + 6 + i, colour);
        }
    }

    /** Which side cell (0-5), lock or Clear the mouse is over, and in which cube; -1 for none. */
    private int buttonAt(double mouseX, double mouseY) {
        double x = mouseX - leftPos;
        double y = mouseY - topPos;
        if (x >= LOCK_X && x < LOCK_X + 18 && y >= LOCK_Y && y < LOCK_Y + 18) return VaultUpgradeMenu.BUTTON_LOCK;
        if (x >= CLEAR_X && x < CLEAR_X + 18 && y >= LOCK_Y && y < LOCK_Y + 18) return VaultUpgradeMenu.BUTTON_CLEAR;
        for (int cube = 0; cube < 2; cube++) {
            int x0 = cube == 0 ? PULL_X : PUSH_X;
            for (var side : VaultSide.values()) {
                int cx = x0 + NET[side.ordinal()][0] * (CELL + 1);
                int cy = NET_Y + NET[side.ordinal()][1] * (CELL + 1);
                if (x >= cx && x < cx + CELL && y >= cy && y < cy + CELL) {
                    return (cube == 0 ? VaultUpgradeMenu.BUTTON_PULL : VaultUpgradeMenu.BUTTON_PUSH) + side.ordinal();
                }
            }
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int id = buttonAt(mouseX, mouseY);
        if (button == 0 && id >= 0 && minecraft != null && minecraft.gameMode != null) {
            boolean enabled = id == VaultUpgradeMenu.BUTTON_LOCK
                    || (id == VaultUpgradeMenu.BUTTON_CLEAR ? menu.clearable() : id < VaultUpgradeMenu.BUTTON_PUSH ? has(VaultUpgrade.Kind.PULL) : has(VaultUpgrade.Kind.PUSH));
            if (enabled) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
                minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void renderSideTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        // Empty upgrade slots say what goes in them.
        if (hoveredSlot instanceof VaultUpgradeMenu.UpgradeSlot slot && !slot.hasItem() && menu.getCarried().isEmpty()) {
            graphics.renderComponentTooltip(font, List.of(
                    Component.translatable("screen.oritech_extensions.vault_upgrades.empty_slot",
                            Component.translatable("screen.oritech_extensions.vault_upgrades.kind." + slot.kind().name().toLowerCase(java.util.Locale.ROOT))),
                    Component.translatable("screen.oritech_extensions.vault_upgrades.slot_limit", slot.kind().limit()).withStyle(net.minecraft.ChatFormatting.GRAY)),
                    mouseX, mouseY);
            return;
        }
        int id = buttonAt(mouseX, mouseY);
        if (id < 0) return;
        if (id == VaultUpgradeMenu.BUTTON_CLEAR) {
            graphics.renderComponentTooltip(font, List.of(
                    Component.translatable("screen.oritech_extensions.vault_upgrades.clear"),
                    Component.translatable(menu.clearable() ? "screen.oritech_extensions.vault_upgrades.clear_help"
                            : "screen.oritech_extensions.vault_upgrades.clear_disabled").withStyle(net.minecraft.ChatFormatting.GRAY)), mouseX, mouseY);
            return;
        }
        if (id == VaultUpgradeMenu.BUTTON_LOCK) {
            graphics.renderComponentTooltip(font, List.of(
                    Component.translatable(menu.locked() ? "screen.oritech_extensions.vault_upgrades.locked" : "screen.oritech_extensions.vault_upgrades.unlocked"),
                    Component.translatable("screen.oritech_extensions.vault_upgrades.lock_help").withStyle(net.minecraft.ChatFormatting.GRAY)), mouseX, mouseY);
            return;
        }
        boolean push = id >= VaultUpgradeMenu.BUTTON_PUSH;
        var side = VaultSide.values()[id - (push ? VaultUpgradeMenu.BUTTON_PUSH : VaultUpgradeMenu.BUTTON_PULL)];
        boolean installed = has(push ? VaultUpgrade.Kind.PUSH : VaultUpgrade.Kind.PULL);
        boolean on = push ? menu.pushes(side) : menu.pulls(side);
        String state = !installed ? "missing" : on ? "on" : "off";
        graphics.renderComponentTooltip(font, List.of(side.displayName(),
                Component.translatable("screen.oritech_extensions.vault_upgrades." + (push ? "push" : "pull") + "_" + state)
                        .withStyle(net.minecraft.ChatFormatting.GRAY)), mouseX, mouseY);
    }
}
