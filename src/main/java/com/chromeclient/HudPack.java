package com.chromeclient;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Extra HUD panels, a custom crosshair and jump ripples: potion list, keybind list, target HUD, performance gauges,
 * a small watermark. Each panel sits in a corner of the screen with its own offset and size (set in the menu).
 */
public final class HudPack {
    /** one panel's on/off, corner (0 top-left, 1 top-right, 2 bottom-left, 3 bottom-right), offsets and size */
    public static final class Panel {
        public boolean on;
        public int corner, ox, oy;
        public float scale = 1f;
        Panel(boolean on, int corner, int ox, int oy) { this.on = on; this.corner = corner; this.ox = ox; this.oy = oy; }
    }

    public static final String[] CORNERS = {"Top left", "Top right", "Bottom left", "Bottom right"};
    public static final Panel potions = new Panel(false, 3, 6, 6);
    public static final Panel binds = new Panel(false, 2, 6, 6);
    public static final Panel target = new Panel(false, 0, 6, 70);
    public static final Panel perf = new Panel(false, 1, 6, 6);
    public static final Panel mark = new Panel(false, 0, 6, 6);

    // crosshair
    public static boolean crossOn = false;
    public static int crossShape = 0;          // 0 cross, 1 dot, 2 circle, 3 square, 4 cross + dot
    public static final String[] SHAPES = {"Cross", "Dot", "Circle", "Square", "Cross + dot"};
    public static int crossSize = 5, crossGap = 2, crossThick = 1, crossColor = 0;
    public static boolean crossOutline = true, crossDynamic = true;
    public static final int[] CROSS_COLORS = {0xFFFFFFFF, 0xFF3EE0A0, 0xFFFF6EB4, 0xFF5AA9FF, 0xFFFFA040, 0xFFB38CFF, 0xFFFF4D4D, 0xFFFFE27A};

    // jump ripples
    public static boolean rippleOn = false;
    public static int rippleStyle = 0;
    public static final String[] RIPPLES = {"Cloud", "Sparks", "Soul fire", "Glow", "Electric", "Petals"};

    private static long lastTargetAt = 0;
    private static LivingEntity lastTarget = null;
    private static final float[] fpsHist = new float[48];
    private static long histAt = 0;
    private static double cpu = 0;
    private static long cpuAt = 0;
    private static boolean wasGround = true;

    private HudPack() {}

    // ---------------------------------------------------------------- saving
    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("chrome-hudpack.json");
    }

    private static void put(JsonObject o, String k, Panel p) {
        o.addProperty(k + "On", p.on); o.addProperty(k + "Corner", p.corner);
        o.addProperty(k + "X", p.ox); o.addProperty(k + "Y", p.oy); o.addProperty(k + "S", p.scale);
    }

    private static void get(JsonObject o, String k, Panel p) {
        if (o.has(k + "On")) p.on = o.get(k + "On").getAsBoolean();
        if (o.has(k + "Corner")) p.corner = Mth.clamp(o.get(k + "Corner").getAsInt(), 0, 3);
        if (o.has(k + "X")) p.ox = Mth.clamp(o.get(k + "X").getAsInt(), 0, 600);
        if (o.has(k + "Y")) p.oy = Mth.clamp(o.get(k + "Y").getAsInt(), 0, 400);
        if (o.has(k + "S")) p.scale = Mth.clamp(o.get(k + "S").getAsFloat(), 0.5f, 2.5f);
    }

    public static void load() {
        try {
            Path f = file();
            if (!Files.isRegularFile(f)) return;
            JsonObject o = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            get(o, "potions", potions); get(o, "binds", binds); get(o, "target", target); get(o, "perf", perf); get(o, "mark", mark);
            if (o.has("crossOn")) crossOn = o.get("crossOn").getAsBoolean();
            if (o.has("crossShape")) crossShape = Mth.clamp(o.get("crossShape").getAsInt(), 0, SHAPES.length - 1);
            if (o.has("crossSize")) crossSize = Mth.clamp(o.get("crossSize").getAsInt(), 1, 16);
            if (o.has("crossGap")) crossGap = Mth.clamp(o.get("crossGap").getAsInt(), 0, 10);
            if (o.has("crossThick")) crossThick = Mth.clamp(o.get("crossThick").getAsInt(), 1, 4);
            if (o.has("crossColor")) crossColor = Mth.clamp(o.get("crossColor").getAsInt(), 0, CROSS_COLORS.length - 1);
            if (o.has("crossOutline")) crossOutline = o.get("crossOutline").getAsBoolean();
            if (o.has("crossDynamic")) crossDynamic = o.get("crossDynamic").getAsBoolean();
            if (o.has("rippleOn")) rippleOn = o.get("rippleOn").getAsBoolean();
            if (o.has("rippleStyle")) rippleStyle = Mth.clamp(o.get("rippleStyle").getAsInt(), 0, RIPPLES.length - 1);
        } catch (Exception ignored) {
            // defaults
        }
    }

    public static void save() {
        try {
            JsonObject o = new JsonObject();
            put(o, "potions", potions); put(o, "binds", binds); put(o, "target", target); put(o, "perf", perf); put(o, "mark", mark);
            o.addProperty("crossOn", crossOn); o.addProperty("crossShape", crossShape); o.addProperty("crossSize", crossSize);
            o.addProperty("crossGap", crossGap); o.addProperty("crossThick", crossThick); o.addProperty("crossColor", crossColor);
            o.addProperty("crossOutline", crossOutline); o.addProperty("crossDynamic", crossDynamic);
            o.addProperty("rippleOn", rippleOn); o.addProperty("rippleStyle", rippleStyle);
            Files.writeString(file(), o.toString());
        } catch (Exception ignored) {
            // still works this session
        }
    }

    // ---------------------------------------------------------------- setup
    public static void init() {
        try {
            HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(ChromeClientMod.MOD_ID, "hud_pack"), HudPack::render);
        } catch (Throwable ignored) {
            // no extra panels in this version
        }
        try {   // the crosshair: ours when it is on, the game's own otherwise
            HudElementRegistry.replaceElement(Identifier.fromNamespaceAndPath("minecraft", "crosshair"), original ->
                    (g, delta) -> {
                        if (crossOn && Minecraft.getInstance().player != null) drawCrosshair(g, Minecraft.getInstance());
                        else original.render(g, delta);
                    });
        } catch (Throwable ignored) {
            // the vanilla crosshair stays
        }
        ClientTickEvents.END_CLIENT_TICK.register(HudPack::tick);
    }

    // ---------------------------------------------------------------- jump ripples
    private static void tick(Minecraft mc) {
        var p = mc.player;
        if (p == null || mc.level == null) return;
        boolean ground = p.onGround();
        wasGround = ground;
    }

    // ---------------------------------------------------------------- panels
    private interface Body { void draw(GuiGraphics g, Font f, int w, int h); }

    /** puts a panel in its corner (with offset and size), draws the glass behind it and then its content */
    private static void panel(GuiGraphics g, Minecraft mc, Panel p, int w, int h, Body body) {
        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        float s = p.scale;
        float x = (p.corner & 1) == 0 ? p.ox : sw - p.ox - w * s;
        float y = (p.corner & 2) == 0 ? p.oy : sh - p.oy - h * s;
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(s, s);
        Ui.rrect(g, 0, 0, w, h, 4, 0xA01A1430);
        Ui.rring(g, 0, 0, w, h, 4, 0x44C9B8FF);
        body.draw(g, mc.font, w, h);
        g.pose().popMatrix();
    }

    private static String time(int ticks) {
        int s = Math.max(0, ticks / 20);
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        var pl = mc.player;
        if (pl == null || mc.options.hideGui || mc.screen instanceof MusicScreen) return;
        Font f = mc.font;

        // ---- potions ----
        if (potions.on) {
            List<MobEffectInstance> fx = new ArrayList<>(pl.getActiveEffects());
            if (!fx.isEmpty()) {
                int w = 70;
                List<String> names = new ArrayList<>(), times = new ArrayList<>();
                for (MobEffectInstance e : fx) {
                    String n = e.getEffect().value().getDisplayName().getString() + (e.getAmplifier() > 0 ? " " + (e.getAmplifier() + 1) : "");
                    String t = e.isInfiniteDuration() ? "inf" : time(e.getDuration());
                    names.add(n); times.add(t);
                    w = Math.max(w, 22 + f.width(n) + 10 + f.width(t));
                }
                int h = fx.size() * 12 + 8;
                panel(g, mc, potions, w, h, (gg, ff, ww, hh) -> {
                    for (int i = 0; i < fx.size(); i++) {
                        int col = 0xFF000000 | fx.get(i).getEffect().value().getColor();
                        int yy = 5 + i * 12;
                        Ui.rrect(gg, 5, yy + 1, 6, 6, 3, col);
                        Ui.text(gg, ff, names.get(i), 15, yy, Ui.TEXT);
                        Ui.text(gg, ff, times.get(i), ww - 5 - ff.width(times.get(i)), yy, Ui.MUTED);
                    }
                });
            }
        }

        // ---- keybinds ----
        if (binds.on) {
            KeyMapping[] keys = {mc.options.keyUp, mc.options.keyLeft, mc.options.keyDown, mc.options.keyRight,
                    mc.options.keyJump, mc.options.keySprint, mc.options.keyShift, mc.options.keyAttack, mc.options.keyUse};
            String[] labels = {"Forward", "Left", "Back", "Right", "Jump", "Sprint", "Sneak", "Attack", "Use"};
            int w = 96, h = keys.length * 12 + 8;
            panel(g, mc, binds, w, h, (gg, ff, ww, hh) -> {
                for (int i = 0; i < keys.length; i++) {
                    boolean down = keys[i].isDown();
                    int yy = 5 + i * 12;
                    if (down) Ui.rrect(gg, 2, yy - 2, ww - 4, 11, 3, Ui.accA(0x55));
                    Ui.text(gg, ff, labels[i], 6, yy, down ? Ui.TEXT : Ui.MUTED);
                    String k = keys[i].getTranslatedKeyMessage().getString();
                    Ui.text(gg, ff, k, ww - 6 - ff.width(k), yy, down ? Ui.TEXT : Ui.MUTED);
                }
            });
        }

        // ---- target ----
        long now = System.currentTimeMillis();
        if (mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity le && le != pl) {
            lastTarget = le;
            lastTargetAt = now;
        }
        if (target.on && lastTarget != null && now - lastTargetAt < 2500 && !lastTarget.isRemoved()) {
            LivingEntity le = lastTarget;
            String name = le.getDisplayName().getString();
            float hp = le.getHealth(), max = Math.max(1f, le.getMaxHealth());
            String info = String.format("%.1f / %.0f   %.1fm", hp, max, (float) pl.distanceTo(le));
            int w = Math.max(110, Math.max(f.width(name), f.width(info)) + 12);
            float frac = Mth.clamp(hp / max, 0f, 1f);
            int bar = Ui.lerp(0xFFFF4D4D, 0xFF3EE0A0, frac);
            panel(g, mc, target, w, 38, (gg, ff, ww, hh) -> {
                Ui.text(gg, ff, name, 6, 5, Ui.TEXT);
                Ui.rrect(gg, 6, 17, ww - 12, 5, 2, 0x33FFFFFF);
                Ui.rrect(gg, 6, 17, Math.max(2f, (ww - 12) * frac), 5, 2, bar);
                Ui.text(gg, ff, info, 6, 26, Ui.MUTED);
            });
        }

        // ---- performance ----
        if (perf.on) {
            if (now - histAt > 250) {
                histAt = now;
                System.arraycopy(fpsHist, 1, fpsHist, 0, fpsHist.length - 1);
                fpsHist[fpsHist.length - 1] = mc.getFps();
            }
            if (now - cpuAt > 1000) {
                cpuAt = now;
                try {
                    var os = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
                    double c = os.getProcessCpuLoad();
                    cpu = c < 0 ? 0 : c;
                } catch (Throwable t) {
                    cpu = 0;
                }
            }
            Runtime rt = Runtime.getRuntime();
            long used = (rt.totalMemory() - rt.freeMemory()) >> 20, maxMb = rt.maxMemory() >> 20;
            float ramFrac = Mth.clamp(used / (float) Math.max(1, maxMb), 0f, 1f);
            float peak = 60f;
            for (float v : fpsHist) peak = Math.max(peak, v);
            final float pk = peak;
            panel(g, mc, perf, 112, 62, (gg, ff, ww, hh) -> {
                Ui.text(gg, ff, mc.getFps() + " FPS", 6, 5, Ui.TEXT);
                for (int i = 0; i < fpsHist.length; i++) {   // little graph, one bar per quarter second
                    float v = Mth.clamp(fpsHist[i] / pk, 0f, 1f);
                    int bh = Math.max(1, Math.round(v * 16));
                    int col = fpsHist[i] < 30 ? 0xFFFF4D4D : fpsHist[i] < 60 ? 0xFFFFC832 : 0xFF3EE0A0;
                    Ui.rect(gg, 6 + i * 2, 24 - bh, 2, bh, col);
                }
                String ram = used + " / " + maxMb + " MB";
                Ui.text(gg, ff, "RAM " + ram, 6, 29, Ui.MUTED);
                Ui.rrect(gg, 6, 39, ww - 12, 4, 2, 0x33FFFFFF);
                Ui.rrect(gg, 6, 39, Math.max(2f, (ww - 12) * ramFrac), 4, 2, Ui.lerp(0xFF3EE0A0, 0xFFFF4D4D, ramFrac));
                Ui.text(gg, ff, "CPU " + Math.round(cpu * 100) + "%", 6, 47, Ui.MUTED);
                Ui.rrect(gg, 52, 49, ww - 58, 4, 2, 0x33FFFFFF);
                Ui.rrect(gg, 52, 49, Math.max(2f, (ww - 58) * (float) Mth.clamp(cpu, 0, 1)), 4, 2, Ui.lerp(0xFF3EE0A0, 0xFFFF4D4D, (float) Mth.clamp(cpu, 0, 1)));
            });
        }

        // ---- watermark ----
        if (mark.on) {
            String ver = FabricLoader.getInstance().getModContainer(ChromeClientMod.MOD_ID)
                    .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
            String s = "Chrome Client " + ver;
            panel(g, mc, mark, f.width(s) + 22, 18, (gg, ff, ww, hh) -> {
                Ui.rrect(gg, 5, 5, 8, 8, 4, Ui.ACCENT);
                Ui.text(gg, ff, s, 17, 5, Ui.TEXT);
            });
        }
    }

    // ---------------------------------------------------------------- crosshair
    private static void drawCrosshair(GuiGraphics g, Minecraft mc) {
        if (!mc.options.getCameraType().name().contains("FIRST")) return;
        int cx = mc.getWindow().getGuiScaledWidth() / 2, cy = mc.getWindow().getGuiScaledHeight() / 2;
        int col = CROSS_COLORS[Mth.clamp(crossColor, 0, CROSS_COLORS.length - 1)];
        int gap = crossGap, t = crossThick, len = crossSize;
        if (crossDynamic && mc.player != null) {   // opens a little while you move or sprint
            double sp = Math.hypot(mc.player.getX() - mc.player.xo, mc.player.getZ() - mc.player.zo);
            gap += (int) Math.min(5, sp * 14) + (mc.player.onGround() ? 0 : 2);
        }
        int outline = 0xFF000000;
        int half = t / 2;
        switch (crossShape) {
            case 1 -> dot(g, cx, cy, t + 1, col);
            case 2 -> {
                int r = len + gap;
                for (int i = 0; i < 24; i++) {
                    double a = i * Math.PI * 2 / 24;
                    int px = cx + (int) Math.round(Math.cos(a) * r), py = cy + (int) Math.round(Math.sin(a) * r);
                    if (crossOutline) Ui.rect(g, px - half - 1, py - half - 1, t + 2, t + 2, outline);
                }
                for (int i = 0; i < 24; i++) {
                    double a = i * Math.PI * 2 / 24;
                    int px = cx + (int) Math.round(Math.cos(a) * r), py = cy + (int) Math.round(Math.sin(a) * r);
                    Ui.rect(g, px - half, py - half, t, t, col);
                }
            }
            case 3 -> {
                int r = len + gap;
                if (crossOutline) Ui.rring(g, cx - r - 1, cy - r - 1, r * 2 + 2, r * 2 + 2, 0, outline);
                Ui.rring(g, cx - r, cy - r, r * 2, r * 2, 0, col);
            }
            default -> {
                arms(g, cx, cy, gap, len, t, half, col, crossOutline ? outline : 0);
                if (crossShape == 4) dot(g, cx, cy, t, col);
            }
        }
    }

    private static void dot(GuiGraphics g, int cx, int cy, int s, int col) {
        Ui.rect(g, cx - s / 2f, cy - s / 2f, s, s, col);
    }

    private static void arms(GuiGraphics g, int cx, int cy, int gap, int len, int t, int half, int col, int outline) {
        int[][] r = {{cx - gap - len, cy - half, len, t}, {cx + gap + 1, cy - half, len, t}, {cx - half, cy - gap - len, t, len}, {cx - half, cy + gap + 1, t, len}};
        if (outline != 0) for (int[] a : r) Ui.rect(g, a[0] - 1, a[1] - 1, a[2] + 2, a[3] + 2, outline);
        for (int[] a : r) Ui.rect(g, a[0], a[1], a[2], a[3], col);
    }
}
