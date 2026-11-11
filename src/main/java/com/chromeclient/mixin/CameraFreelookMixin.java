package com.chromeclient.mixin;

import com.chromeclient.Freelook;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Freelook: adds the camera offset on top of where your character looks.
 * Two independent hooks do the same job so one of them is enough: the call inside Camera.setup (setRotation(yaw, pitch))
 * and the method itself. When the first one already added the offset, the second one skips it for that call.
 * Either one that runs also tells Freelook the camera is hooked (without that the mouse is never taken away from you).
 */
@Mixin(Camera.class)
public abstract class CameraFreelookMixin {
    private static boolean chromeclient$siteDone = false;

    @ModifyArgs(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V"))
    private void chromeclient$site(Args args) {
        Freelook.hooked = true;
        if (!Freelook.active) return;
        args.set(0, args.<Float>get(0) + Freelook.yaw);
        args.set(1, Mth.clamp(args.<Float>get(1) + Freelook.pitch, -90f, 90f));
        chromeclient$siteDone = true;
    }

    @Inject(method = "setRotation(FF)V", at = @At("RETURN"))
    private void chromeclient$leave(float yRot, float xRot, CallbackInfo ci) {
        chromeclient$siteDone = false;
    }

    @ModifyVariable(method = "setRotation(FF)V", at = @At("HEAD"), argsOnly = true, index = 1)
    private float chromeclient$yaw(float yRot) {
        Freelook.hooked = true;
        return Freelook.active && !chromeclient$siteDone ? yRot + Freelook.yaw : yRot;
    }

    @ModifyVariable(method = "setRotation(FF)V", at = @At("HEAD"), argsOnly = true, index = 2)
    private float chromeclient$pitch(float xRot) {
        return Freelook.active && !chromeclient$siteDone ? Mth.clamp(xRot + Freelook.pitch, -90f, 90f) : xRot;
    }
}
