package com.chromeboost;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/** every option of the mod, saved as config/chrome-boost.json */
public final class Settings {
    // entities
    public boolean entityCull = true;
    public int itemDistance = 32;       // blocks; dropped items, XP orbs, arrows beyond this are not drawn (0 = off)
    public int mobDistance = 96;        // blocks; mobs and other entities beyond this are not drawn (0 = off)
    // particles
    public boolean particleCull = true;
    public int particleDistance = 32;   // blocks; particles that start further away than this are skipped
    public int particleCap = 400;       // most particles that may be created in one tick (0 = off)
    // frame pacing
    public boolean backgroundLimit = true;
    public int backgroundFps = 15;      // FPS cap while the game window is not in front
    // render distance
    public boolean adaptive = false;
    public int targetFps = 60;
    public int minDistance = 6;         // adaptive render distance never goes below this many chunks
    // memory
    public boolean memoryWarning = true;
    public boolean cleanOnLeave = true; // one memory clean-up shortly after you leave a world

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Settings instance = new Settings();

    public static Settings get() {
        return instance;
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("chrome-boost.json");
    }

    public static void load() {
        try {
            Path f = file();
            if (Files.exists(f)) {
                Settings s = GSON.fromJson(Files.readString(f), Settings.class);
                if (s != null) instance = s;
            }
        } catch (Throwable ignored) {
            // a broken file just means defaults
        }
        instance.sanitize();
    }

    public static void save() {
        try {
            instance.sanitize();
            Files.writeString(file(), GSON.toJson(instance));
        } catch (Throwable ignored) {
            // saving is best effort
        }
    }

    private void sanitize() {
        itemDistance = clamp(itemDistance, 0, 256);
        mobDistance = clamp(mobDistance, 0, 512);
        particleDistance = clamp(particleDistance, 0, 256);
        particleCap = clamp(particleCap, 0, 5000);
        backgroundFps = clamp(backgroundFps, 5, 60);
        targetFps = clamp(targetFps, 20, 240);
        minDistance = clamp(minDistance, 2, 32);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
