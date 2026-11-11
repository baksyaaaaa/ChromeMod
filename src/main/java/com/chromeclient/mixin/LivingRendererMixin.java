package com.chromeclient.mixin;

import com.chromeclient.CosmeticLayer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the cosmetics layer to every living-entity renderer; the layer itself only draws on your own player. */
@SuppressWarnings({"rawtypes", "unchecked"})
@Mixin(targets = "net.minecraft.client.renderer.entity.LivingEntityRenderer")
public abstract class LivingRendererMixin {
    private static boolean done = false;

    @Shadow
    protected abstract boolean addLayer(RenderLayer layer);

    @Inject(method = "<init>", at = @At("TAIL"))
    private void chromeclient$cosmetics(CallbackInfo ci) {
        try {
            addLayer(new CosmeticLayer((RenderLayerParent) (Object) this));
            if (!done) { done = true; org.slf4j.LoggerFactory.getLogger("ChromeCosmetics").info("layer added to {}", this.getClass().getName()); }
        } catch (Throwable t) {
            org.slf4j.LoggerFactory.getLogger("ChromeCosmetics").error("could not add layer", t);
        }
    }
}
