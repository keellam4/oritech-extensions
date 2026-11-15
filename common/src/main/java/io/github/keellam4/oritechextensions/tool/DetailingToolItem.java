package io.github.keellam4.oritechextensions.tool;

import io.github.keellam4.oritechextensions.ModContent;
import io.github.keellam4.oritechextensions.paint.Paint;
import io.github.keellam4.oritechextensions.paint.PaintTargets;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.Level;
import rearth.oritech.item.other.ColorCartridgeItem;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The Detailing Tool: a brush that holds Color Cartridges and paints everything this mod and Oritech can paint.
 * <p>
 * Loading works like a bundle: right-click cartridges onto the tool in the inventory, or right-click the tool onto
 * them. Right-clicking the tool with an empty cursor takes the selected colour's cartridges back out. Painting
 * itself happens in {@link DetailingTool}.
 */
public class DetailingToolItem extends Item {

    private static final int BAR_COLOUR = 0x58C4B1;
    private static final Map<Paint, Item> CARTRIDGES = new EnumMap<>(Paint.class);

    public DetailingToolItem(Properties properties) {
        super(properties);
    }

    public static ToolContents contents(ItemStack stack) {
        return stack.getOrDefault(ModContent.TOOL_CONTENTS.get(), ToolContents.EMPTY);
    }

    /** Stores {@code contents}; also picks the item model that shows the selected colour (see tools/paint/detailing_tool.py). */
    public static void setContents(ItemStack stack, ToolContents contents) {
        stack.set(ModContent.TOOL_CONTENTS.get(), contents);
        if (contents.selectedCount() > 0) {
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(contents.selected().ordinal() + 1));
        } else {
            stack.remove(DataComponents.CUSTOM_MODEL_DATA);
        }
    }

    /** A stack of the cartridge item that paints {@code paint}. */
    public static ItemStack cartridge(Paint paint, int count) {
        if (paint == Paint.RAINBOW) return new ItemStack(ModContent.RAINBOW_CARTRIDGE.get(), count);
        synchronized (CARTRIDGES) {
            if (CARTRIDGES.isEmpty()) {
                for (var item : BuiltInRegistries.ITEM) {
                    if (item instanceof ColorCartridgeItem cartridge) CARTRIDGES.putIfAbsent(Paint.of(cartridge.variant), item);
                }
            }
        }
        var item = CARTRIDGES.get(paint);
        return item == null ? ItemStack.EMPTY : new ItemStack(item, count);
    }

    // ---------------------------------------------------------------- loading and unloading

    /** Tool on the cursor, right-clicked onto a slot: load the cartridges in it, or unload into an empty slot. */
    @Override
    public boolean overrideStackedOnOther(ItemStack tool, Slot slot, ClickAction action, Player player) {
        if (action != ClickAction.SECONDARY) return false;
        var other = slot.getItem();
        if (other.isEmpty()) {
            var out = unload(tool, player);
            if (out.isEmpty()) return false;
            var rest = slot.safeInsert(out);
            if (!rest.isEmpty()) load(tool, rest, player); // the slot didn't take all of it
            return true;
        }
        if (PaintTargets.cartridgePaint(other) == null || !slot.allowModification(player)) return false;
        int moved = load(tool, other, player);
        if (moved > 0) {
            other.shrink(moved);
            slot.setChanged();
        }
        return true;
    }

    /** Cartridges on the cursor, right-clicked onto the tool: load them. Empty cursor: take the selected colour out. */
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack tool, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess access) {
        if (action != ClickAction.SECONDARY || !slot.allowModification(player)) return false;
        if (other.isEmpty()) {
            var out = unload(tool, player);
            if (out.isEmpty()) return false;
            access.set(out);
            return true;
        }
        if (PaintTargets.cartridgePaint(other) == null) return false;
        int moved = load(tool, other, player);
        if (moved > 0) other.shrink(moved);
        return true;
    }

    /** Adds as many of {@code cartridges} as fit; the caller removes them from the stack. Returns how many fit. */
    public static int load(ItemStack tool, ItemStack cartridges, Player player) {
        var paint = PaintTargets.cartridgePaint(cartridges);
        if (paint == null) return 0;
        var contents = contents(tool);
        int moved = Math.min(cartridges.getCount(), ToolContents.CAPACITY - contents.count(paint));
        if (moved <= 0) return 0;
        contents = contents.withCount(paint, contents.count(paint) + moved);
        if (contents.selectedCount() == 0) contents = contents.withSelected(paint);
        setContents(tool, contents);
        player.playSound(SoundEvents.BUNDLE_INSERT, 0.8f, 0.8f + player.level().getRandom().nextFloat() * 0.4f);
        return moved;
    }

    /** Takes all charges of the selected colour out as cartridges. */
    public static ItemStack unload(ItemStack tool, Player player) {
        var contents = contents(tool);
        int count = contents.selectedCount();
        if (count <= 0) return ItemStack.EMPTY;
        var out = cartridge(contents.selected(), count);
        if (out.isEmpty()) return ItemStack.EMPTY;
        setContents(tool, contents.withCount(contents.selected(), 0));
        player.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8f, 0.8f + player.level().getRandom().nextFloat() * 0.4f);
        return out;
    }

    // ---------------------------------------------------------------- use

    /** Sneak + right-click the air switches mode. Right-click the air in Area mode forgets the first corner. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        var contents = contents(stack);
        if (player.isSecondaryUseActive()) {
            if (!level.isClientSide()) {
                contents = contents.withMode(contents.mode().next());
                setContents(stack, contents);
                DetailingTool.showStatus(player, contents);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        if (contents.mode() == DetailingMode.AREA && contents.corner().isPresent()) {
            if (!level.isClientSide()) {
                setContents(stack, contents.withCorner(Optional.empty()));
                player.displayClientMessage(Component.translatable("message.oritech_extensions.detailing_tool.corner_cleared"), true);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        return InteractionResultHolder.pass(stack);
    }

    // ---------------------------------------------------------------- looks

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return contents(stack).selectedCount() > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13f * contents(stack).selectedCount() / ToolContents.CAPACITY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BAR_COLOUR;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        var contents = contents(stack);
        lines.add(Component.translatable("tooltip.oritech_extensions.detailing_tool.mode", contents.mode().displayName())
                .withStyle(ChatFormatting.GRAY));
        if (contents.isEmpty()) {
            lines.add(Component.translatable("tooltip.oritech_extensions.detailing_tool.empty").withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable("tooltip.oritech_extensions.detailing_tool.selected",
                    DetailingTool.paintName(contents.selected()), contents.selectedCount(), ToolContents.CAPACITY).withStyle(ChatFormatting.GRAY));
            for (var paint : contents.loaded()) {
                if (paint == contents.selected()) continue;
                lines.add(Component.translatable("tooltip.oritech_extensions.detailing_tool.loaded",
                        DetailingTool.paintName(paint), contents.count(paint)).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        for (int i = 1; i <= 4; i++) {
            lines.add(Component.translatable("tooltip.oritech_extensions.detailing_tool.help." + i).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
    }
}
