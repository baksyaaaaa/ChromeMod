package com.chromeclient.mixin;

import com.chromeclient.MenuStyle;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** every normal button in every menu gets the Chrome look; if this does not apply, the vanilla buttons stay */
@Mixin(AbstractButton.class)
public abstract class ButtonStyleMixin {
    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true, require = 0)
    private void chromeclient$style(GuiGraphics g, int mx, int my, float d, CallbackInfo ci) {
        AbstractButton self = (AbstractButton) (Object) this;
        if (MenuStyle.applies(self)) {
            MenuStyle.draw(self, g);
            ci.cancel();
        }
    }
}
