package com.chromeclient.mixin;

import com.chromeclient.Visuals;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Low fire: the fire on your screen when you burn is drawn lower. The game positions it with one translate (-0.3 up/down);
 * we push that further down by how much you asked. Both number types are covered, whichever one the game uses.
 */
@Mixin(ScreenEffectRenderer.class)
public abstract class LowFireMixin {
    @ModifyArg(method = "renderFire", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"), index = 1, require = 0)
    private static float chromeclient$lowF(float y) {
        return Visuals.fireOn ? y - Visuals.fireDrop() : y;
    }

    @ModifyArg(method = "renderFire", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V"), index = 1, require = 0)
    private static double chromeclient$lowD(double y) {
        return Visuals.fireOn ? y - Visuals.fireDrop() : y;
    }
}
