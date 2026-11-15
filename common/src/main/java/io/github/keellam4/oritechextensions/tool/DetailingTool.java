package io.github.keellam4.oritechextensions.tool;

import io.github.keellam4.oritechextensions.Config;
import io.github.keellam4.oritechextensions.paint.Paint;
import io.github.keellam4.oritechextensions.paint.PaintTarget;
import io.github.keellam4.oritechextensions.paint.PaintTargets;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;
import rearth.oritech.block.entity.MachineCoreEntity;
import rearth.oritech.util.ColorableMachine;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * What the Detailing Tool does in the world. Works on every {@link PaintTarget} plus Oritech's own machines.
 * <ul>
 *     <li>Right-click: paint, by mode (group, single block, or area between two clicked corners).</li>
 *     <li>Sneak + right-click a block: pick its colour (eyedropper).</li>
 *     <li>Sneak + scroll: switch between the loaded colours ({@link DetailingToolPayload}).</li>
 *     <li>Sneak + right-click the air: switch mode ({@link DetailingToolItem#use}).</li>
 * </ul>
 * Cost is the same as Color Cartridges, paid from the tool's charges. Creative players paint for free.
 */
public final class DetailingTool {

    private DetailingTool() {
    }

    /**
     * Loader hook: a player right-clicked a block. Returns the result to cancel the click with, or null to let the
     * click behave normally. Runs before the block's own click handling, so machines don't open their screens.
     */
    @Nullable
    public static InteractionResult onRightClickBlock(Level level, BlockPos pos, Player player, InteractionHand hand, ItemStack stack) {
        if (!(stack.getItem() instanceof DetailingToolItem)) return null;
        var contents = DetailingToolItem.contents(stack);
        boolean pick = player.isSecondaryUseActive();
        boolean paintable = PaintTargets.find(level, pos) != null || isMachine(level, pos);
        if (!paintable && (pick || contents.mode() != DetailingMode.AREA)) return null;

        if (level instanceof ServerLevel serverLevel) {
            if (pick) {
                pickColour(serverLevel, pos, player, stack);
            } else {
                paint(serverLevel, pos, player, stack);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    /** Payload handler: sneak + scroll. */
    public static void onScroll(ServerPlayer player, int direction) {
        var stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof DetailingToolItem) || direction == 0) return;
        var contents = DetailingToolItem.contents(stack).cycle(direction);
        DetailingToolItem.setContents(stack, contents);
        showStatus(player, contents);
    }

    /** A right-click with the tool, by mode. */
    public static void paint(ServerLevel level, BlockPos pos, Player player, ItemStack stack) {
        if (!mayPaint(level, pos, player)) return;
        var contents = DetailingToolItem.contents(stack);

        if (contents.mode() == DetailingMode.AREA) {
            var corner = contents.corner();
            if (corner.isEmpty()) {
                DetailingToolItem.setContents(stack, contents.withCorner(Optional.of(pos)));
                player.displayClientMessage(Component.translatable("message.oritech_extensions.detailing_tool.corner",
                        pos.getX(), pos.getY(), pos.getZ()), true);
                return;
            }
            DetailingToolItem.setContents(stack, contents.withCorner(Optional.empty()));
            paintArea(level, corner.get(), pos, player, stack);
            return;
        }

        boolean free = player.getAbilities().instabuild;
        var paint = contents.selected();
        int available = free ? Integer.MAX_VALUE : contents.selectedCount();
        if (available <= 0) {
            player.displayClientMessage(Component.translatable("message.oritech_extensions.detailing_tool.no_paint", paintName(paint)), true);
            return;
        }

        var painted = paintOne(level, pos, paint, available, contents.mode() == DetailingMode.SINGLE, new HashSet<>(), null, player);
        if (painted == null) return;
        if (!free) spend(stack, paint, painted.cost());
        PaintTargets.paintEffects(level, pos);
    }

    /** Paints every paintable block in the box between {@code a} and {@code b}, one charge per group (or per block, by the config). */
    public static void paintArea(ServerLevel level, BlockPos a, BlockPos b, Player player, ItemStack stack) {
        var box = BoundingBox.fromCorners(a, b);
        int limit = Config.detailingToolAreaSize();
        if (box.getXSpan() > limit || box.getYSpan() > limit || box.getZSpan() > limit) {
            player.displayClientMessage(Component.translatable("message.oritech_extensions.detailing_tool.too_big", limit), true);
            return;
        }

        var contents = DetailingToolItem.contents(stack);
        var paint = contents.selected();
        boolean free = player.getAbilities().instabuild;
        int available = free ? Integer.MAX_VALUE : contents.selectedCount();
        if (available <= 0) {
            player.displayClientMessage(Component.translatable("message.oritech_extensions.detailing_tool.no_paint", paintName(paint)), true);
            return;
        }

        var visited = new HashSet<BlockPos>();
        int spent = 0;
        int changed = 0;
        for (var mutable : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            if (spent >= available) break;
            var pos = mutable.immutable();
            if (visited.contains(pos) || !mayPaint(level, pos, player)) continue;
            var painted = paintOne(level, pos, paint, available - spent, false, visited, box, null);
            if (painted == null) continue;
            spent += painted.cost();
            changed += painted.changed();
        }

        if (changed == 0) {
            player.displayClientMessage(Component.translatable("message.oritech_extensions.detailing_tool.area_nothing"), true);
            return;
        }
        if (!free) spend(stack, paint, spent);
        PaintTargets.paintEffects(level, b);
        player.displayClientMessage(Component.translatable("message.oritech_extensions.detailing_tool.area_done",
                changed, free ? 0 : spent), true);
    }

    /**
     * Paints the block at {@code pos}: its whole group, only the block itself ({@code single}), or the part of its
     * group inside {@code box}. Positions handled are added to {@code visited}.
     *
     * @param player told why nothing happened, when not null.
     */
    @Nullable
    private static PaintTargets.Painted paintOne(ServerLevel level, BlockPos pos, Paint paint, int available, boolean single,
                                                 HashSet<BlockPos> visited, @Nullable BoundingBox box, @Nullable Player player) {
        var target = PaintTargets.find(level, pos);
        if (target != null) {
            var group = target.group(level, pos);
            visited.add(pos);
            visited.addAll(group);
            if (group.contains(pos)) {
                // A group that doesn't contain the clicked block (a hangar door extension paints the door) stays whole.
                if (single) group = List.of(pos);
                else if (box != null) group = group.stream().filter(box::isInside).toList();
            }
            return PaintTargets.paintGroup(level, target, group, paint, available);
        }

        var machine = machine(level, pos);
        if (machine == null || !visited.add(machine.getBlockPos())) return null;
        var colorable = (ColorableMachine) machine;
        var variant = paint.variant();
        if (variant == null) {
            if (player != null) player.displayClientMessage(Component.translatable("message.oritech_extensions.detailing_tool.no_rainbow_machines"), true);
            return null;
        }
        if (colorable.getCurrentColor() == variant) return null;
        colorable.assignColor(variant);
        return new PaintTargets.Painted(1, 1);
    }

    /** Sneak + right-click: select the colour of the clicked block. */
    public static void pickColour(ServerLevel level, BlockPos pos, Player player, ItemStack stack) {
        Paint colour;
        var target = PaintTargets.find(level, pos);
        if (target != null) {
            colour = target.get(level, pos);
        } else {
            var machine = machine(level, pos);
            if (machine == null) return;
            colour = Paint.of(((ColorableMachine) machine).getCurrentColor());
        }
        var contents = DetailingToolItem.contents(stack).withSelected(colour);
        DetailingToolItem.setContents(stack, contents);
        if (contents.selectedCount() > 0) {
            showStatus(player, contents);
        } else {
            player.displayClientMessage(Component.translatable("message.oritech_extensions.detailing_tool.picked_empty", paintName(colour)), true);
        }
    }

    // ---------------------------------------------------------------- helpers

    private static void spend(ItemStack stack, Paint paint, int cost) {
        var contents = DetailingToolItem.contents(stack);
        DetailingToolItem.setContents(stack, contents.withCount(paint, contents.count(paint) - cost));
    }

    /** Spawn protection, adventure mode and claim mods that hook {@code mayInteract}. */
    private static boolean mayPaint(ServerLevel level, BlockPos pos, Player player) {
        return player.mayBuild() && level.mayInteract(player, pos) && level.getWorldBorder().isWithinBounds(pos);
    }

    /** True for Oritech machines and their multiblock parts. Works on the client, where cores may not know their controller yet. */
    public static boolean isMachine(Level level, BlockPos pos) {
        var entity = level.getBlockEntity(pos);
        return entity instanceof MachineCoreEntity || (entity instanceof ColorableMachine machine && machine.supportRecoloring());
    }

    /** The paintable Oritech machine at {@code pos}, following multiblock cores to their controller. */
    @Nullable
    public static BlockEntity machine(Level level, BlockPos pos) {
        var entity = level.getBlockEntity(pos);
        if (entity instanceof MachineCoreEntity core && core.getCachedController() instanceof BlockEntity controller) {
            entity = controller;
        }
        return entity instanceof ColorableMachine machine && machine.supportRecoloring() ? entity : null;
    }

    public static Component paintName(Paint paint) {
        return Component.translatable("paint.oritech_extensions." + paint.name().toLowerCase(Locale.ROOT));
    }

    /** Action bar: mode, colour and charges. */
    public static void showStatus(Player player, ToolContents contents) {
        player.displayClientMessage(Component.translatable("message.oritech_extensions.detailing_tool.status",
                contents.mode().displayName(), paintName(contents.selected()), contents.selectedCount(), ToolContents.CAPACITY), true);
    }
}
