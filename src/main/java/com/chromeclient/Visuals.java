package com.chromeclient;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Hand position (viewmodel), swing trail and block outline. The hand settings are read by HandTransformMixin;
 * the swing trail is a short arc of particles each time you swing; the block outline is drawn over the block you look at.
 */
public final class Visuals {
    // hand: where the held item / hand sits on screen (the numbers are 1/100 of a block, the size is a multiplier)
    public static boolean handOn = false;
    public static float handX = 0, handY = 0, handZ = 0, handScale = 1f, handTilt = 0f;

    // swing trail
    public static boolean trailOn = false;
    public static int trailStyle = 1;   // index into HudPack.RIPPLES

    // block outline
    public static boolean outlineOn = false;
    public static int outlineColor = 0;   // index into HudPack.CROSS_COLORS
    public static int outlineThick = 1;
    public static boolean fireOn = false;
    public static float fireHeight = 40f;   // % of the normal height of the fire on your screen

    /** how much further down the screen fire is drawn (the game's own spot is about 0.3 below the middle) */
    public static float fireDrop() {
        return (1f - fireHeight / 100f) * 0.75f;
    }
    public static boolean outlineGlow = true;

    private static boolean wasSwinging = false;

    private Visuals() {}

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("chrome-visuals.json");
    }

    public static void load() {
        try {
            Path f = file();
            if (!Files.isRegularFile(f)) return;
            JsonObject o = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            if (o.has("handOn")) handOn = o.get("handOn").getAsBoolean();
            if (o.has("handX")) handX = Mth.clamp(o.get("handX").getAsFloat(), -60, 60);
            if (o.has("handY")) handY = Mth.clamp(o.get("handY").getAsFloat(), -60, 60);
            if (o.has("handZ")) handZ = Mth.clamp(o.get("handZ").getAsFloat(), -60, 60);
            if (o.has("handScale")) handScale = Mth.clamp(o.get("handScale").getAsFloat(), 0.4f, 1.6f);
            if (o.has("handTilt")) handTilt = Mth.clamp(o.get("handTilt").getAsFloat(), -45, 45);
            if (o.has("trailOn")) trailOn = o.get("trailOn").getAsBoolean();
            if (o.has("trailStyle")) trailStyle = Mth.clamp(o.get("trailStyle").getAsInt(), 0, HudPack.RIPPLES.length - 1);
            if (o.has("outlineColor")) outlineColor = Mth.clamp(o.get("outlineColor").getAsInt(), 0, HudPack.CROSS_COLORS.length - 1);
            if (o.has("fireOn")) fireOn = o.get("fireOn").getAsBoolean();
            if (o.has("fireHeight")) fireHeight = Mth.clamp(o.get("fireHeight").getAsFloat(), 0f, 100f);
            if (o.has("outlineThick")) outlineThick = Mth.clamp(o.get("outlineThick").getAsInt(), 1, 3);
            if (o.has("outlineGlow")) outlineGlow = o.get("outlineGlow").getAsBoolean();
        } catch (Exception ignored) {
            // defaults
        }
    }

    public static void save() {
        try {
            JsonObject o = new JsonObject();
            o.addProperty("handOn", handOn); o.addProperty("handX", handX); o.addProperty("handY", handY);
            o.addProperty("handZ", handZ); o.addProperty("handScale", handScale); o.addProperty("handTilt", handTilt);
            o.addProperty("trailOn", trailOn); o.addProperty("trailStyle", trailStyle);
            o.addProperty("outlineOn", outlineOn); o.addProperty("outlineColor", outlineColor);
            o.addProperty("fireOn", fireOn); o.addProperty("fireHeight", fireHeight);
            o.addProperty("outlineThick", outlineThick); o.addProperty("outlineGlow", outlineGlow);
            Files.writeString(file(), o.toString());
        } catch (Exception ignored) {
            // still works this session
        }
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(Visuals::tick);
    }

    private static ParticleOptions style(int i) {
        return switch (i) {
            case 1 -> ParticleTypes.END_ROD;
            case 2 -> ParticleTypes.SOUL_FIRE_FLAME;
            case 3 -> ParticleTypes.GLOW;
            case 4 -> ParticleTypes.ELECTRIC_SPARK;
            case 5 -> ParticleTypes.CHERRY_LEAVES;
            default -> ParticleTypes.CLOUD;
        };
    }

    // ---------------------------------------------------------------- swing trail
    private static void tick(Minecraft mc) {
        var p = mc.player;
        if (p == null || mc.level == null) return;
        boolean sw = p.swinging;
        wasSwinging = sw;
    }

    private static float[] project(double dx, double dy, double dz, double rx, double rz, double ux, double uy, double uz,
                                   double fx, double fy, double fz, int sw, int sh, double tanHalf, double aspect) {
        double cz = dx * fx + dy * fy + dz * fz;
        if (cz < 0.1) return null;
        double cx = dx * rx + dz * rz, cy = dx * ux + dy * uy + dz * uz;
        return new float[]{(float) (sw / 2.0 * (1.0 + cx / (cz * tanHalf * aspect))), (float) (sh / 2.0 * (1.0 - cy / (cz * tanHalf)))};
    }
}
