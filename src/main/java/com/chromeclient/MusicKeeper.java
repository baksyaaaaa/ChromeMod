package com.chromeclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Keeps the YT Music mod playing. Opening a menu (Right Shift, the pause menu...) made the music stop and it never started again;
 * if the music was playing when a menu opened and it gets stopped while that menu is open, this starts it again. A pause YOU make
 * (in the music window, or with the key while playing) is never undone: only a stop that happens while some other menu is open.
 */
public final class MusicKeeper {
    private static boolean wantPlaying;     // the music was playing and nobody asked it to stop
    private static boolean menuWasOpen;
    private static int retries;
    private static long graceUntil;         // just after a menu opens or closes the player may flicker: do not judge yet
    private static int tick;
    private static int silentTicks;

    private static boolean musicScreen(Screen s) {
        return s instanceof MusicScreen || (s != null && s.getClass().getName().startsWith("com.ytmusic."));
    }

    public static void tick(Minecraft mc) {
        if (++tick % 5 != 0) return;
        if (!YtBridge.installed()) return;
        long now = System.currentTimeMillis();
        Screen s = mc.screen;
        boolean menu = s != null && !musicScreen(s);
        YtBridge.Track t = YtBridge.current();
        boolean playing = t != null && t.playing();

        if (menu != menuWasOpen) {
            menuWasOpen = menu;
            graceUntil = now + 4000;
            retries = 0;
        }

        if (playing) {
            wantPlaying = true;
            silentTicks = 0;
            return;
        }

        if (t == null || t.title() == null || t.title().isBlank()) {
            // the player has nothing (it can drop out for a moment): if it was playing, give it a kick after a while
            if (wantPlaying && ++silentTicks > 12 && YtBridge.browserRunning() == false && retries < 3) {
                retries++;
                silentTicks = 0;
                YtBridge.ensureBrowser();
            }
            return;
        }

        // something is loaded but not playing
        if (wantPlaying) {
            if (menu || now < graceUntil) {
                // stopped by opening / closing a menu, not by you: start it again (a few tries)
                if (retries < 4 && (now > graceUntil - 3500 || menu)) {
                    retries++;
                    YtBridge.togglePlay();
                }
            } else if (s == null || musicScreen(s)) {
                wantPlaying = false;   // paused by you
            }
        }
    }
}
