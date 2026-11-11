package com.chromeclient.mixin;

import com.chromeclient.Visuals;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hand position: moves, shrinks or tilts the held item / hand in first person. */
@Mixin(ItemInHandRenderer.class)
public abstract class HandTransformMixin {
    @Inject(method = "applyItemArmTransform", at = @At("TAIL"), require = 0)
    private void chromeclient$hand(PoseStack pose, HumanoidArm arm, float equip, CallbackInfo ci) {
        if (!Visuals.handOn) return;
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null && mc.player.isUsingItem()) return;   // eating / drinking / bow: the game's own animation, left alone
        float side = arm == HumanoidArm.RIGHT ? 1f : -1f;
        pose.translate(Visuals.handX * 0.01f * side, Visuals.handY * 0.01f, Visuals.handZ * 0.01f);
        pose.scale(Visuals.handScale, Visuals.handScale, Visuals.handScale);
        if (Visuals.handTilt != 0f) pose.mulPose(Axis.ZP.rotationDegrees(Visuals.handTilt * side));
    }
}
