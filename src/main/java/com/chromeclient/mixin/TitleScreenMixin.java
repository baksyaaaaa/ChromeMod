package com.chromeclient.mixin;

import com.chromeclient.ChromeTitleScreen;
import com.chromeclient.Extras;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** swaps the normal main menu for Chrome Client's; if this does not apply, the normal menu simply stays */
@Mixin(Minecraft.class)
public abstract class TitleScreenMixin {
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true, require = 0)
    private Screen chromeclient$title(Screen screen) {
        if (Extras.customTitle && screen != null && screen.getClass() == TitleScreen.class) return new ChromeTitleScreen();
        return screen;
    }
}
