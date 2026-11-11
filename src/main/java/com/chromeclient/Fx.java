package com.chromeclient;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Our own particles (instead of the game's): soft glowing orbs, twinkling stars, hearts, flames with a hot core,
 * petals, spark streaks and zaps. They are simulated here and drawn over the world, fading in and out, shrinking,
 * changing colour as they age. Particles behind blocks are hidden.
 */
public final class Fx {
    public enum Kind { STAR, GLOW, HEART, FLAME, SNOW, NOTE, SOUL, PETAL, SPARK, ZAP, PUFF, SPORE, EMBER, ASH }

    private static final class P {
        double x, y, z, vx, vy, vz;
        float gravity, drag;
        int age, life;
        float s0, s1;      // size in blocks at the start and at the end
        int c0, c1;        // colours at the start and at the end (rgb)
        Kind k;
        float rot, spin, seed;
        boolean hidden;
    }

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("ChromeFx");
    private static boolean drew = false;
    private static final List<P> list = new ArrayList<>();
    private static final int MAX = 260;
    private static ClientLevel inLevel = null;
    private static int tickNo = 0;

    private Fx() {}

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(Fx::tick);
        try {
            HudElementRegistry.addFirst(Identifier.fromNamespaceAndPath(ChromeClientMod.MOD_ID, "fx"), Fx::render);
            LOG.info("custom particles ready");
        } catch (Throwable t) {
            LOG.error("custom particles could not start", t);
        }
    }

    // ---------------------------------------------------------------- spawning
    /** the particle that belongs to a cosmetic effect (1..10, see Cosmetics.ITEMS[3]) */
    public static void effect(int e, double x, double y, double z, double vx, double vy, double vz) {
        Kind k = switch (e) {
            case 1 -> Kind.STAR;
            case 2 -> Kind.HEART;
            case 3 -> Kind.FLAME;
            case 4 -> Kind.SNOW;
            case 5 -> Kind.STAR;
            case 6 -> Kind.NOTE;
            case 7 -> Kind.SOUL;
            case 8 -> Kind.PETAL;
            case 9 -> Kind.ZAP;
            default -> Kind.GLOW;
        };
        if (e == 5) spawn(k, x, y, z, vx, vy, vz, 0xB38CFF, 0x5CF2FF);          // magic: violet to cyan stars
        else if (e == 10) spawn(k, x, y, z, vx, vy, vz, 0x7CF3E0, 0x4D8DFF);   // glow: soft teal to blue
        else spawn(k, x, y, z, vx, vy, vz, -1, -1);
    }

    /** the particle for a ripple / swing trail style (see HudPack.RIPPLES) */
    public static void style(int s, double x, double y, double z, double vx, double vy, double vz) {
        Kind k = switch (s) {
            case 1 -> Kind.STAR;
            case 2 -> Kind.SOUL;
            case 3 -> Kind.GLOW;
            case 4 -> Kind.ZAP;
            case 5 -> Kind.PETAL;
            default -> Kind.PUFF;
        };
        spawn(k, x, y, z, vx, vy, vz, -1, -1);
    }

    public static void spawn(Kind k, double x, double y, double z, double vx, double vy, double vz, int ca, int cb) {
        if (list.size() >= MAX) return;
        Minecraft mc = Minecraft.getInstance();
        int fps = mc.getFps();
        if (fps > 0 && fps < 45 && ThreadLocalRandom.current().nextFloat() > Math.max(0.2f, (fps - 15) / 40f)) return;   // easy on a slow PC
        ThreadLocalRandom r = ThreadLocalRandom.current();
        P p = new P();
        p.k = k; p.x = x; p.y = y; p.z = z; p.vx = vx; p.vy = vy; p.vz = vz;
        p.seed = r.nextFloat() * 100f;
        p.rot = r.nextFloat() * 6.28f;
        p.spin = (r.nextFloat() - 0.5f) * 0.25f;
        p.drag = 0.96f;
        switch (k) {
            case STAR -> { p.life = 24 + r.nextInt(14); p.s0 = 0.10f; p.s1 = 0.02f; p.c0 = 0xFFFFFF; p.c1 = 0xFFC832; p.gravity = -0.0006f; }
            case GLOW -> { p.life = 30 + r.nextInt(24); p.s0 = 0.09f; p.s1 = 0.02f; p.c0 = 0xCFFFF4; p.c1 = 0x4DD6FF; p.gravity = -0.0004f; }
            case HEART -> { p.life = 30 + r.nextInt(12); p.s0 = 0.14f; p.s1 = 0.10f; p.c0 = 0xFF8FB5; p.c1 = 0xFF4D7A; p.gravity = -0.0012f; p.spin = 0; }
            case FLAME -> { p.life = 14 + r.nextInt(10); p.s0 = 0.14f; p.s1 = 0.01f; p.c0 = 0xFFE27A; p.c1 = 0xFF3B14; p.gravity = -0.004f; p.drag = 0.93f; }
            case SNOW -> { p.life = 46 + r.nextInt(30); p.s0 = 0.06f; p.s1 = 0.04f; p.c0 = 0xFFFFFF; p.c1 = 0xBFE4FF; p.gravity = 0.0006f; p.drag = 0.97f; }
            case NOTE -> { p.life = 32 + r.nextInt(10); p.s0 = 0.16f; p.s1 = 0.12f; p.c0 = hue((float) vx); p.c1 = p.c0; p.gravity = -0.0008f; p.vx = 0; p.spin = 0; }
            case SOUL -> { p.life = 16 + r.nextInt(12); p.s0 = 0.13f; p.s1 = 0.01f; p.c0 = 0xC8FBFF; p.c1 = 0x1F8FFF; p.gravity = -0.0035f; p.drag = 0.93f; }
            case PETAL -> { p.life = 44 + r.nextInt(30); p.s0 = 0.08f; p.s1 = 0.07f; p.c0 = 0xFFD2E2; p.c1 = 0xFF8FB5; p.gravity = 0.0008f; p.drag = 0.95f; }
            case SPARK -> { p.life = 8 + r.nextInt(6); p.s0 = 0.05f; p.s1 = 0.02f; p.c0 = 0xFFFFFF; p.c1 = 0x6FB4FF; p.gravity = 0.002f; p.drag = 0.9f; }
            case ZAP -> { p.life = 6 + r.nextInt(6); p.s0 = 0.30f; p.s1 = 0.10f; p.c0 = 0xFFFFFF; p.c1 = 0x7AB8FF; p.gravity = 0f; p.drag = 0.8f; }
            case PUFF -> { p.life = 14 + r.nextInt(8); p.s0 = 0.08f; p.s1 = 0.30f; p.c0 = 0xFFFFFF; p.c1 = 0xBFD2FF; p.gravity = 0f; p.drag = 0.88f; }
            case SPORE -> { p.life = 60 + r.nextInt(40); p.s0 = 0.07f; p.s1 = 0.03f; p.c0 = 0xCFFF9A; p.c1 = 0x3EE07A; p.gravity = -0.0003f; p.drag = 0.98f; }
            case EMBER -> { p.life = 40 + r.nextInt(30); p.s0 = 0.06f; p.s1 = 0.01f; p.c0 = 0xFFC060; p.c1 = 0xFF3B14; p.gravity = -0.0008f; p.drag = 0.97f; }
            default -> { p.life = 60 + r.nextInt(40); p.s0 = 0.05f; p.s1 = 0.04f; p.c0 = 0x8A8A92; p.c1 = 0x4A4A52; p.gravity = 0.0008f; p.drag = 0.98f; }   // ash
        }
        if (ca >= 0) { p.c0 = ca; p.c1 = cb >= 0 ? cb : ca; }
        list.add(p);
    }

    private static int hue(float h) {
        return java.awt.Color.HSBtoRGB(Mth.positiveModulo(h, 1f), 0.55f, 1f) & 0xFFFFFF;
    }

    // ---------------------------------------------------------------- simulating
    private static void tick(Minecraft mc) {
        ClientLevel lv = mc.level;
        if (lv == null || mc.player == null) { list.clear(); inLevel = null; return; }
        if (lv != inLevel) { list.clear(); inLevel = lv; }
        if (mc.isPaused()) return;
        tickNo++;
        Vec3 eye = mc.player.getEyePosition();
        int rays = 0;
        for (int i = list.size() - 1; i >= 0; i--) {
            P p = list.get(i);
            p.age++;
            if (p.age >= p.life) { list.remove(i); continue; }
            p.vy -= p.gravity;
            p.vx *= p.drag; p.vy *= p.drag; p.vz *= p.drag;
            if (p.k == Kind.PETAL || p.k == Kind.SNOW) {   // flutter and drift
                p.vx += Math.sin((p.age + p.seed) * 0.35) * 0.003;
                p.vz += Math.cos((p.age + p.seed) * 0.31) * 0.003;
            }
            p.x += p.vx; p.y += p.vy; p.z += p.vz;
            p.rot += p.spin;
            if (rays < 24 && (p.age + i) % 6 == 0) {   // is a block in the way? (only a few checks per tick)
                rays++;
                Vec3 to = new Vec3(p.x, p.y, p.z);
                if (to.distanceToSqr(eye) < 400) {
                    BlockHitResult h = lv.clip(new ClipContext(eye, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
                    p.hidden = h.getType() == HitResult.Type.BLOCK && h.getLocation().distanceToSqr(eye) < to.distanceToSqr(eye) - 0.2;
                }
            }
        }
    }

    // ---------------------------------------------------------------- drawing
    private static int col(int rgb, float a) {
        return (Mth.clamp(Math.round(a * 255f), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    private static int mix(int a, int b, float t) {
        t = Mth.clamp(t, 0f, 1f);
        int r = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return (r << 16) | (g << 8) | bl;
    }

    /** a soft glowing orb: wide faint halo, brighter body, white-hot core */
    private static void orb(GuiGraphics g, float x, float y, float r, int rgb, float a, float core) {
        if (r < 1.1f) { Ui.rect(g, x - r, y - r, r * 2, r * 2, col(rgb, a)); return; }
        Ui.rrect(g, x - r * 1.8f, y - r * 1.8f, r * 3.6f, r * 3.6f, r * 1.8f, col(rgb, a * 0.18f));
        float c = r * 0.6f;
        Ui.rrect(g, x - c, y - c, c * 2, c * 2, c, col(mix(rgb, 0xFFFFFF, core), a));
    }

    private static void line(GuiGraphics g, float x0, float y0, float x1, float y1, float w, int color) {
        float dx = x1 - x0, dy = y1 - y0;
        int n = Math.max(1, (int) (Math.hypot(dx, dy) / Math.max(0.8f, w * 0.7f)));
        for (int i = 0; i <= n; i++) {
            float t = i / (float) n;
            Ui.rect(g, x0 + dx * t - w / 2, y0 + dy * t - w / 2, w, w, color);
        }
    }

    private static final String[] HEART = {"0110110", "1111111", "1111111", "0111110", "0011100", "0001000"};

    private static void render(GuiGraphics g, DeltaTracker delta) {
        try {
            draw(g, delta);
        } catch (Throwable t) {
            if (!drew) { drew = true; LOG.error("custom particles failed to draw", t); }
        }
    }

    private static void draw(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        var pl = mc.player;
        if (list.isEmpty() || pl == null || mc.level == null || mc.options.hideGui) return;
        float pt = delta.getGameTimeDeltaPartialTick(false);

        // the camera (first person, or behind / in front of you in third person)
        double ex = Mth.lerp(pt, pl.xo, pl.getX()), ey = Mth.lerp(pt, pl.yo, pl.getY()) + pl.getEyeHeight(), ez = Mth.lerp(pt, pl.zo, pl.getZ());
        float yawDeg = Mth.lerp(pt, pl.yRotO, pl.getYRot()) + (Freelook.active ? Freelook.yaw : 0f);
        float pitchDeg = Mth.clamp(Mth.lerp(pt, pl.xRotO, pl.getXRot()) + (Freelook.active ? Freelook.pitch : 0f), -90f, 90f);
        String cam = mc.options.getCameraType().name();
        double yw = Math.toRadians(yawDeg), pc = Math.toRadians(pitchDeg);
        double lfx = -Math.sin(yw) * Math.cos(pc), lfy = -Math.sin(pc), lfz = Math.cos(yw) * Math.cos(pc);
        double camX = ex, camY = ey, camZ = ez;
        if (cam.contains("BACK")) { camX -= lfx * 4.0; camY -= lfy * 4.0; camZ -= lfz * 4.0; }
        else if (cam.contains("FRONT")) { camX += lfx * 4.0; camY += lfy * 4.0; camZ += lfz * 4.0; yawDeg += 180f; pitchDeg = -pitchDeg; }
        double yaw = Math.toRadians(yawDeg), pitch = Math.toRadians(pitchDeg);
        double fx = -Math.sin(yaw) * Math.cos(pitch), fy = -Math.sin(pitch), fz = Math.cos(yaw) * Math.cos(pitch);
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        double ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy;
        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        double tanHalf = Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0);
        double aspect = sw / (double) Math.max(1, sh);
        float time = (System.currentTimeMillis() % 100000L) / 1000f;

        for (P p : list) {
            if (p.hidden) continue;
            double px = p.x + p.vx * pt, py = p.y + p.vy * pt, pz = p.z + p.vz * pt;
            double dx = px - camX, dy = py - camY, dz = pz - camZ;
            double cz = dx * fx + dy * fy + dz * fz;
            if (cz < 0.4 || cz > 60) continue;
            double cx = dx * rx + dz * rz, cy = dx * ux + dy * uy + dz * uz;
            float sx = (float) (sw / 2.0 * (1.0 + cx / (cz * tanHalf * aspect)));
            float sy = (float) (sh / 2.0 * (1.0 - cy / (cz * tanHalf)));
            if (sx < -40 || sx > sw + 40 || sy < -40 || sy > sh + 40) continue;
            float t = (p.age + pt) / p.life;
            float fade = Math.min(Mth.clamp(t / 0.12f, 0f, 1f), Mth.clamp((1f - t) / 0.45f, 0f, 1f));
            float near = Mth.clamp((float) (cz - 0.4) / 1.2f, 0f, 1f);   // fades out when it is right at your face
            float a = fade * near;
            if (a < 0.02f) continue;
            float sizeW = Mth.lerp(t, p.s0, p.s1);
            float r = (float) (sizeW * (sh / 2.0) / (cz * tanHalf));   // blocks -> screen pixels
            r = Mth.clamp(r, 0.5f, 40f);
            int rgb = mix(p.c0, p.c1, t);
            switch (p.k) {
                case GLOW, SPORE, SNOW -> orb(g, sx, sy, r, rgb, a, p.k == Kind.SNOW ? 0.8f : 0.5f);
                case EMBER -> orb(g, sx, sy, r, rgb, a * (0.7f + 0.3f * (float) Math.sin(time * 18 + p.seed)), 0.7f);
                case FLAME, SOUL -> {   // hot core, colour cools towards the edges
                    float flick = 0.8f + 0.2f * (float) Math.sin(time * 25 + p.seed);
                    orb(g, sx, sy, r * flick, rgb, a, 0.85f);
                }
                case PUFF -> {
                    Ui.rrect(g, sx - r, sy - r, r * 2, r * 2, r, col(rgb, a * 0.35f));
                    Ui.rring(g, sx - r, sy - r, r * 2, r * 2, r, col(0xFFFFFF, a * 0.5f));
                }
                case STAR -> {   // four-point sparkle that twinkles
                    float tw = 0.65f + 0.35f * (float) Math.sin(time * 14 + p.seed * 3);
                    float len = r * 2.6f * tw, th = Math.max(1f, r * 0.28f);
                    g.pose().pushMatrix();
                    g.pose().translate(sx, sy);
                    g.pose().rotate(p.rot);
                    Ui.rect(g, -len, -th / 2, len * 2, th, col(rgb, a * 0.9f));
                    Ui.rect(g, -th / 2, -len, th, len * 2, col(rgb, a * 0.9f));
                    g.pose().popMatrix();
                    orb(g, sx, sy, Math.max(1f, r * 0.55f), rgb, a, 0.9f);
                }
                case HEART -> {
                    float cell = Math.max(1f, r * 0.55f);
                    float w = 7 * cell, h = 6 * cell;
                    for (int ry = 0; ry < HEART.length; ry++) {
                        for (int rxx = 0; rxx < 7; rxx++) {
                            if (HEART[ry].charAt(rxx) == '1') Ui.rect(g, sx - w / 2 + rxx * cell, sy - h / 2 + ry * cell, cell, cell, col(ry < 2 && rxx < 2 && rxx > 0 ? mix(rgb, 0xFFFFFF, 0.5f) : rgb, a));
                        }
                    }
                }
                case NOTE -> {
                    g.pose().pushMatrix();
                    g.pose().translate(sx, sy);
                    float sc = Math.max(0.6f, r / 4f);
                    g.pose().scale(sc, sc);
                    g.drawString(mc.font, "♪", -3, -4, col(rgb, a), false);
                    g.pose().popMatrix();
                }
                case PETAL -> {
                    g.pose().pushMatrix();
                    g.pose().translate(sx, sy);
                    g.pose().rotate(p.rot + time * 2f + p.seed);
                    float sq = 0.4f + 0.6f * Math.abs((float) Math.sin(time * 3 + p.seed));   // turns as it falls
                    Ui.rrect(g, -r, -r * sq * 0.5f, r * 2, Math.max(1f, r * sq), Math.max(0.5f, r * sq * 0.5f), col(rgb, a));
                    g.pose().popMatrix();
                }
                case SPARK, ZAP -> {
                    // a streak along the way it moves, bright at the head and fading behind it
                    double bx = px - p.vx * 3, by = py - p.vy * 3, bz = pz - p.vz * 3;
                    double ddx = bx - camX, ddy = by - camY, ddz = bz - camZ;
                    double bcz = ddx * fx + ddy * fy + ddz * fz;
                    if (bcz < 0.4) break;
                    float bsx = (float) (sw / 2.0 * (1.0 + (ddx * rx + ddz * rz) / (bcz * tanHalf * aspect)));
                    float bsy = (float) (sh / 2.0 * (1.0 - (ddx * ux + ddy * uy + ddz * uz) / (bcz * tanHalf)));
                    float w = Math.max(1f, r * 0.35f);
                    if (p.k == Kind.ZAP) {   // jagged lightning: three kinks that change every few ticks
                        long sd = (long) (p.seed * 1000) + p.age / 2;
                        java.util.Random jr = new java.util.Random(sd);
                        float lx = sx, ly = sy;
                        for (int s = 1; s <= 3; s++) {
                            float tx = Mth.lerp(s / 3f, sx, bsx) + (jr.nextFloat() - 0.5f) * r * 3f * (s < 3 ? 1 : 0);
                            float ty = Mth.lerp(s / 3f, sy, bsy) + (jr.nextFloat() - 0.5f) * r * 3f * (s < 3 ? 1 : 0);
                            line(g, lx, ly, tx, ty, w, col(rgb, a));
                            line(g, lx, ly, tx, ty, w * 2.6f, col(rgb, a * 0.18f));
                            lx = tx; ly = ty;
                        }
                    } else {
                        line(g, sx, sy, bsx, bsy, w, col(rgb, a));
                        orb(g, sx, sy, Math.max(1f, r * 0.6f), rgb, a, 0.9f);
                    }
                }
                default -> {   // ash: tiny tumbling flake
                    g.pose().pushMatrix();
                    g.pose().translate(sx, sy);
                    g.pose().rotate(p.rot);
                    Ui.rect(g, -r * 0.6f, -r * 0.4f, r * 1.2f, r * 0.8f, col(rgb, a * 0.8f));
                    g.pose().popMatrix();
                }
            }
        }
    }
}
