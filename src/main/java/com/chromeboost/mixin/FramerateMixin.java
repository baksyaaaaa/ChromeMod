package com.chromeboost.mixin;

import com.chromeboost.Settings;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While the window is behind other windows there is no reason to render at full speed: the frame limit drops (default 15),
 * which frees the CPU/GPU for what you are actually doing and keeps the laptop cool. It goes back the moment you click in.
 */
@Mixin(Minecraft.class)
public abstract class FramerateMixin {
    @Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true, require = 0)
    private void chromeboost$background(CallbackInfoReturnable<Integer> cir) {
        Settings s = Settings.get();
        if (!s.backgroundLimit) return;
        Minecraft mc = (Minecraft) (Object) this;
        if (!mc.isWindowActive() && cir.getReturnValue() > s.backgroundFps) cir.setReturnValue(s.backgroundFps);
    }
}
