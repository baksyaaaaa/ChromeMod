package com.chromeclient;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/** Small on/off settings for Freelook and the Lyrics pop-ups, saved in config/chrome-extras.json. */
public final class Extras {
    public static boolean freelook = true;
    public static boolean lyrics = false;
    public static boolean emotes = true;           // press B for the emote wheel
    // world lyrics settings
    public static float lyricsDistance = 9f;       // how far away the words hang (blocks)
    public static float lyricsSize = 1f;
    public static float lyricsScatter = 0.7f;      // how widely they spread around where you look
    public static float lyricsSync = -0.7f;        // seconds: negative = lyrics come later, positive = earlier
    public static boolean lyricsGlow = true;
    public static float lyricsGlowStrength = 0.9f;
    public static boolean lyricsShadow = true;
    public static boolean hideYtBar = true;        // hide the YT Music mod's own bar in the top-right corner
    public static float lyricsTilt = 0.6f;        // how much the words turn away from facing you
    public static int lyricsPalette = 0;          // flowing gradient colours
    public static boolean topPill = false;          // clock / name / FPS / ping pill at the top centre
    public static int uiTheme = 0;                 // menu theme (see Ui.THEMES)
    public static boolean lyricsPill = true;       // the "now singing" pill at the top

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("chrome-extras.json");
    }

    public static void load() {
        try {
            Path f = file();
            if (!Files.isRegularFile(f)) return;
            JsonObject o = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            if (o.has("freelook")) freelook = o.get("freelook").getAsBoolean();
            if (o.has("emotes")) emotes = o.get("emotes").getAsBoolean();
            if (o.has("lyrics")) lyrics = o.get("lyrics").getAsBoolean();
            if (o.has("lyricsDistance")) lyricsDistance = o.get("lyricsDistance").getAsFloat();
            if (o.has("lyricsSize")) lyricsSize = o.get("lyricsSize").getAsFloat();
            if (o.has("lyricsScatter")) lyricsScatter = o.get("lyricsScatter").getAsFloat();
            if (o.has("lyricsSync")) lyricsSync = o.get("lyricsSync").getAsFloat();
            if (o.has("lyricsGlow")) lyricsGlow = o.get("lyricsGlow").getAsBoolean();
            if (o.has("lyricsGlowStrength")) lyricsGlowStrength = o.get("lyricsGlowStrength").getAsFloat();
            if (o.has("lyricsShadow")) lyricsShadow = o.get("lyricsShadow").getAsBoolean();
            if (o.has("hideYtBar")) hideYtBar = o.get("hideYtBar").getAsBoolean();
            if (o.has("lyricsTilt")) lyricsTilt = o.get("lyricsTilt").getAsFloat();
            if (o.has("lyricsPalette")) lyricsPalette = o.get("lyricsPalette").getAsInt();
            if (o.has("lyricsPill")) lyricsPill = o.get("lyricsPill").getAsBoolean();
            if (o.has("uiTheme")) { uiTheme = o.get("uiTheme").getAsInt(); Ui.setTheme(uiTheme); }
        } catch (Exception ignored) {
            // defaults
        }
    }

    public static void save() {
        try {
            JsonObject o = new JsonObject();
            o.addProperty("freelook", freelook);
            o.addProperty("uiTheme", uiTheme);
            o.addProperty("lyrics", lyrics);
            o.addProperty("emotes", emotes);
            o.addProperty("lyricsDistance", lyricsDistance);
            o.addProperty("lyricsSize", lyricsSize);
            o.addProperty("lyricsScatter", lyricsScatter);
            o.addProperty("lyricsSync", lyricsSync);
            o.addProperty("lyricsGlow", lyricsGlow);
            o.addProperty("lyricsGlowStrength", lyricsGlowStrength);
            o.addProperty("lyricsShadow", lyricsShadow);
            o.addProperty("lyricsTilt", lyricsTilt);
            o.addProperty("lyricsPalette", lyricsPalette);
            o.addProperty("topPill", topPill);
            o.addProperty("lyricsPill", lyricsPill);
            o.addProperty("hideYtBar", hideYtBar);
            Files.writeString(file(), o.toString());
        } catch (Exception ignored) {
            // can't save: it still works this session
        }
    }
}
