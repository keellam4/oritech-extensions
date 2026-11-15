package io.github.keellam4.oritechextensions.client;

import io.github.keellam4.oritechextensions.OritechExtensions;
import io.github.keellam4.oritechextensions.platform.Platform;
import io.github.keellam4.oritechextensions.storage.CargoNetwork;
import io.github.keellam4.oritechextensions.storage.TerminalActionPayload;
import io.github.keellam4.oritechextensions.storage.TerminalMenu;
import io.github.keellam4.oritechextensions.storage.TerminalSyncPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Cargo Controller terminal: a searchable grid of everything in the network.
 * Left-click takes a stack, right-click takes one, shift-click sends a stack to your inventory. With items on the
 * cursor, left-click puts them all in and right-click puts one in. Type "@mod" to search by mod.
 */
public class TerminalScreen extends AbstractContainerScreen<TerminalMenu> {

    private static final ResourceLocation TEXTURE = OritechExtensions.id("textures/gui/cargo_terminal.png");
    private static final int COLUMNS = 9;
    private static final int ROWS = 5;
    private static final int GRID_X = 8;
    private static final int GRID_Y = 18;
    private static final int BAR_X = 175;
    private static final int TEXT = 0xFFDDE3E8;

    private static List<CargoNetwork.Entry> received = List.of();
    private static int receivedFor = -1;
    private static int receivedVaults;

    private EditBox search;
    private List<CargoNetwork.Entry> shown = List.of();
    private int scroll;
    private String lastQuery = "";
    private List<CargoNetwork.Entry> lastSource = null;

    public TerminalScreen(TerminalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 195;
        imageHeight = 207;
        inventoryLabelY = TerminalMenu.INVENTORY_Y - 11;
    }

    /** Payload handler (client thread). */
    public static void receive(TerminalSyncPayload payload) {
        received = payload.entries();
        receivedFor = payload.containerId();
        receivedVaults = payload.vaults();
    }

    @Override
    protected void init() {
        super.init();
        search = new EditBox(font, leftPos + 82, topPos + 5, 89, 10, Component.translatable("screen.oritech_extensions.terminal.search"));
        search.setBordered(false);
        search.setMaxLength(50);
        search.setTextColor(0xFFFFFFFF);
        search.setHint(Component.translatable("screen.oritech_extensions.terminal.search").withStyle(ChatFormatting.DARK_GRAY));
        addRenderableWidget(search);
    }

    private List<CargoNetwork.Entry> entries() {
        return receivedFor == menu.containerId ? received : List.of();
    }

    private void refilter() {
        var source = entries();
        var query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        if (source == lastSource && query.equals(lastQuery)) return;
        lastSource = source;
        lastQuery = query;
        var result = new ArrayList<CargoNetwork.Entry>();
        for (var entry : source) {
            if (query.isEmpty()) {
                result.add(entry);
            } else if (query.startsWith("@")) {
                if (BuiltInRegistries.ITEM.getKey(entry.item().getItem()).getNamespace().contains(query.substring(1))) result.add(entry);
            } else if (entry.item().getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)) {
                result.add(entry);
            }
        }
        shown = result;
        scroll = Math.min(scroll, maxScroll());
    }

    private int maxScroll() {
        return Math.max(0, (shown.size() + COLUMNS - 1) / COLUMNS - ROWS);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        refilter();
        super.render(graphics, mouseX, mouseY, partialTick);
        var hovered = entryAt(mouseX, mouseY);
        if (hovered != null && menu.getCarried().isEmpty()) {
            var lines = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), hovered.item()));
            lines.add(Component.translatable("screen.oritech_extensions.terminal.stored", String.format("%,d", hovered.count())).withStyle(ChatFormatting.AQUA));
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
        } else {
            renderTooltip(graphics, mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        int first = scroll * COLUMNS;
        for (int i = 0; i < COLUMNS * ROWS && first + i < shown.size(); i++) {
            var entry = shown.get(first + i);
            int x = leftPos + GRID_X + (i % COLUMNS) * 18;
            int y = topPos + GRID_Y + (i / COLUMNS) * 18;
            graphics.renderItem(entry.item(), x, y);
            graphics.renderItemDecorations(font, entry.item(), x, y, CargoVaultRendererText.short_(entry.count()));
        }
        var hovered = entryAt(mouseX, mouseY);
        if (hovered != null) {
            int index = shown.indexOf(hovered) - first;
            int x = leftPos + GRID_X + (index % COLUMNS) * 18;
            int y = topPos + GRID_Y + (index / COLUMNS) * 18;
            graphics.fill(x, y, x + 16, y + 16, 0x80FFFFFF);
        }
        // scrollbar
        int track = ROWS * 18 - 15;
        int handleY = topPos + GRID_Y + (maxScroll() == 0 ? 0 : track * scroll / maxScroll());
        graphics.fill(leftPos + BAR_X, handleY, leftPos + BAR_X + 12, handleY + 15, maxScroll() == 0 ? 0xFF4A4F56 : 0xFF3EC9D5);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, TEXT, false);
        var info = Component.translatable("screen.oritech_extensions.terminal.info", receivedFor == menu.containerId ? receivedVaults : 0, entries().size());
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
        graphics.drawString(font, info, imageWidth - 8 - font.width(info), inventoryLabelY, 0xFF8A949E, false);
    }

    private CargoNetwork.Entry entryAt(double mouseX, double mouseY) {
        int x = (int) mouseX - leftPos - GRID_X;
        int y = (int) mouseY - topPos - GRID_Y;
        if (x < 0 || y < 0 || x >= COLUMNS * 18 || y >= ROWS * 18) return null;
        int index = scroll * COLUMNS + (y / 18) * COLUMNS + x / 18;
        return index < shown.size() ? shown.get(index) : null;
    }

    private boolean overGrid(double mouseX, double mouseY) {
        double x = mouseX - leftPos - GRID_X;
        double y = mouseY - topPos - GRID_Y;
        return x >= 0 && y >= 0 && x < COLUMNS * 18 && y < ROWS * 18;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (overGrid(mouseX, mouseY) && (button == 0 || button == 1)) {
            var carried = menu.getCarried();
            if (!carried.isEmpty()) {
                send(button == 0 ? TerminalMenu.INSERT_CARRIED : TerminalMenu.INSERT_ONE, ItemStack.EMPTY);
                return true;
            }
            var entry = entryAt(mouseX, mouseY);
            if (entry != null) {
                int action = button == 1 ? TerminalMenu.TAKE_ONE : hasShiftDown() ? TerminalMenu.TAKE_TO_INVENTORY : TerminalMenu.TAKE_STACK;
                send(action, entry.item());
                return true;
            }
        }
        double barX = mouseX - leftPos - BAR_X;
        double barY = mouseY - topPos - GRID_Y;
        if (barX >= 0 && barX < 12 && barY >= 0 && barY < ROWS * 18 && maxScroll() > 0) {
            scroll = (int) Math.round(barY / (ROWS * 18) * maxScroll());
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void send(int action, ItemStack item) {
        Platform.INSTANCE.sendToServer(new TerminalActionPayload(menu.containerId, action, item.copyWithCount(1)));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll() > 0) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (search.isFocused() && keyCode != GLFW.GLFW_KEY_ESCAPE) {
            search.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Counts in the grid: 950, 1.2k, 12k, 1.2M. */
    static final class CargoVaultRendererText {
        static String short_(long count) {
            if (count < 1000) return Long.toString(count);
            if (count < 10_000) return String.format(Locale.ROOT, "%.1fk", count / 1000f);
            if (count < 1_000_000) return (count / 1000) + "k";
            return String.format(Locale.ROOT, "%.1fM", count / 1_000_000f);
        }
    }
}
