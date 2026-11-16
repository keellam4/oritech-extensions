package io.github.keellam4.oritechextensions.client;

import io.github.keellam4.oritechextensions.storage.SiloControllerBlockEntity;
import io.github.keellam4.oritechextensions.storage.SiloMenu;
import io.github.keellam4.oritechextensions.storage.SiloTank;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * The Silo Controller screen: a bar of the whole silo split by fluid, one row per fluid with its rules, and the
 * partition upgrade slot. Row buttons: Limit and Reserve (left-click up, right-click down, shift-click to type an
 * exact amount in buckets), Lock and Void.
 * Shift-click a fluid's name to move it up (pipes pulling out take from the top row first).
 */
public class SiloScreen extends AbstractContainerScreen<SiloMenu> {

    private static final int PANEL = 0xFF2B2F36, EDGE = 0xFF0E1013, LIGHT = 0xFF4E545E, DARK = 0xFF16191E;
    private static final int ORANGE = 0xFFC36C2B, ORANGE_DARK = 0xFF9C3215, TEAL = 0xFF3EC9D5, TEXT = 0xFFDDE3E8, MUTED = 0xFF8A949E;
    private static final int ROW_Y = 30, ROW_H = 20, BUTTON_X = 100, BUTTON = 16, GAP = 2;
    private static final String[] BUTTONS = {"limit", "reserve", "lock", "void"};

    private int scroll;

    /** The box for typing an exact limit or reserve, while open: which row, and which of the two. */
    @Nullable
    private net.minecraft.client.gui.components.EditBox exactBox;
    private int exactRow;
    private boolean exactReserve;

    public SiloScreen(SiloMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = SiloMenu.INVENTORY_Y + 82;
        inventoryLabelY = SiloMenu.INVENTORY_Y - 11;
    }

    @Nullable
    private SiloControllerBlockEntity silo() {
        var level = Minecraft.getInstance().level;
        return level != null && level.getBlockEntity(menu.pos()) instanceof SiloControllerBlockEntity silo ? silo : null;
    }

    private List<SiloTank> tanks() {
        var silo = silo();
        return silo == null ? List.of() : silo.tanks();
    }

    private static Component name(Fluid fluid) {
        return fluid.defaultFluidState().createLegacyBlock().getBlock().getName();
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        var tip = tooltip(mouseX, mouseY);
        if (tip != null) graphics.renderComponentTooltip(font, tip, mouseX, mouseY);
        else renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, EDGE);
        graphics.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, PANEL);
        graphics.fill(x + 1, y + 1, x + imageWidth - 2, y + 2, LIGHT);
        graphics.fill(x + 6, y + 16, x + imageWidth - 6, y + 17, ORANGE);
        graphics.fill(x + 6, y + SiloMenu.INVENTORY_Y - 14, x + imageWidth - 6, y + SiloMenu.INVENTORY_Y - 13, ORANGE);

        var silo = silo();
        var tanks = tanks();
        long capacity = silo == null ? 0 : silo.fluidCapacity();

        // The whole silo: one coloured stretch per fluid, reserved space hatched, free space dark.
        int barX = x + 8, barY = y + 19, barW = 160;
        graphics.fill(barX - 1, barY - 1, barX + barW + 1, barY + 7, EDGE);
        graphics.fill(barX, barY, barX + barW, barY + 6, DARK);
        int at = 0;
        for (var tank : tanks) {
            int width = capacity <= 0 ? 0 : (int) Math.round(barW * (double) tank.amount / capacity);
            int colour = 0xFF000000 | fluidColour(tank.fluid);
            graphics.fill(barX + at, barY, barX + Math.min(barW, at + width), barY + 6, colour);
            at += width;
        }

        // Rows.
        for (int i = 0; i < SiloMenu.ROWS; i++) {
            int index = scroll + i;
            int ry = y + ROW_Y + i * ROW_H;
            graphics.fill(x + 6, ry - 1, x + imageWidth - 6, ry + ROW_H - 3, i % 2 == 0 ? 0xFF262A30 : 0xFF23272D);
            if (index >= tanks.size()) continue;
            var tank = tanks.get(index);
            drawFluidIcon(graphics, tank.fluid, x + 8, ry);
            var label = font.plainSubstrByWidth(name(tank.fluid).getString(), BUTTON_X - 30);
            graphics.drawString(font, label, x + 27, ry, TEXT, false);
            var amount = String.format(Locale.ROOT, "%,d B", tank.amount / 1000);
            graphics.drawString(font, amount, x + 27, ry + 9, MUTED, false);
            for (int b = 0; b < BUTTONS.length; b++) drawButton(graphics, tank, b, x + BUTTON_X + b * (BUTTON + GAP), ry, mouseX, mouseY);
        }
        if (tanks.isEmpty()) {
            var empty = Component.translatable("screen.oritech_extensions.silo.empty");
            graphics.drawString(font, empty, x + (imageWidth - font.width(empty)) / 2, y + ROW_Y + 20, MUTED, false);
        }

        // Upgrade slot and inventory slots.
        slot(graphics, x + SiloMenu.UPGRADE_X - 1, y + SiloMenu.UPGRADE_Y - 1, true);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) slot(graphics, x + 7 + col * 18, y + SiloMenu.INVENTORY_Y - 1 + row * 18, false);
        }
        for (int col = 0; col < 9; col++) slot(graphics, x + 7 + col * 18, y + SiloMenu.INVENTORY_Y + 57, false);
    }

    private void slot(GuiGraphics graphics, int x, int y, boolean accent) {
        graphics.fill(x, y, x + 18, y + 18, accent ? ORANGE_DARK : DARK);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF1E2228);
    }

    private void drawButton(GuiGraphics graphics, SiloTank tank, int button, int x, int y, int mouseX, int mouseY) {
        boolean over = mouseX >= x && mouseX < x + BUTTON && mouseY >= y && mouseY < y + BUTTON;
        boolean on = switch (button) {
            case 0 -> tank.limit > 0 || tank.limitMb > 0;
            case 1 -> tank.reserve > 0 || tank.reserveMb > 0;
            case 2 -> tank.locked;
            default -> tank.voids;
        };
        graphics.fill(x, y, x + BUTTON, y + BUTTON, on ? TEAL : EDGE);
        graphics.fill(x + 1, y + 1, x + BUTTON - 1, y + BUTTON - 1, over ? 0xFF3A4048 : 0xFF1E2228);
        String text = switch (button) {
            case 0 -> tank.limitMb > 0 ? shortBuckets(tank.limitMb) : tank.limit > 0 ? Integer.toString(tank.limit) : "L";
            case 1 -> tank.reserveMb > 0 ? shortBuckets(tank.reserveMb) : tank.reserve > 0 ? Integer.toString(tank.reserve) : "R";
            case 2 -> "▣";
            default -> "✕";
        };
        // Exact amounts (buckets) in orange, percentages in teal.
        boolean exact = (button == 0 && tank.limitMb > 0) || (button == 1 && tank.reserveMb > 0);
        int colour = exact ? 0xFFE87F4A : on ? TEAL : MUTED;
        if (font.width(text) > BUTTON - 2) {
            var pose = graphics.pose();
            pose.pushPose();
            pose.translate(x + BUTTON / 2f, y + 5, 0);
            pose.scale(0.6f, 0.6f, 1);
            graphics.drawString(font, text, -font.width(text) / 2, 0, colour, false);
            pose.popPose();
            return;
        }
        graphics.drawString(font, text, x + (BUTTON - font.width(text)) / 2 + 1, y + 4, colour, false);
    }

    /** 950, 2.5, 1.2k, 45k, 1.2M (buckets), to fit a button. */
    static String shortBuckets(long millibuckets) {
        double buckets = millibuckets / 1000.0;
        if (buckets < 10 && buckets != Math.floor(buckets)) return String.format(Locale.ROOT, "%.1f", buckets);
        if (buckets < 1000) return Long.toString(Math.round(buckets));
        if (buckets < 10_000) return String.format(Locale.ROOT, "%.1fk", buckets / 1000);
        if (buckets < 1_000_000) return Math.round(buckets / 1000) + "k";
        return String.format(Locale.ROOT, "%.1fM", buckets / 1_000_000);
    }

    /** 1,250.5 B */
    static String longBuckets(long millibuckets) {
        return millibuckets % 1000 == 0 ? String.format(Locale.ROOT, "%,d B", millibuckets / 1000)
                : String.format(Locale.ROOT, "%,.3f B", millibuckets / 1000.0).replaceAll("0+ B$", " B");
    }

    /** Parses "1250.5", "1,250", "2k" into millibuckets; -1 if it isn't a number. Empty or 0 clears. */
    static long parseBuckets(String text) {
        var clean = text.trim().toLowerCase(Locale.ROOT).replace(",", "").replace("b", "").trim();
        if (clean.isEmpty()) return 0;
        double scale = 1;
        if (clean.endsWith("k")) { scale = 1000; clean = clean.substring(0, clean.length() - 1); }
        else if (clean.endsWith("m")) { scale = 1_000_000; clean = clean.substring(0, clean.length() - 1); }
        try {
            double value = Double.parseDouble(clean.trim()) * scale;
            return value < 0 || Double.isNaN(value) ? -1 : Math.round(value * 1000);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void drawFluidIcon(GuiGraphics graphics, Fluid fluid, int x, int y) {
        var sprite = ClientPlatform.INSTANCE.fluidSprite(fluid);
        int colour = fluidColour(fluid);
        graphics.blit(x, y, 0, 16, 16, sprite, (colour >> 16 & 255) / 255f, (colour >> 8 & 255) / 255f, (colour & 255) / 255f, 1f);
    }

    private int fluidColour(Fluid fluid) {
        var mc = Minecraft.getInstance();
        return ClientPlatform.INSTANCE.fluidColour(fluid, mc.level, mc.player == null ? null : mc.player.blockPosition());
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, TEXT, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
        var silo = silo();
        if (silo == null) return;
        long capacity = silo.fluidCapacity();
        var total = String.format(Locale.ROOT, "%,d / %,d B", silo.amount() / 1000, capacity / 1000);
        graphics.drawString(font, total, imageWidth - 8 - font.width(total), titleLabelY, MUTED, false);
        int partitions = silo.partitions();
        var count = Component.translatable("screen.oritech_extensions.silo.fluids", silo.tanks().size(),
                partitions == Integer.MAX_VALUE ? "∞" : Integer.toString(partitions));
        graphics.drawString(font, count, 8, SiloMenu.UPGRADE_Y + 5, TEXT, false);
    }

    @Nullable
    private List<Component> tooltip(int mouseX, int mouseY) {
        int[] hit = hit(mouseX, mouseY);
        if (hit == null) return null;
        var tanks = tanks();
        if (hit[0] >= tanks.size()) return null;
        var tank = tanks.get(hit[0]);
        if (hit[1] < 0) {
            return List.of(name(tank.fluid), Component.literal(String.format(Locale.ROOT, "%,d mB", tank.amount)).withStyle(ChatFormatting.AQUA),
                    Component.translatable("screen.oritech_extensions.silo.move").withStyle(ChatFormatting.GRAY));
        }
        var key = "screen.oritech_extensions.silo." + BUTTONS[hit[1]];
        Component state = switch (hit[1]) {
            case 0 -> tank.limitMb > 0 ? Component.literal(longBuckets(tank.limitMb)) : tank.limit > 0 ? Component.literal(tank.limit + "%")
                    : Component.translatable("screen.oritech_extensions.silo.none");
            case 1 -> tank.reserveMb > 0 ? Component.literal(longBuckets(tank.reserveMb)) : tank.reserve > 0 ? Component.literal(tank.reserve + "%")
                    : Component.translatable("screen.oritech_extensions.silo.none");
            case 2 -> Component.translatable(tank.locked ? "options.on" : "options.off");
            default -> Component.translatable(tank.voids ? "options.on" : "options.off");
        };
        return List.of(Component.translatable(key, state), Component.translatable(key + ".help").withStyle(ChatFormatting.GRAY));
    }

    /** {row index, button} under the mouse; button -1 is the fluid's name. */
    @Nullable
    private int[] hit(double mouseX, double mouseY) {
        double rx = mouseX - leftPos, ry = mouseY - topPos - ROW_Y;
        if (ry < 0 || ry >= SiloMenu.ROWS * ROW_H || rx < 8 || rx >= imageWidth - 6) return null;
        int row = (int) (ry / ROW_H);
        if (ry - row * ROW_H >= BUTTON) return null;
        int index = scroll + row;
        if (rx < BUTTON_X) return new int[]{index, -1};
        int button = (int) ((rx - BUTTON_X) / (BUTTON + GAP));
        if (button >= BUTTONS.length || (rx - BUTTON_X) - button * (BUTTON + GAP) >= BUTTON) return null;
        return new int[]{index, button};
    }

    private void openExactBox(int row, boolean reserve, double mouseY) {
        closeExactBox();
        var tank = tanks().get(row);
        long current = reserve ? tank.reserveMb : tank.limitMb;
        int y = topPos + ROW_Y + (row - scroll) * ROW_H;
        var box = new net.minecraft.client.gui.components.EditBox(font, leftPos + 27, y, BUTTON_X - 30, 16,
                Component.translatable("screen.oritech_extensions.silo.exact"));
        box.setMaxLength(16);
        box.setHint(Component.translatable("screen.oritech_extensions.silo.exact_hint").withStyle(ChatFormatting.DARK_GRAY));
        if (current > 0) box.setValue(longBuckets(current).replace(" B", "").replace(",", ""));
        box.setFilter(text -> text.matches("[0-9.,kKmMbB ]*"));
        exactBox = addRenderableWidget(box);
        exactRow = row;
        exactReserve = reserve;
        setFocused(box);
        box.setFocused(true);
    }

    private void closeExactBox() {
        if (exactBox != null) removeWidget(exactBox);
        exactBox = null;
    }

    private void commitExact() {
        if (exactBox == null) return;
        long millibuckets = parseBuckets(exactBox.getValue());
        if (millibuckets >= 0) {
            io.github.keellam4.oritechextensions.platform.Platform.INSTANCE.sendToServer(
                    new io.github.keellam4.oritechextensions.storage.SiloExactPayload(menu.containerId, exactRow, exactReserve, millibuckets));
        }
        closeExactBox();
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (exactBox != null) {
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) commitExact();
            else if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) closeExactBox();
            else exactBox.keyPressed(key, scanCode, modifiers);
            return true; // typing "e" mustn't close the screen
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        if (exactBox != null) return exactBox.charTyped(character, modifiers);
        return super.charTyped(character, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        if (exactBox != null && !exactBox.isMouseOver(mouseX, mouseY)) commitExact();
        else if (exactBox != null) return exactBox.mouseClicked(mouseX, mouseY, mouseButton);
        int[] hit = hit(mouseX, mouseY);
        if (hit != null && hit[0] < tanks().size() && (mouseButton == 0 || mouseButton == 1)) {
            int action;
            if (hit[1] < 0) {
                if (!hasShiftDown()) return true;
                action = 6;
            } else if (hit[1] <= 1 && hasShiftDown()) {
                openExactBox(hit[0], hit[1] == 1, mouseY);
                return true;
            } else {
                action = switch (hit[1]) {
                    case 0 -> mouseButton == 1 ? 1 : 0;
                    case 1 -> mouseButton == 1 ? 3 : 2;
                    case 2 -> 4;
                    default -> 5;
                };
            }
            if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, hit[0] * 8 + action);
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = Math.max(0, tanks().size() - SiloMenu.ROWS);
        if (max > 0 && mouseY < topPos + ROW_Y + SiloMenu.ROWS * ROW_H) {
            scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
