@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mojang.blaze3d.platform.InputConstants
import kotlin.math.pow
import net.minecraft.client.gui.screens.Screen
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.OneConfigPrefs
import org.polyfrost.oneconfig.internal.ThemeConfig
import org.polyfrost.oneconfig.internal.ui.components.RetainedVisibility
import org.polyfrost.polyplus.client.gui.gamesocial.SocialWindow
import org.polyfrost.polyplus.client.gui.gamesocial.SocialWindowState
import org.polyfrost.polyplus.client.social.SocialOverlay

class SocialOverlayScreen(previousScreen: Screen?) : OverlayScreen(previousScreen) {
    init {
        // Chrome Client: friends and direct messages replace the Polyfrost social window; these flags are not used any more
        SocialOverlay.consumeAutoHostCurrentWorld()
        SocialOverlay.consumeOpenVoice()
    }

    //? if > 1.8.9 {
    override fun isPauseScreen(): Boolean = previous?.isPauseScreen == true
    //?} else {
    /*override fun shouldPauseGame(): Boolean = previous?.shouldPauseGame() == true
    *///?}

    override fun handleKeyPressed(key: Int, modifiers: Int): Boolean {
        return super.handleKeyPressed(key, modifiers)
    }

    @Composable
    override fun content(onClose: () -> Unit) {
        org.polyfrost.polyplus.client.gui.lobbysocial.ChromeChatPage(onBack = onClose)
    }
}

private const val OPEN_HIDDEN_SCALE = 0.9f

private val EaseOutExpo = Easing { x -> if (x >= 1f) 1f else 1f - 2f.pow(-10f * x) }

@Composable
internal fun SocialOpenAnimation(content: @Composable () -> Unit) {
    val openMillis = if (OneConfigPrefs.guiOpenAnimation) (ThemeConfig.animationTime * 1000f).toInt().coerceAtLeast(1) else 1
    RetainedVisibility(
        visible = true,
        enter = tween(openMillis, easing = EaseOutExpo),
        exit = tween(1),
        modifier = Modifier.fillMaxSize(),
        hiddenScale = OPEN_HIDDEN_SCALE,
    ) {
        content()
    }
}
