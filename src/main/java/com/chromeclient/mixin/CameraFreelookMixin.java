package com.chromeclient.mixin;

import com.chromeclient.Freelook;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Freelook: while it is active the mouse only changes Freelook.yaw / Freelook.pitch. Just for the moment the camera is
 * set up, the offset is added to your character's rotation (both the current and the previous values, so the
 * per-frame smoothing stays consistent) and taken away again straight after. The camera therefore turns - in first
 * and third person, up/down as well as left/right - while your character keeps facing the same way.
 */
@Mixin(Camera.class)
public abstract class CameraFreelookMixin {
    @Unique private boolean chromeclient$swapped = false;
    @Unique private float chromeclient$y, chromeclient$x, chromeclient$yo, chromeclient$xo;

    @Inject(method = "setup", at = @At("HEAD"), require = 0)
    private void chromeclient$before(CallbackInfo ci) {
        Freelook.hooked = true;
        chromeclient$swapped = false;
        if (!Freelook.active) return;
        Minecraft mc = Minecraft.getInstance();
        Entity e = mc.getCameraEntity();
        if (e == null || e != mc.player) return;
        chromeclient$y = e.getYRot();
        chromeclient$x = e.getXRot();
        chromeclient$yo = e.yRotO;
        chromeclient$xo = e.xRotO;
        float newX = Mth.clamp(chromeclient$x + Freelook.pitch, -90f, 90f);
        e.setYRot(chromeclient$y + Freelook.yaw);
        e.yRotO = chromeclient$yo + Freelook.yaw;
        e.setXRot(newX);
        e.xRotO = Mth.clamp(chromeclient$xo + Freelook.pitch, -90f, 90f);
        chromeclient$swapped = true;
    }

    @Inject(method = "setup", at = @At("RETURN"), require = 0)
    private void chromeclient$after(CallbackInfo ci) {
        if (!chromeclient$swapped) return;
        chromeclient$swapped = false;
        Minecraft mc = Minecraft.getInstance();
        Entity e = mc.getCameraEntity();
        if (e == null || e != mc.player) return;
        e.setYRot(chromeclient$y);
        e.setXRot(chromeclient$x);
        e.yRotO = chromeclient$yo;
        e.xRotO = chromeclient$xo;
    }
}
