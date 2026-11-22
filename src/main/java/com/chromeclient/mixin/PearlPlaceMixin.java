package com.chromeclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * No block delay after a pearl: the game waits 4 ticks after every right click before the next one is allowed, so a block
 * placed right after a pearl throw is held back. After a pearl throw we clear that wait.
 */
@Mixin(Minecraft.class)
public abstract class PearlPlaceMixin {
    @Shadow private int rightClickDelay;
    @Unique private boolean chromeclient$pearl;

    @Inject(method = "startUseItem", at = @At("HEAD"), require = 0)
    private void chromeclient$before(CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;
        chromeclient$pearl = mc.player != null
                && (mc.player.getMainHandItem().is(Items.ENDER_PEARL) || mc.player.getOffhandItem().is(Items.ENDER_PEARL));
    }

    @Inject(method = "startUseItem", at = @At("RETURN"), require = 0)
    private void chromeclient$after(CallbackInfo ci) {
        if (chromeclient$pearl) rightClickDelay = 0;
        chromeclient$pearl = false;
    }
}
