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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The Cargo Controller and Crafting Terminal screens: a searchable, sortable grid of everything in the network.
 * <ul>
 *     <li>Left-click takes a stack, right-click one, shift-click sends a stack to your inventory.</li>
 *     <li>With items on the cursor, left-click puts them all in, right-click one.</li>
 *     <li>Fluids: click with an empty bucket to fill it, with a full one to empty it in.</li>
 *     <li>Buttons on the right: sort by amount, name or mod; direction; show items, fluids or both.</li>
 *     <li>Crafting Terminal: a 3 × 3 grid under the list that refills itself from the network.</li>
 * </ul>
 */
public class TerminalScreen extends AbstractContainerScreen<TerminalMenu> {

    private static final ResourceLocation TEXTURE = OritechExtensions.id("textures/gui/cargo_terminal.png");
    private static final ResourceLocation CRAFTING_TEXTURE = OritechExtensions.id("textures/gui/crafting_terminal.png");
    private static final int TEXTURE_W = 256;
    private static final int TEXTURE_H = 288;

    private static final int COLUMNS = 9;
    private static final int GRID_X = 8;
    private static final int GRID_Y = 20;
    private static final int BAR_X = 175;
    private static final int BUTTON_X = 192;
    private static final int TEXT = 0xFFDDE3E8;
    private static final int MUTED = 0xFF8A949E;
    private static final int TEAL = 0xFF3EC9D5;

    enum Sort { AMOUNT, NAME, MOD }

    enum Show { ALL, ITEMS, FLUIDS }

    // Remembered while the game runs.
    private static Sort sort = Sort.AMOUNT;
    private static boolean descending = true;
    private static Show show = Show.ALL;

    private static List<CargoNetwork.Entry> received = List.of();
    private static int receivedFor = -1;
    private static int receivedVaults;

    private final int rows;
    private EditBox search;
    private List<CargoNetwork.Entry> shown = List.of();
    private int scroll;
    private String lastFilter = null;
    private List<CargoNetwork.Entry> lastSource = null;
    private boolean dragging;

    public TerminalScreen(TerminalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        rows = menu.isCrafting() ? TerminalMenu.CRAFTING_ROWS : TerminalMenu.ROWS;
        imageWidth = 212;
        imageHeight = TerminalMenu.inventoryY(menu.isCrafting()) + 82;
        inventoryLabelY = TerminalMenu.inventoryY(menu.isCrafting()) - 11;
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
        search = new EditBox(font, leftPos + 107, topPos + 6, 78, 10, Component.translatable("screen.oritech_extensions.terminal.search"));
        search.setBordered(false);
        search.setMaxLength(50);
        search.setTextColor(0xFFFFFFFF);
        search.setHint(Component.translatable("screen.oritech_extensions.terminal.search").withStyle(ChatFormatting.DARK_GRAY));
        addRenderableWidget(search);
    }

    // ---------------------------------------------------------------- the list

    private List<CargoNetwork.Entry> entries() {
        return receivedFor == menu.containerId ? received : List.of();
    }

    private static Component name(CargoNetwork.Entry entry) {
        return entry.isFluid() ? entry.fluid().defaultFluidState().createLegacyBlock().getBlock().getName() : entry.item().getHoverName();
    }

    private static String mod(CargoNetwork.Entry entry) {
        var key = entry.isFluid() ? BuiltInRegistries.FLUID.getKey(entry.fluid()) : BuiltInRegistries.ITEM.getKey(entry.item().getItem());
        return key.getNamespace();
    }

    private void refilter() {
        var source = entries();
        var query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        var filter = query + "|" + sort + descending + show;
        if (source == lastSource && filter.equals(lastFilter)) return;
        lastSource = source;
        lastFilter = filter;
        var result = new ArrayList<CargoNetwork.Entry>();
        for (var entry : source) {
            if (show == Show.ITEMS && entry.isFluid() || show == Show.FLUIDS && !entry.isFluid()) continue;
            if (query.startsWith("@")) {
                if (!mod(entry).contains(query.substring(1))) continue;
            } else if (!query.isEmpty() && !name(entry).getString().toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }
            result.add(entry);
        }
        Comparator<CargoNetwork.Entry> order = switch (sort) {
            case AMOUNT -> Comparator.comparingLong(e -> e.isFluid() ? e.count() / 1000 : e.count());
            case NAME -> Comparator.comparing(e -> name(e).getString().toLowerCase(Locale.ROOT));
            case MOD -> Comparator.<CargoNetwork.Entry, String>comparing(TerminalScreen::mod).thenComparing(e -> name(e).getString());
        };
        result.sort(descending ? order.reversed() : order);
        shown = result;
        scroll = Math.min(scroll, maxScroll());
    }

    private int maxScroll() {
        return Math.max(0, (shown.size() + COLUMNS - 1) / COLUMNS - rows);
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        refilter();
        super.render(graphics, mouseX, mouseY, partialTick);
        var hovered = entryAt(mouseX, mouseY);
        int button = buttonAt(mouseX, mouseY);
        if (hovered != null && hovered.isFluid()) {
            graphics.renderComponentTooltip(font, List.of(name(hovered),
                    Component.translatable("screen.oritech_extensions.terminal.stored_fluid", String.format("%,d", hovered.count())).withStyle(ChatFormatting.AQUA),
                    Component.translatable("screen.oritech_extensions.terminal.bucket_help").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
        } else if (hovered != null && menu.getCarried().isEmpty()) {
            var lines = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), hovered.item()));
            lines.add(Component.translatable("screen.oritech_extensions.terminal.stored", String.format("%,d", hovered.count())).withStyle(ChatFormatting.AQUA));
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
        } else if (overClear(mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.translatable("screen.oritech_extensions.terminal.clear"), mouseX, mouseY);
        } else if (button >= 0) {
            graphics.renderTooltip(font, buttonTooltip(button), mouseX, mouseY);
        } else {
            renderTooltip(graphics, mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(menu.isCrafting() ? CRAFTING_TEXTURE : TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, TEXTURE_W, TEXTURE_H);

        int first = scroll * COLUMNS;
        var hovered = entryAt(mouseX, mouseY);
        for (int i = 0; i < COLUMNS * rows && first + i < shown.size(); i++) {
            var entry = shown.get(first + i);
            int x = leftPos + GRID_X + (i % COLUMNS) * 18;
            int y = topPos + GRID_Y + (i / COLUMNS) * 18;
            if (entry == hovered) graphics.fill(x - 1, y - 1, x + 17, y + 17, 0x553EC9D5);
            if (entry.isFluid()) {
                drawFluid(graphics, entry, x, y);
            } else {
                graphics.renderItem(entry.item(), x, y);
                graphics.renderItemDecorations(font, entry.item(), x, y, shortCount(entry.count()));
            }
        }
        if (shown.isEmpty()) {
            var empty = Component.translatable(entries().isEmpty() ? "screen.oritech_extensions.terminal.empty" : "screen.oritech_extensions.terminal.no_match");
            graphics.drawString(font, empty, leftPos + GRID_X + (COLUMNS * 18 - font.width(empty)) / 2, topPos + GRID_Y + rows * 9 - 4, MUTED, false);
        }

        // Scroll handle: teal with grip lines.
        int track = rows * 18 - 15;
        int handleY = topPos + GRID_Y + (maxScroll() == 0 ? 0 : track * scroll / maxScroll());
        int handleColour = maxScroll() == 0 ? 0xFF4A4F56 : TEAL;
        graphics.fill(leftPos + BAR_X, handleY, leftPos + BAR_X + 11, handleY + 15, handleColour);
        for (int line = 0; line < 3; line++) {
            graphics.fill(leftPos + BAR_X + 3, handleY + 5 + line * 2, leftPos + BAR_X + 8, handleY + 6 + line * 2, 0x66000000);
        }

        drawButton(graphics, 0, switch (sort) {
            case AMOUNT -> new ItemStack(Items.COMPARATOR);
            case NAME -> new ItemStack(Items.NAME_TAG);
            case MOD -> new ItemStack(Items.BOOK);
        }, mouseX, mouseY);
        drawButton(graphics, 1, ItemStack.EMPTY, mouseX, mouseY);
        drawButton(graphics, 2, switch (show) {
            case ALL -> new ItemStack(Items.HOPPER);
            case ITEMS -> new ItemStack(Items.CHEST);
            case FLUIDS -> new ItemStack(Items.WATER_BUCKET);
        }, mouseX, mouseY);
        // Direction arrow, drawn in the middle button.
        int ax = leftPos + BUTTON_X + 5;
        int ay = topPos + GRID_Y + 18 + 5;
        for (int i = 0; i < 4; i++) {
            int y = descending ? ay + i : ay + 3 - i;
            graphics.fill(ax + i, y, ax + 8 - i, y + 1, TEAL);
        }
        if (menu.isCrafting()) drawClearButton(graphics, mouseX, mouseY);
    }

    private void drawButton(GuiGraphics graphics, int index, ItemStack icon, int mouseX, int mouseY) {
        int x = leftPos + BUTTON_X;
        int y = topPos + GRID_Y + index * 18;
        boolean over = buttonAt(mouseX, mouseY) == index;
        graphics.fill(x, y, x + 18, y + 18, 0xFF0E1013);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, over ? 0xFF3A4048 : 0xFF2B3038);
        if (!icon.isEmpty()) graphics.renderFakeItem(icon, x + 1, y + 1);
    }

    private void drawClearButton(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = leftPos + TerminalMenu.CRAFT_X - 20;
        int y = topPos + TerminalMenu.CRAFT_Y;
        boolean over = overClear(mouseX, mouseY);
        graphics.fill(x, y, x + 14, y + 14, 0xFF0E1013);
        graphics.fill(x + 1, y + 1, x + 13, y + 13, over ? 0xFF6A3A20 : 0xFF2B3038);
        for (int i = 0; i < 6; i++) {
            graphics.fill(x + 4 + i, y + 4 + i, x + 5 + i, y + 5 + i, 0xFFE87F4A);
            graphics.fill(x + 9 - i, y + 4 + i, x + 10 - i, y + 5 + i, 0xFFE87F4A);
        }
    }

    /** A fluid entry: its texture, tinted, with the amount in buckets. */
    private void drawFluid(GuiGraphics graphics, CargoNetwork.Entry entry, int x, int y) {
        var sprite = ClientPlatform.INSTANCE.fluidSprite(entry.fluid());
        int colour = ClientPlatform.INSTANCE.fluidColour(entry.fluid(), minecraft == null ? null : minecraft.level,
                minecraft == null || minecraft.player == null ? null : minecraft.player.blockPosition());
        float r = (colour >> 16 & 255) / 255f, g = (colour >> 8 & 255) / 255f, b = (colour & 255) / 255f;
        graphics.blit(x, y, 0, 16, 16, sprite, r, g, b, 1f);
        var text = shortCount(entry.count() / 1000) + "B";
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 200);
        graphics.drawString(font, text, x + 17 - font.width(text), y + 9, 0xFFFFFFFF, true);
        graphics.pose().popPose();
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY + 1, TEXT, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
        var info = Component.translatable("screen.oritech_extensions.terminal.info",
                receivedFor == menu.containerId ? receivedVaults : 0, shown.size());
        graphics.drawString(font, info, imageWidth - 8 - font.width(info), inventoryLabelY, MUTED, false);
        if (menu.isCrafting()) {
            // The arrow between the grid and the result.
            int ax = TerminalMenu.CRAFT_X + 3 * 18 + 6;
            int ay = TerminalMenu.RESULT_Y + 4;
            graphics.fill(ax, ay + 3, ax + 18, ay + 5, 0xFF4A4F56);
            for (int i = 0; i < 4; i++) graphics.fill(ax + 18 + i, ay + i, ax + 19 + i, ay + 8 - i, 0xFF4A4F56);
        }
    }

    private Component buttonTooltip(int button) {
        return switch (button) {
            case 0 -> Component.translatable("screen.oritech_extensions.terminal.sort." + sort.name().toLowerCase(Locale.ROOT));
            case 1 -> Component.translatable(descending ? "screen.oritech_extensions.terminal.descending" : "screen.oritech_extensions.terminal.ascending");
            default -> Component.translatable("screen.oritech_extensions.terminal.show." + show.name().toLowerCase(Locale.ROOT));
        };
    }

    // ---------------------------------------------------------------- input

    private CargoNetwork.Entry entryAt(double mouseX, double mouseY) {
        int x = (int) mouseX - leftPos - GRID_X;
        int y = (int) mouseY - topPos - GRID_Y;
        if (x < 0 || y < 0 || x >= COLUMNS * 18 || y >= rows * 18) return null;
        int index = scroll * COLUMNS + (y / 18) * COLUMNS + x / 18;
        return index < shown.size() ? shown.get(index) : null;
    }

    private boolean overGrid(double mouseX, double mouseY) {
        double x = mouseX - leftPos - GRID_X;
        double y = mouseY - topPos - GRID_Y;
        return x >= 0 && y >= 0 && x < COLUMNS * 18 && y < rows * 18;
    }

    private int buttonAt(double mouseX, double mouseY) {
        double x = mouseX - leftPos - BUTTON_X;
        double y = mouseY - topPos - GRID_Y;
        if (x < 0 || x >= 18 || y < 0 || y >= 3 * 18) return -1;
        return (int) (y / 18);
    }

    private boolean overClear(double mouseX, double mouseY) {
        if (!menu.isCrafting()) return false;
        double x = mouseX - leftPos - (TerminalMenu.CRAFT_X - 20);
        double y = mouseY - topPos - TerminalMenu.CRAFT_Y;
        return x >= 0 && x < 14 && y >= 0 && y < 14;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int pressed = buttonAt(mouseX, mouseY);
        if (pressed >= 0) {
            boolean back = button == 1;
            switch (pressed) {
                case 0 -> sort = Sort.values()[Math.floorMod(sort.ordinal() + (back ? -1 : 1), Sort.values().length)];
                case 1 -> descending = !descending;
                default -> show = Show.values()[Math.floorMod(show.ordinal() + (back ? -1 : 1), Show.values().length)];
            }
            click();
            return true;
        }
        if (overClear(mouseX, mouseY)) {
            send(TerminalMenu.CLEAR_GRID, ItemStack.EMPTY, Fluids.EMPTY);
            click();
            return true;
        }
        if (overGrid(mouseX, mouseY) && (button == 0 || button == 1)) {
            var carried = menu.getCarried();
            var clicked = entryAt(mouseX, mouseY);
            if (clicked != null && clicked.isFluid()) {
                if (carried.is(Items.BUCKET) || TerminalMenu.fluidInBucket(carried) != null) {
                    send(TerminalMenu.BUCKET, ItemStack.EMPTY, clicked.fluid());
                }
                return true;
            }
            if (!carried.isEmpty()) {
                send(button == 0 ? TerminalMenu.INSERT_CARRIED : TerminalMenu.INSERT_ONE, ItemStack.EMPTY, Fluids.EMPTY);
                return true;
            }
            if (clicked != null) {
                int action = button == 1 ? TerminalMenu.TAKE_ONE : hasShiftDown() ? TerminalMenu.TAKE_TO_INVENTORY : TerminalMenu.TAKE_STACK;
                send(action, clicked.item(), Fluids.EMPTY);
                return true;
            }
        }
        if (overBar(mouseX, mouseY)) {
            dragging = true;
            scrollTo(mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean overBar(double mouseX, double mouseY) {
        double x = mouseX - leftPos - BAR_X;
        double y = mouseY - topPos - GRID_Y;
        return x >= 0 && x < 12 && y >= 0 && y < rows * 18 && maxScroll() > 0;
    }

    private void scrollTo(double mouseY) {
        double y = mouseY - topPos - GRID_Y - 7.5;
        scroll = (int) Math.round(Math.max(0, Math.min(1, y / (rows * 18 - 15))) * maxScroll());
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging) {
            scrollTo(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void click() {
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
    }

    private void send(int action, ItemStack item, Fluid fluid) {
        Platform.INSTANCE.sendToServer(new TerminalActionPayload(menu.containerId, action, item.isEmpty() ? ItemStack.EMPTY : item.copyWithCount(1), fluid));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll() > 0 && (overGrid(mouseX, mouseY) || overBar(mouseX, mouseY) || mouseY < topPos + GRID_Y + rows * 18)) {
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
    static String shortCount(long count) {
        if (count < 1000) return Long.toString(count);
        if (count < 10_000) return String.format(Locale.ROOT, "%.1fk", count / 1000f);
        if (count < 1_000_000) return (count / 1000) + "k";
        return String.format(Locale.ROOT, "%.1fM", count / 1_000_000f);
    }
}
