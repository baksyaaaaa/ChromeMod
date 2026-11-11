package com.chromeclient.mixin;

import com.chromeclient.Freelook;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Freelook: while it is active the mouse turns the camera offset instead of your character. */
@Mixin(Entity.class)
public abstract class EntityTurnMixin {
    @Inject(method = "turn(DD)V", at = @At("HEAD"), cancellable = true)
    private void chromeclient$freelook(double yRot, double xRot, CallbackInfo ci) {
        if (Freelook.active && Freelook.hooked && (Object) this instanceof LocalPlayer) {
            Freelook.turn(yRot, xRot);
            ci.cancel();
        }
    }
}
