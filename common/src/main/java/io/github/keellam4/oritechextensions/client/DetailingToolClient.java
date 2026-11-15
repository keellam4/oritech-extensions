package io.github.keellam4.oritechextensions.client;

import io.github.keellam4.oritechextensions.platform.Platform;
import io.github.keellam4.oritechextensions.tool.DetailingToolItem;
import io.github.keellam4.oritechextensions.tool.DetailingToolPayload;
import net.minecraft.client.Minecraft;

/** Client side of the Detailing Tool: sneak + scroll switches colour instead of the hotbar slot. */
public final class DetailingToolClient {

    /** Trackpads send many small scroll steps; switch colour once per whole step. */
    private static double scrolled;

    private DetailingToolClient() {
    }

    /** Called from MouseHandlerMixin. Returns true when the scroll was used (the hotbar must not move). */
    public static boolean onScroll(double amount) {
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null || minecraft.screen != null || !player.isShiftKeyDown()) return false;
        if (!(player.getMainHandItem().getItem() instanceof DetailingToolItem)) return false;

        if (Math.signum(amount) != Math.signum(scrolled)) scrolled = 0;
        scrolled += amount;
        int steps = (int) scrolled;
        if (steps == 0) return true;
        scrolled -= steps;
        // Scrolling up moves the hotbar left, so it picks the previous colour here too.
        Platform.INSTANCE.sendToServer(new DetailingToolPayload(-Integer.signum(steps)));
        return true;
    }
}
