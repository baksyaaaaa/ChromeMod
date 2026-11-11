package com.chromeclient;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/** Everything you can switch in the menu. Saved to config/chromeclient.json. */
public final class Config {
    public boolean fps = true;
    public boolean cps = true;
    public boolean ping = false;
    public boolean coords = true;
    public boolean direction = false;
    public boolean clock = false;
    public boolean keystrokes = true;
    public boolean armor = true;
    public boolean autoSprint = false;
    public boolean zoom = true;
    public int color = 0;   // index into COLORS
    /** pay timer length in seconds, and an optional custom chat pattern (named groups: amount, player) */
    public int payTimerSeconds = 300;
    public String payRegex = "";
    /** how far each movable element was dragged from its default spot: id -> {dx, dy} */
    public java.util.Map<String, int[]> offsets = new java.util.HashMap<>();
    /** how much each movable element was resized (1.0 = normal) */
    public java.util.Map<String, Float> scales = new java.util.HashMap<>();

    public static final int[] COLORS = {0xFFFFFFFF, 0xFF3EE0A0, 0xFFFF6EB4, 0xFF5AA9FF, 0xFFFFA040};
    public static final String[] COLOR_NAMES = {"White", "Mint", "Pink", "Blue", "Orange"};

    private static Config instance;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static Config get() {
        if (instance == null) load();
        return instance;
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("chromeclient.json");
    }

    public static void load() {
        Config c = null;
        try {
            Path p = file();
            if (Files.isRegularFile(p)) c = GSON.fromJson(Files.readString(p), Config.class);
        } catch (Exception ignored) {
            // unreadable file: start from defaults
        }
        if (c == null) c = new Config();
        c.color = Math.floorMod(c.color, COLORS.length);
        c.payTimerSeconds = Math.max(5, Math.min(99999, c.payTimerSeconds));
        if (c.payRegex == null) c.payRegex = "";
        if (c.scales == null) c.scales = new java.util.HashMap<>();
        if (c.offsets == null) c.offsets = new java.util.HashMap<>();
        instance = c;
    }

    public static void save() {
        try {
            Files.writeString(file(), GSON.toJson(get()));
        } catch (Exception ignored) {
            // read-only folder etc.: the settings just won't persist
        }
    }

    public int[] off(String id) {
        int[] o = offsets.get(id);
        if (o == null || o.length < 2) {
            o = new int[]{0, 0};
            offsets.put(id, o);
        }
        return o;
    }

    public float scale(String id) {
        Float f = scales == null ? null : scales.get(id);
        return f == null ? 1f : Math.max(0.5f, Math.min(3f, f));
    }

    public void setScale(String id, float v) {
        if (scales == null) scales = new java.util.HashMap<>();
        scales.put(id, Math.max(0.5f, Math.min(3f, v)));
    }

    public int textColor() {
        return 0xFFFFFFFF;
    }
}
