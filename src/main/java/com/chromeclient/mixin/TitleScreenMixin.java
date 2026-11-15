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
        if (!Extras.customTitle) return screen;
        Minecraft self = (Minecraft) (Object) this;
        if (screen != null && screen.getClass() == TitleScreen.class) return new ChromeTitleScreen();
        // a menu closes with "no screen" while you are not in a world: the game would then open its own main menu, so we open ours
        if (screen == null && self.level == null) return new ChromeTitleScreen();
        return screen;
    }
}
