package io.github.keellam4.oritechextensions.client;

import io.github.keellam4.oritechextensions.storage.CargoVaultBlock;
import io.github.keellam4.oritechextensions.storage.CargoVaultBlockEntity;
import io.github.keellam4.oritechextensions.storage.FluidVaultBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Look at a vault's front and a small readout appears under the crosshair: the item in the window you're aiming at,
 * how many there are out of how many fit, and whether the vault is locked.
 */
public final class VaultHud {

    private static final int NAME_COLOUR = 0xFFFFFFFF;
    private static final int COUNT_COLOUR = 0xFF9CFFF5;
    private static final int LOCKED_COLOUR = 0xFFFFC27A;
    private static final int BACKGROUND = 0x90101418;

    private VaultHud() {
    }

    /** The slot under the crosshair on the vault at {@code pos}, or -1. Also used to decide which slot a left-click takes from. */
    public static int slotUnderCrosshair(BlockPos pos) {
        var minecraft = Minecraft.getInstance();
        if (!(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(pos)) return -1;
        var level = minecraft.level;
        if (level == null) return -1;
        var state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CargoVaultBlock block)) return -1;
        return block.layout().slotAt(state.getValue(BlockStateProperties.HORIZONTAL_FACING), pos, hit.getLocation(), hit.getDirection());
    }

    public static void render(GuiGraphics graphics) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.screen != null || minecraft.level == null) return;
        if (!(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        var pos = hit.getBlockPos();
        if (!(minecraft.level.getBlockEntity(pos) instanceof CargoVaultBlockEntity vault)) return;
        int slot = slotUnderCrosshair(pos);
        if (slot < 0 || slot >= vault.slots()) return;

        ItemStack item;
        Component name;
        String amount;
        if (vault instanceof FluidVaultBlockEntity tank) {
            var fluid = tank.fluid();
            boolean empty = fluid == net.minecraft.world.level.material.Fluids.EMPTY;
            item = empty ? ItemStack.EMPTY : new ItemStack(fluid.getBucket());
            name = empty ? Component.translatable("hud.oritech_extensions.vault.empty") : fluid.defaultFluidState().createLegacyBlock().getBlock().getName();
            amount = String.format("%,d / %,d mB", tank.amount(), tank.fluidCapacity());
        } else {
            item = vault.item(slot);
            name = item.isEmpty() ? Component.translatable("hud.oritech_extensions.vault.empty") : item.getHoverName();
            amount = item.isEmpty()
                    ? String.format("0 / %,d", vault.capacity(slot))
                    : String.format("%,d / %,d", vault.count(slot), vault.capacity(slot));
        }
        var font = minecraft.font;
        Component lock = vault.isLocked() ? Component.translatable("hud.oritech_extensions.vault.locked") : null;

        int textWidth = Math.max(font.width(name), font.width(amount) + (lock == null ? 0 : font.width(lock) + 6));
        int iconSize = item.isEmpty() ? 0 : 18;
        int width = iconSize + textWidth + 8;
        int height = 24;
        int x = graphics.guiWidth() / 2 - width / 2;
        int y = graphics.guiHeight() / 2 + 14;

        graphics.fill(x, y, x + width, y + height, BACKGROUND);
        if (!item.isEmpty()) graphics.renderItem(item, x + 3, y + 4);
        int textX = x + 4 + iconSize;
        graphics.drawString(font, name, textX, y + 3, NAME_COLOUR, true);
        graphics.drawString(font, amount, textX, y + 13, COUNT_COLOUR, true);
        if (lock != null) graphics.drawString(font, lock, textX + font.width(amount) + 6, y + 13, LOCKED_COLOUR, true);
    }
}
