package com.chromeclient;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Talks to the separate "YT Music In-Game" mod (id ytmusic) when it is installed: opens its player window and reads
 * what is playing. It all goes through reflection, so Chrome Client works fine without it.
 */
public final class YtBridge {
    private static final String CLIENT = "com.ytmusic.ingame.YtMusicClient";
    private static final String PLAYER_SCREEN = "com.ytmusic.ingame.PlayerScreen";

    private static Object player;
    private static Method mPlaying, mTitle, mArtist, mPosition, mDuration, mExact;
    private static boolean failed = false;
    private static boolean overlayHidden = false;

    public static boolean installed() {
        return FabricLoader.getInstance().isModLoaded("ytmusic");
    }

    /** opens the YT Music window; false if the mod isn't there */
    public static boolean open() {
        if (!installed()) return false;
        try {
            Class<?> c = Class.forName(PLAYER_SCREEN);
            var ctor = c.getDeclaredConstructor();
            ctor.setAccessible(true);
            Minecraft.getInstance().setScreen((Screen) ctor.newInstance());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean bind() {
        if (failed) return false;
        if (player != null) return true;
        if (!installed()) return false;
        try {
            Class<?> client = Class.forName(CLIENT);
            Object p = client.getField("PLAYER").get(null);
            if (p == null) return false;   // not created yet
            Class<?> pc = p.getClass();
            mPlaying = pc.getMethod("playing");
            mTitle = pc.getMethod("title");
            mArtist = pc.getMethod("artist");
            mPosition = pc.getMethod("position");
            mDuration = pc.getMethod("duration");
            try { mExact = pc.getMethod("positionPrecise"); } catch (Throwable ignored) { mExact = null; }   // only newer versions of that mod have it
            player = p;
            hideTopRightOverlay(client);
            return true;
        } catch (Throwable t) {
            failed = true;   // a different version of that mod: just no lyrics
            return false;
        }
    }

    /** that mod's own small now-playing bar in the top-right corner is replaced by our drop-down, so switch it off once (U cycles it back) */
    private static void hideTopRightOverlay(Class<?> client) {
        if (overlayHidden) return;
        overlayHidden = true;
        try {
            Object cfg = client.getField("CONFIG").get(null);
            Field f = cfg.getClass().getField("overlay");
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object hidden = Enum.valueOf((Class) f.getType(), "HIDDEN");
            f.set(cfg, hidden);
        } catch (Throwable ignored) {
            // a different version of that mod: leave it as it is
        }
    }

    /** keeps that mod's own top-right bar switched off (called every tick, cheap); does nothing if it isn't there */
    public static void hideOverlay() {
        if (!installed()) return;
        try {
            Object cfg = Class.forName(CLIENT).getField("CONFIG").get(null);
            if (cfg == null) return;
            Field f = cfg.getClass().getField("overlay");
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object hidden = Enum.valueOf((Class) f.getType(), "HIDDEN");
            if (f.get(cfg) != hidden) f.set(cfg, hidden);
        } catch (Throwable ignored) {
            // a different version of that mod: leave it as it is
        }
    }

    private static Object call(String name, Class<?>[] types, Object... args) {
        if (!bind()) return null;
        try {
            return player.getClass().getMethod(name, types).invoke(player, args);
        } catch (Throwable t) {
            return null;
        }
    }

    public static void togglePlay() { call("togglePlay", new Class<?>[0]); }
    public static void next() { call("next", new Class<?>[0]); }
    public static void prev() { call("prev", new Class<?>[0]); }
    public static void toggleMute() { call("toggleMute", new Class<?>[0]); }
    public static void seek(double fraction) { call("seekFraction", new Class<?>[]{double.class}, fraction); }
    public static void setVolume(int v) { call("setVolume", new Class<?>[]{int.class}, v); }

    public static int volume() {
        Object o = call("volume", new Class<?>[0]);
        return o instanceof Integer i ? i : 50;
    }

    public static boolean muted() {
        Object o = call("muted", new Class<?>[0]);
        return o instanceof Boolean b && b;
    }

    /** starts the browser in the background if it isn't running (so play / next work before the window was ever opened) */
    public static boolean ensureBrowser() {
        Object o = call("ensureBrowser", new Class<?>[]{int.class, int.class}, 960, 540);
        return o instanceof Boolean b && b;
    }

    public static boolean browserRunning() {
        return call("browser", new Class<?>[0]) != null;
    }

    /** title, artist, position (s), duration (s), playing: null if nothing can be read */
    public record Track(String title, String artist, int position, int duration, boolean playing, double exact) {}

    public static Track current() {
        if (!bind()) return null;
        try {
            double exact = -1;
            if (mExact != null) {
                try { exact = (Double) mExact.invoke(player); } catch (Throwable ignored) { exact = -1; }
            }
            return new Track((String) mTitle.invoke(player), (String) mArtist.invoke(player),
                    (Integer) mPosition.invoke(player), (Integer) mDuration.invoke(player), (Boolean) mPlaying.invoke(player), exact);
        } catch (Throwable t) {
            return null;
        }
    }
}
