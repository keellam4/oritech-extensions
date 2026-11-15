package io.github.keellam4.oritechextensions.mixin;

import io.github.keellam4.oritechextensions.client.DetailingToolClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sneak + scroll with a Detailing Tool switches its colour (both loaders, so no loader scroll event is needed). */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void oritechExtensions$switchDetailingToolColour(long window, double xOffset, double yOffset, CallbackInfo ci) {
        if (window != minecraft.getWindow().getWindow()) return;
        double amount = minecraft.options.discreteMouseScroll().get() ? Math.signum(yOffset) : yOffset;
        if (DetailingToolClient.onScroll(amount * minecraft.options.mouseWheelSensitivity().get())) ci.cancel();
    }
}
