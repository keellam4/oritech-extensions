package io.github.keellam4.oritechextensions.wireless;

import io.github.keellam4.oritechextensions.Config;
import io.github.keellam4.oritechextensions.ModContent;
import io.github.keellam4.oritechextensions.storage.CargoControllerBlockEntity;
import io.github.keellam4.oritechextensions.storage.TerminalMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Cargo Link: a handheld terminal for a Cargo Wall. Sneak + right-click a Cargo Controller or Crafting
 * Terminal to link it, then right-click anywhere in range to open the wall's crafting terminal. Each transfer
 * uses a little of its battery, which an Induction Pylon (or any charger) refills.
 */
public class CargoLinkItem extends Item {

    public static final int CAPACITY = 200_000;
    public static final int MAX_INPUT = 10_000;

    public CargoLinkItem(Properties properties) {
        super(properties);
    }

    // ---------------------------------------------------------------- battery and binding

    public static int energy(ItemStack stack) {
        return stack.getOrDefault(ModContent.LINK_ENERGY.get(), 0);
    }

    public static void setEnergy(ItemStack stack, int energy) {
        stack.set(ModContent.LINK_ENERGY.get(), Math.max(0, Math.min(CAPACITY, energy)));
    }

    /** Uses the battery for moving {@code items} items. False (and nothing used) if it's too low. */
    public static boolean spend(ItemStack stack, int items) {
        int cost = items * Config.linkEnergyPerItem();
        if (energy(stack) < cost) return false;
        setEnergy(stack, energy(stack) - cost);
        return true;
    }

    @Nullable
    public static GlobalPos target(ItemStack stack) {
        return stack.get(ModContent.LINK_TARGET.get());
    }

    public static void bind(ItemStack stack, GlobalPos target) {
        stack.set(ModContent.LINK_TARGET.get(), target);
    }

    /** The bound controller, if its chunk is loaded. */
    @Nullable
    public static CargoControllerBlockEntity controller(Level level, ItemStack stack) {
        var target = target(stack);
        if (target == null || level.getServer() == null) return null;
        ServerLevel targetLevel = level.getServer().getLevel(target.dimension());
        if (targetLevel == null || !targetLevel.isLoaded(target.pos())) return null;
        return targetLevel.getBlockEntity(target.pos()) instanceof CargoControllerBlockEntity controller ? controller : null;
    }

    // ---------------------------------------------------------------- using it

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var player = context.getPlayer();
        if (player == null || !player.isShiftKeyDown()) return InteractionResult.PASS;
        if (!(context.getLevel().getBlockEntity(context.getClickedPos()) instanceof CargoControllerBlockEntity)) return InteractionResult.PASS;
        if (!context.getLevel().isClientSide()) {
            bind(context.getItemInHand(), GlobalPos.of(context.getLevel().dimension(), context.getClickedPos()));
            player.displayClientMessage(Component.translatable("message.oritech_extensions.link.bound").withStyle(ChatFormatting.AQUA), true);
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (level.isClientSide()) return InteractionResultHolder.success(stack);
        var target = target(stack);
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.oritech_extensions.link.unbound").withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        if (target.dimension() != level.dimension()) {
            player.displayClientMessage(Component.translatable("message.oritech_extensions.link.dimension").withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        var controller = controller(level, stack);
        if (controller == null) {
            player.displayClientMessage(Component.translatable("message.oritech_extensions.link.offline").withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        int range = LinkRange.range(level, controller);
        double distance = player.position().distanceTo(target.pos().getCenter());
        if (distance > range) {
            player.displayClientMessage(Component.translatable("message.oritech_extensions.link.out_of_range",
                    (int) distance, range).withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        var access = new Access(hand, target, range);
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new TerminalMenu(id, inventory, controller, true).remote(access),
                stack.getHoverName()));
        player.displayClientMessage(signal(distance, range, energy(stack)), true);
        return InteractionResultHolder.consume(stack);
    }

    /** "▮▮▮▯ 45 / 64 blocks · 120,000 RF" */
    public static Component signal(double distance, int range, int energy) {
        int bars = LinkRange.bars(distance, range);
        var text = Component.literal("\\u25AE".repeat(bars)).withStyle(bars <= 1 ? ChatFormatting.RED : ChatFormatting.AQUA)
                .append(Component.literal("\\u25AF".repeat(4 - bars)).withStyle(ChatFormatting.DARK_GRAY));
        return text.append(Component.translatable("message.oritech_extensions.link.signal", (int) distance, range, String.format("%,d", energy))
                .withStyle(ChatFormatting.GRAY));
    }

    /** Keeps the remote terminal open while the Link stays in hand, linked and in range, and pays for transfers. */
    public record Access(InteractionHand hand, GlobalPos target, int range) implements TerminalMenu.RemoteAccess {

        @Override
        public boolean stillValid(Player player) {
            var stack = player.getItemInHand(hand);
            return stack.getItem() instanceof CargoLinkItem && target.equals(target(stack))
                    && player.level().dimension() == target.dimension()
                    && player.position().distanceTo(target.pos().getCenter()) <= range;
        }

        @Override
        public boolean pay(Player player, int items) {
            if (player.getAbilities().instabuild) return true;
            if (spend(player.getItemInHand(hand), items)) return true;
            player.displayClientMessage(Component.translatable("message.oritech_extensions.link.battery").withStyle(ChatFormatting.RED), true);
            return false;
        }
    }

    // ---------------------------------------------------------------- looks

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13f * energy(stack) / CAPACITY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x3EC9D5;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        var target = target(stack);
        lines.add(target == null
                ? Component.translatable("tooltip.oritech_extensions.link.unbound").withStyle(ChatFormatting.GRAY)
                : Component.translatable("tooltip.oritech_extensions.link.bound", target.pos().getX(), target.pos().getY(), target.pos().getZ(),
                        target.dimension().location().toString()).withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable("tooltip.oritech_extensions.link.energy", String.format("%,d", energy(stack)), String.format("%,d", CAPACITY))
                .withStyle(ChatFormatting.GRAY));
    }
}
