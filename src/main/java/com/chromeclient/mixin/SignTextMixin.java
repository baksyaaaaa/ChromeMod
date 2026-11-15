package com.chromeclient.mixin;

import com.chromeclient.Extras;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.AbstractSignRenderer;
import net.minecraft.client.renderer.blockentity.state.SignRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Laying out and drawing the text of a sign is one of the more expensive block-entity jobs, and past about 24 blocks the
 * letters are smaller than a pixel. So the text of far signs is skipped (the sign board itself is still drawn).
 * If this does not apply to the game version, the signs just draw as normal.
 */
@Mixin(AbstractSignRenderer.class)
public abstract class SignTextMixin {
    @Inject(method = "submitSignText", at = @At("HEAD"), cancellable = true, require = 0)
    private void chromeclient$farSigns(SignRenderState state, PoseStack pose, SubmitNodeCollector queue, boolean front, CallbackInfo ci) {
        if (!Extras.farSigns) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        double dx = state.blockPos.getX() + 0.5 - mc.player.getX(), dy = state.blockPos.getY() + 0.5 - mc.player.getY(), dz = state.blockPos.getZ() + 0.5 - mc.player.getZ();
        if (dx * dx + dy * dy + dz * dz > 24.0 * 24.0) ci.cancel();
    }
}
