package com.chromeclient;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Cosmetic effects as ONE shape each, not a cloud of particles: a rune circle, a spinning snowflake, a fire ring,
 * a spiral, orbiting rings, a heart, lightning bolts, a ribbon behind your feet, feathers for wings.
 * They are drawn as smooth glowing lines over the world, a few hundred rectangles in total, so they are also cheap.
 */
public final class Aura {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("ChromeAura");
    private static boolean logged = false;

    private static final class A {
        int e, where;
        float amount;
        long last;
        final ArrayDeque<double[]> hist = new ArrayDeque<>();
    }

    private static final Map<Integer, A> map = new HashMap<>();

    /** a one-off shape (jump shockwave, sword slash) that grows and fades by itself */
    private static final class Shot {
        boolean slash;
        int style;
        long born;
        double x, y, z;
        List<double[]> arc;   // slash: the curve in the world
    }
    private static final List<Shot> shots = new ArrayList<>();
    private static final int[][] STYLE_COL = {
            {0xFFFFFF, 0x9FC4FF}, {0xFFFFFF, 0xFFD34D}, {0xC8FBFF, 0x1F7FFF},
            {0x7CF3E0, 0x4D8DFF}, {0xFFFFFF, 0x6FA8FF}, {0xFFD2E2, 0xFF7FAE}};

    /** jump: rings of light spread out over the ground from where you left it */
    public static void shock(int style, double x, double y, double z) {
        Shot s = new Shot();
        s.style = Mth.clamp(style, 0, 5); s.born = System.currentTimeMillis(); s.x = x; s.y = y + 0.05; s.z = z;
        if (shots.size() < 12) shots.add(s);
    }

    /** swing: a curved slash hanging in the air where your arm swept, fading away */
    public static void slash(int style, double ox, double oy, double oz, double lx, double ly, double lz) {
        // the plane of the swing: right = look x up, up2 = right x look
        double rxl = ly * 0 - lz * 1, ryl = lz * 0 - lx * 0, rzl = lx * 1 - ly * 0;   // look x (0,1,0)
        double m = Math.sqrt(rxl * rxl + rzl * rzl);
        if (m < 1e-4) return;
        rxl /= m; rzl /= m;
        double u2x = ryl * lz - rzl * ly, u2y = rzl * lx - rxl * lz, u2z = rxl * ly - ryl * lx;
        double um = Math.sqrt(u2x * u2x + u2y * u2y + u2z * u2z);
        if (um < 1e-4) return;
        u2x /= um; u2y /= um; u2z /= um;
        Shot s = new Shot();
        s.slash = true; s.style = Mth.clamp(style, 0, 5); s.born = System.currentTimeMillis();
        s.arc = new ArrayList<>();
        double cx = ox + lx * 1.5, cy = oy + ly * 1.5, cz = oz + lz * 1.5;
        for (int i = 0; i <= 22; i++) {
            double k = i / 22.0 * 2 - 1;                    // -1 .. 1 across the swing
            double a = -k * 1.0, bulge = 0.35 * (1 - k * k);   // a curve like a crescent
            double dx = rxl * (-k * 1.05) + u2x * (k * 0.5 + bulge * 0.6) + lx * (-bulge * 0.3);
            double dy = ryl * (-k * 1.05) + u2y * (k * 0.5 + bulge * 0.6) + ly * (-bulge * 0.3);
            double dz = rzl * (-k * 1.05) + u2z * (k * 0.5 + bulge * 0.6) + lz * (-bulge * 0.3);
            s.arc.add(v(cx + dx, cy + dy, cz + dz));
        }
        if (shots.size() < 12) shots.add(s);
    }

    // colours of every effect: start / end
    private static final int[][] COL = {
            {0xFFFFFF, 0xFFD34D}, {0xFFFFFF, 0xFFD34D},   // 1 stars
            {0xFF9FC0, 0xFF3D70},                          // 2 hearts
            {0xFFE27A, 0xFF3B14},                          // 3 flames
            {0xFFFFFF, 0x8FD0FF},                          // 4 snow
            {0xB38CFF, 0x5CF2FF},                          // 5 magic
            {0xFF8FD0, 0x6FE0FF},                          // 6 music
            {0xC8FBFF, 0x1F7FFF},                          // 7 soul
            {0xFFD2E2, 0xFF7FAE},                          // 8 petals
            {0xFFFFFF, 0x6FA8FF},                          // 9 electric
            {0x7CF3E0, 0x4D8DFF},                          // 10 glow
    };

    private Aura() {}

    public static void init() {
        try {
            HudElementRegistry.addFirst(Identifier.fromNamespaceAndPath(ChromeClientMod.MOD_ID, "aura"), Aura::render);
            // the game's own view and projection of this frame: the effects follow the real camera (third person, FOV, bobbing)
            WorldRenderEvents.END_EXTRACTION.register(ctx -> {
                try {
                    Matrix4f vm = new Matrix4f(ctx.viewMatrix()), pm = new Matrix4f(ctx.cullProjectionMatrix());
                    Vector3f tr = new Vector3f();
                    vm.getTranslation(tr);
                    viewHasPos = tr.length() > 1.5f;   // some versions bake the camera position into it, some leave it out
                    viewM = vm; projM = pm; matStamp = System.currentTimeMillis();
                } catch (Throwable t) {
                    viewM = null;
                }
            });
        } catch (Throwable t) {
            LOG.error("effects could not start", t);
        }
    }

    /** called every tick for every player whose effect is on (you, or another player using the mod) */
    public static void update(net.minecraft.world.entity.player.Player p, int e, int where, float amount) {
        A a = map.computeIfAbsent(p.getId(), k -> new A());
        a.e = e; a.where = where; a.amount = amount; a.last = System.currentTimeMillis();
    }

    // ---------------------------------------------------------------- camera
    private static double camX, camY, camZ, fx, fy, fz, rx, rz, ux, uy, uz, tanHalf, aspect;
    private static int sw, sh;
    private static volatile Matrix4f viewM, projM;
    private static volatile long matStamp;
    private static volatile boolean viewHasPos;
    private static boolean useMat;
    private static int budget = 0;   // how many lines may still be drawn this frame (keeps the frame rate up whatever is on)

    private static float[] proj(double x, double y, double z) {
        if (useMat) {
            Matrix4f vm = viewM, pm = projM;
            if (vm != null && pm != null) {
                Vector4f v = viewHasPos ? new Vector4f((float) x, (float) y, (float) z, 1f)
                        : new Vector4f((float) (x - camX), (float) (y - camY), (float) (z - camZ), 1f);
                vm.transform(v);
                pm.transform(v);
                if (v.w < 0.3f) return null;
                return new float[]{sw / 2f * (1f + v.x / v.w), sh / 2f * (1f - v.y / v.w), v.w};
            }
        }
        double dx = x - camX, dy = y - camY, dz = z - camZ;
        double cz = dx * fx + dy * fy + dz * fz;
        if (cz < 0.3) return null;
        double cx = dx * rx + dz * rz, cy = dx * ux + dy * uy + dz * uz;
        return new float[]{(float) (sw / 2.0 * (1.0 + cx / (cz * tanHalf * aspect))),
                (float) (sh / 2.0 * (1.0 - cy / (cz * tanHalf))), (float) cz};
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        try {
            draw(g, delta);
        } catch (Throwable t) {
            if (!logged) { logged = true; LOG.error("effects failed to draw", t); }
        }
    }

    private static void draw(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        var pl = mc.player;
        if ((map.isEmpty() && shots.isEmpty()) || pl == null || mc.level == null || mc.options.hideGui) return;
        float pt = delta.getGameTimeDeltaPartialTick(false);
        double ex = Mth.lerp(pt, pl.xo, pl.getX()), ey = Mth.lerp(pt, pl.yo, pl.getY()) + pl.getEyeHeight(), ez = Mth.lerp(pt, pl.zo, pl.getZ());
        float yawDeg = Mth.lerp(pt, pl.yRotO, pl.getYRot()) + (Freelook.active ? Freelook.yaw : 0f);
        float pitchDeg = Mth.clamp(Mth.lerp(pt, pl.xRotO, pl.getXRot()) + (Freelook.active ? Freelook.pitch : 0f), -90f, 90f);
        String cam = mc.options.getCameraType().name();
        double yw = Math.toRadians(yawDeg), pc = Math.toRadians(pitchDeg);
        double lfx = -Math.sin(yw) * Math.cos(pc), lfy = -Math.sin(pc), lfz = Math.cos(yw) * Math.cos(pc);
        camX = ex; camY = ey; camZ = ez;
        if (cam.contains("BACK")) { camX -= lfx * 4.0; camY -= lfy * 4.0; camZ -= lfz * 4.0; }
        else if (cam.contains("FRONT")) { camX += lfx * 4.0; camY += lfy * 4.0; camZ += lfz * 4.0; yawDeg += 180f; pitchDeg = -pitchDeg; }
        double yaw = Math.toRadians(yawDeg), pitch = Math.toRadians(pitchDeg);
        fx = -Math.sin(yaw) * Math.cos(pitch); fy = -Math.sin(pitch); fz = Math.cos(yaw) * Math.cos(pitch);
        rx = -Math.cos(yaw); rz = -Math.sin(yaw);
        ux = -rz * fy; uy = rz * fx - rx * fz; uz = rx * fy;
        sw = mc.getWindow().getGuiScaledWidth(); sh = mc.getWindow().getGuiScaledHeight();
        tanHalf = Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0);
        aspect = sw / (double) Math.max(1, sh);
        Matrix4f vmat = viewM, pmat = projM;
        useMat = false;
        try {   // the game's own camera: real position (third person, wall pushback) and real turn
            var cm = mc.gameRenderer.getMainCamera();
            var cp = cm.position();
            var q = cm.rotation();
            Vector3f f = new Vector3f(0, 0, -1).rotate(q), r = new Vector3f(1, 0, 0).rotate(q), u = new Vector3f(0, 1, 0).rotate(q);
            camX = cp.x; camY = cp.y; camZ = cp.z;
            fx = f.x; fy = f.y; fz = f.z;
            rx = r.x; rz = r.z;
            ux = u.x; uy = u.y; uz = u.z;
        } catch (Throwable ignored) { }
        if (pmat != null && System.currentTimeMillis() - matStamp < 500 && pmat.m11() > 0.05f) tanHalf = 1.0 / pmat.m11();

        budget = 170;
        long now = System.currentTimeMillis();
        float t = (now % 1000000L) / 1000f;
        for (Iterator<Map.Entry<Integer, A>> it = map.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            A a = en.getValue();
            if (now - a.last > 1500) { it.remove(); continue; }
            if (now - a.last > 150) continue;   // effect switched off: it just stops
            Entity ent = mc.level.getEntity(en.getKey());
            if (ent == null) continue;
            double px = Mth.lerp(pt, ent.xo, ent.getX()), py = Mth.lerp(pt, ent.yo, ent.getY()), pz = Mth.lerp(pt, ent.zo, ent.getZ());
            if (!visible(mc, pl, px, py, pz)) continue;   // behind a wall: not drawn
            int e = Mth.clamp(a.e, 1, 10);
            int c0 = COL[e][0], c1 = COL[e][1];
            float sc = Mth.clamp(0.75f + a.amount * 0.25f, 0.7f, 1.5f);
            if (a.where == 1) wings(g, ent, px, py, pz, c0, c1, t, sc);
            else around(g, e, px, py, pz, c0, c1, t, sc);
        }
        for (Iterator<Shot> it = shots.iterator(); it.hasNext(); ) {
            Shot sh2 = it.next();
            float age = (now - sh2.born) / 1000f;
            if (age > (sh2.slash ? 0.38f : 0.85f)) { it.remove(); continue; }
            if (sh2.slash) drawSlash(g, sh2, age); else drawShock(g, sh2, age);
        }
    }

    /** can the camera see this player? looks at a few points from feet to above the head, so only a hidden one is skipped */
    private static boolean visible(Minecraft mc, net.minecraft.world.entity.Entity viewer, double px, double py, double pz) {
        try {
            var from = new net.minecraft.world.phys.Vec3(camX, camY, camZ);
            for (double h : new double[]{1.0, 1.7, 0.3, 2.4}) {
                var to = new net.minecraft.world.phys.Vec3(px, py + h, pz);
                var hit = mc.level.clip(new net.minecraft.world.level.ClipContext(from, to,
                        net.minecraft.world.level.ClipContext.Block.VISUAL, net.minecraft.world.level.ClipContext.Fluid.NONE, viewer));
                if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) return true;
            }
            return false;
        } catch (Throwable t) {
            return true;
        }
    }

    private static void drawShock(GuiGraphics g, Shot s, float age) {
        int c0 = STYLE_COL[s.style][0], c1 = STYLE_COL[s.style][1];
        float k = age / 0.85f;
        float ease = 1f - (1f - k) * (1f - k);   // fast at first, then slows
        for (int ring = 0; ring < 3; ring++) {
            float kk = Mth.clamp(ease - ring * 0.12f, 0f, 1f);
            if (kk <= 0f) continue;
            double r = 0.3 + kk * (2.6 - ring * 0.5);
            float fade = (1f - kk) * (1f - ring * 0.25f);
            poly(g, circle(s.x, s.y, s.z, r, 28), true, 0.07 * (1f - kk * 0.6f), mix(c0, c1, ring / 2f), c1, fade, fade);
        }
        for (int i = 0; i < 10; i++) {   // streaks flying outwards
            double a = i * Math.PI * 2 / 10 + 0.3;
            double r0 = 0.3 + ease * 2.0, r1 = r0 + 0.35 * (1f - k);
            line(g, v(s.x + Math.cos(a) * r0, s.y, s.z + Math.sin(a) * r0), v(s.x + Math.cos(a) * r1, s.y + 0.04, s.z + Math.sin(a) * r1), 0.05, c0, 1f - k);
        }
        orb(g, v(s.x, s.y + 0.1 + ease * 0.3, s.z), 0.12 * (1f - k), c0, 1f - k);
    }

    private static void drawSlash(GuiGraphics g, Shot s, float age) {
        int c0 = STYLE_COL[s.style][0], c1 = STYLE_COL[s.style][1];
        float k = age / 0.38f;
        float reveal = Mth.clamp(k * 3.2f, 0f, 1f);   // the slash is drawn quickly from one end to the other
        int n = s.arc.size() - 1;
        int upto = Math.round(reveal * n);
        float fade = 1f - Mth.clamp((k - 0.25f) / 0.75f, 0f, 1f);
        for (int i = 0; i < upto; i++) {
            float pos = i / (float) n;
            float taper = (float) Math.sin(Math.PI * Mth.clamp(pos, 0.04f, 0.96f));   // thin at the ends, thick in the middle
            float behind = Mth.clamp((upto - i) / (float) n * 2.2f, 0f, 1f);          // the head of the swing is brightest
            line(g, s.arc.get(i), s.arc.get(i + 1), 0.015 + 0.075 * taper, mix(c0, c1, 1f - behind), fade * (0.35f + 0.65f * behind));
        }
        if (upto > 0 && upto <= n) orb(g, s.arc.get(upto), 0.06, c0, fade);
    }

    // ---------------------------------------------------------------- drawing helpers
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

    /** one glowing line between two world points; ww = its thickness in blocks */
    private static void line(GuiGraphics g, double[] a, double[] b, double ww, int rgb, float alpha) {
        if (budget <= 0) return;
        float[] p = proj(a[0], a[1], a[2]), q = proj(b[0], b[1], b[2]);
        if (p == null || q == null) return;
        float near = Mth.clamp((Math.min(p[2], q[2]) - 0.5f) / 1.0f, 0f, 1f);
        alpha *= near;
        if (alpha < 0.02f) return;
        float dx = q[0] - p[0], dy = q[1] - p[1];
        float len = (float) Math.hypot(dx, dy);
        if (len < 1.2f || len > 900f) return;
        budget--;
        float w = (float) Mth.clamp(ww * (sh / 2.0) / (((p[2] + q[2]) / 2.0) * tanHalf), 1.0, 7.0);
        g.pose().pushMatrix();
        g.pose().translate(p[0], p[1]);
        g.pose().rotate((float) Math.atan2(dy, dx));
        if (w >= 1.6f && budget > 90) Ui.rect(g, -w * 0.5f, -w * 1.7f, len + w, w * 3.4f, col(rgb, alpha * 0.17f));   // soft glow
        Ui.rect(g, -w * 0.3f, -w / 2f, len + w * 0.6f, w, col(mix(rgb, 0xFFFFFF, 0.35f), alpha));
        g.pose().popMatrix();
    }

    /** a smooth line through points, colour and fade going from the first to the last */
    private static void poly(GuiGraphics g, List<double[]> pts, boolean closed, double ww, int c0, int c1, float a0, float a1) {
        int n = pts.size();
        int segs = closed ? n : n - 1;
        for (int i = 0; i < segs; i++) {
            float k = i / (float) Math.max(1, segs - 1);
            line(g, pts.get(i), pts.get((i + 1) % n), ww, mix(c0, c1, k), Mth.lerp(k, a0, a1));
        }
    }

    private static double[] v(double x, double y, double z) {
        return new double[]{x, y, z};
    }

    /** a point on the camera-facing plane: right * a + up * b */
    private static double[] plane(double cx, double cy, double cz, double a, double b) {
        return v(cx + rx * a + ux * b, cy + uy * b, cz + rz * a + uz * b);
    }

    private static double[] rot(double x, double y, double z, double ax, double ay) {
        double y1 = y * Math.cos(ax) - z * Math.sin(ax), z1 = y * Math.sin(ax) + z * Math.cos(ax);
        return v(x * Math.cos(ay) + z1 * Math.sin(ay), y1, -x * Math.sin(ay) + z1 * Math.cos(ay));
    }

    private static List<double[]> circle(double cx, double cy, double cz, double r, int n) {
        List<double[]> o = new ArrayList<>(n);
        for (int i = 0; i < n; i++) o.add(v(cx + Math.cos(i * Math.PI * 2 / n) * r, cy, cz + Math.sin(i * Math.PI * 2 / n) * r));
        return o;
    }

    private static void sparkle(GuiGraphics g, double[] c, double size, int rgb, float alpha) {
        line(g, plane(c[0], c[1], c[2], -size, 0), plane(c[0], c[1], c[2], size, 0), 0.03, rgb, alpha);
        line(g, plane(c[0], c[1], c[2], 0, -size), plane(c[0], c[1], c[2], 0, size), 0.03, rgb, alpha);
    }

    private static void orb(GuiGraphics g, double[] c, double rr, int rgb, float alpha) {
        float[] p = proj(c[0], c[1], c[2]);
        if (p == null) return;
        float r = (float) Mth.clamp(rr * (sh / 2.0) / (p[2] * tanHalf), 1f, 30f);
        alpha *= Mth.clamp((p[2] - 0.5f) / 1.0f, 0f, 1f);
        Ui.rrect(g, p[0] - r * 1.8f, p[1] - r * 1.8f, r * 3.6f, r * 3.6f, r * 1.8f, col(rgb, alpha * 0.16f));
        Ui.rrect(g, p[0] - r * 0.7f, p[1] - r * 0.7f, r * 1.4f, r * 1.4f, r * 0.7f, col(mix(rgb, 0xFFFFFF, 0.7f), alpha));
    }

    // ---------------------------------------------------------------- the effects around you
    private static void around(GuiGraphics g, int e, double x, double y, double z, int c0, int c1, float t, float sc) {
        switch (e) {
            case 1 -> {   // a ring of stars joined by thin lines, turning slowly around you
                int n = 6;
                List<double[]> pts = new ArrayList<>();
                for (int i = 0; i < n; i++) {
                    double a = i * Math.PI * 2 / n + t * 0.9;
                    pts.add(v(x + Math.cos(a) * 0.95 * sc, y + 1.0 + Math.sin(t * 1.3 + i * 2) * 0.3, z + Math.sin(a) * 0.95 * sc));
                }
                for (int i = 0; i < n; i++) {
                    line(g, pts.get(i), pts.get((i + 1) % n), 0.018, c1, 0.45f);
                    line(g, pts.get(i), pts.get((i + 2) % n), 0.012, c0, 0.2f);
                }
                for (int i = 0; i < n; i++) {
                    float tw = 0.65f + 0.35f * (float) Math.sin(t * 6 + i * 1.9);
                    sparkle(g, pts.get(i), 0.16 * tw, mix(c0, c1, i / (float) n), 1f);
                    orb(g, pts.get(i), 0.035, c0, 1f);
                }
            }
            case 2 -> {   // a big glowing heart above your head, beating
                double beat = 1.0 + 0.12 * Math.pow(Math.max(0, Math.sin(t * 5)), 2);
                double cy = y + 2.55 + Math.sin(t * 1.6) * 0.06;
                for (int layer = 0; layer < 3; layer++) {
                    double k = 0.024 * sc * beat * (1.0 - layer * 0.26);
                    List<double[]> pts = new ArrayList<>();
                    for (int i = 0; i < 32; i++) {
                        double u = i * Math.PI * 2 / 32;
                        double hx = 16 * Math.pow(Math.sin(u), 3);
                        double hy = 13 * Math.cos(u) - 5 * Math.cos(2 * u) - 2 * Math.cos(3 * u) - Math.cos(4 * u);
                        pts.add(plane(x, cy, z, hx * k, hy * k));
                    }
                    poly(g, pts, true, 0.04 - layer * 0.008, mix(c0, c1, layer / 2f), c1, 1f - layer * 0.28f, 1f - layer * 0.28f);
                }
                orb(g, plane(x, cy, z, -0.12 * sc, 0.2 * sc), 0.04, 0xFFFFFF, 0.9f);
            }
            case 3 -> {   // a ring of fire around your feet
                int n = 26;
                for (int i = 0; i < n; i++) {
                    double a = i * Math.PI * 2 / n + t * 0.5;
                    double h = (0.3 + 0.5 * (0.5 + 0.5 * Math.sin(t * 7 + i * 1.7)) * (0.6 + 0.4 * Math.sin(t * 3.1 + i * 0.7))) * sc;
                    double r = 0.85 * sc;
                    double bx = x + Math.cos(a) * r, bz = z + Math.sin(a) * r;
                    double sway = Math.sin(t * 5 + i) * 0.08;
                    List<double[]> flame = new ArrayList<>();
                    flame.add(v(bx, y + 0.04, bz));
                    flame.add(v(bx - Math.cos(a) * 0.05 + sway, y + 0.04 + h * 0.55, bz - Math.sin(a) * 0.05));
                    flame.add(v(bx - Math.cos(a) * 0.1 - sway, y + 0.04 + h, bz - Math.sin(a) * 0.1));
                    poly(g, flame, false, 0.075, c0, c1, 1f, 0.2f);
                }
                poly(g, circle(x, y + 0.04, z, 0.85 * sc, 26), true, 0.05, 0xFFB030, c1, 0.9f, 0.9f);
            }
            case 4 -> {   // a spinning snowflake over your head
                double cy = y + 2.5 + Math.sin(t * 1.5) * 0.05, len = 0.5 * sc;
                for (int arm = 0; arm < 6; arm++) {
                    double a = arm * Math.PI / 3 + t * 0.6;
                    double ca = Math.cos(a), sa = Math.sin(a);
                    double[] tip = plane(x, cy, z, ca * len, sa * len);
                    line(g, v(x, cy, z), tip, 0.04, mix(c0, c1, 0.3f), 1f);
                    for (int b = 1; b <= 2; b++) {
                        double at = len * (0.35 + 0.3 * b), bl = 0.2 * sc * (1.1 - 0.35 * b);
                        double[] base = plane(x, cy, z, ca * at, sa * at);
                        for (int side = -1; side <= 1; side += 2) {
                            double ba = a + side * Math.PI / 3;
                            line(g, base, plane(x, cy, z, ca * at + Math.cos(ba) * bl, sa * at + Math.sin(ba) * bl), 0.03, c1, 0.85f);
                        }
                    }
                    orb(g, tip, 0.03, c0, 1f);
                }
                orb(g, v(x, cy, z), 0.06, c0, 1f);
            }
            case 5 -> {   // a rune circle on the ground with a turning star inside
                double r = 1.0 * sc, yy = y + 0.05;
                poly(g, circle(x, yy, z, r, 30), true, 0.04, c0, c1, 1f, 1f);
                poly(g, circle(x, yy, z, r * 0.78, 30), true, 0.03, c1, c0, 0.8f, 0.8f);
                for (int i = 0; i < 16; i++) {   // marks between the rings
                    double a = i * Math.PI * 2 / 16 - t * 0.4;
                    line(g, v(x + Math.cos(a) * r * 0.78, yy, z + Math.sin(a) * r * 0.78), v(x + Math.cos(a) * r, yy, z + Math.sin(a) * r), 0.025, c0, i % 2 == 0 ? 0.9f : 0.4f);
                }
                for (int tri = 0; tri < 2; tri++) {   // hexagram
                    List<double[]> pts = new ArrayList<>();
                    for (int i = 0; i < 3; i++) {
                        double a = i * Math.PI * 2 / 3 + tri * Math.PI / 3 + t * 0.7;
                        pts.add(v(x + Math.cos(a) * r * 0.78, yy, z + Math.sin(a) * r * 0.78));
                    }
                    poly(g, pts, true, 0.03, c0, c1, 0.9f, 0.9f);
                }
                for (int i = 0; i < 4; i++) {   // light rising off the circle
                    double a = i * Math.PI / 2 + t * 0.7, h = ((t * 0.5 + i * 0.25) % 1.0);
                    line(g, v(x + Math.cos(a) * r * 0.9, yy + h * 1.6, z + Math.sin(a) * r * 0.9),
                            v(x + Math.cos(a) * r * 0.9, yy + h * 1.6 + 0.35, z + Math.sin(a) * r * 0.9), 0.03, c1, (float) (1 - h));
                }
            }
            case 6 -> {   // a ribbon of music winding up around you, with notes riding it
                List<double[]> pts = new ArrayList<>();
                for (int i = 0; i <= 50; i++) {
                    double s = i / 50.0, a = s * Math.PI * 4 + t * 2;
                    pts.add(v(x + Math.cos(a) * 0.7 * sc, y + 0.1 + s * 2.0, z + Math.sin(a) * 0.7 * sc));
                }
                poly(g, pts, false, 0.04, c0, c1, 0.15f, 1f);
                var mc = Minecraft.getInstance();
                for (int k = 0; k < 3; k++) {
                    double s = (t * 0.22 + k / 3.0) % 1.0, a = s * Math.PI * 4 + t * 2;
                    float[] p = proj(x + Math.cos(a) * 0.7 * sc, y + 0.1 + s * 2.0, z + Math.sin(a) * 0.7 * sc);
                    if (p == null) continue;
                    float k2 = (float) Mth.clamp((sh / 2.0) * 0.22 / (p[2] * tanHalf) / 8f, 0.6, 3.0);
                    g.pose().pushMatrix();
                    g.pose().translate(p[0], p[1]);
                    g.pose().scale(k2, k2);
                    g.drawString(mc.font, "♪", -3, -4, col(mix(c0, c1, (float) s), (float) Math.min(1, (1 - s) * 3)), false);
                    g.pose().popMatrix();
                }
            }
            case 7 -> {   // two blue flames spiralling up
                for (int strand = 0; strand < 2; strand++) {
                    List<double[]> pts = new ArrayList<>();
                    for (int i = 0; i <= 40; i++) {
                        double s = i / 40.0, a = s * Math.PI * 3 + t * 2.4 + strand * Math.PI;
                        double r = (0.65 - 0.3 * s) * sc;
                        pts.add(v(x + Math.cos(a) * r, y + 0.05 + s * 2.1, z + Math.sin(a) * r));
                    }
                    poly(g, pts, false, 0.07, c1, c0, 1f, 0.1f);
                }
            }
            case 8 -> {   // petals drifting down in a spiral
                List<double[]> pts = new ArrayList<>();
                for (int i = 0; i <= 44; i++) {
                    double s = i / 44.0, a = s * Math.PI * 3 + t * 0.8;
                    pts.add(v(x + Math.cos(a) * 0.8 * sc, y + 2.1 - s * 1.9, z + Math.sin(a) * 0.8 * sc));
                }
                poly(g, pts, false, 0.012, c0, c1, 0.3f, 0.3f);
                for (int k = 0; k < 12; k++) {
                    double s = (t * 0.12 + k / 12.0) % 1.0, a = s * Math.PI * 3 + t * 0.8;
                    double[] c = v(x + Math.cos(a) * 0.8 * sc, y + 2.1 - s * 1.9, z + Math.sin(a) * 0.8 * sc);
                    double ang = t * 2 + k * 1.3;
                    double[] d = v(Math.cos(ang) * 0.09, Math.sin(ang * 0.7) * 0.05, Math.sin(ang) * 0.09);
                    line(g, v(c[0] - d[0], c[1] - d[1], c[2] - d[2]), v(c[0] + d[0], c[1] + d[1], c[2] + d[2]), 0.075, mix(c0, c1, k / 12f), (float) Math.min(1, Math.min(s * 6, (1 - s) * 6)));
                }
            }
            case 9 -> {   // lightning arcing from above you down to the ground around you
                long q = (long) (t * 12);
                float flash = 1f - (t * 12 - q) * 0.6f;
                Random r = new Random(q * 7919L);
                for (int bolt = 0; bolt < 3; bolt++) {
                    double a0 = r.nextDouble() * Math.PI * 2, a1 = a0 + (r.nextDouble() - 0.5) * 1.6;
                    double[] s = v(x + Math.cos(a0) * 0.45, y + 2.3, z + Math.sin(a0) * 0.45);
                    double[] e2 = v(x + Math.cos(a1) * 1.1 * sc, y + 0.05, z + Math.sin(a1) * 1.1 * sc);
                    List<double[]> pts = new ArrayList<>();
                    pts.add(s);
                    for (int i = 1; i < 7; i++) {
                        double k = i / 7.0;
                        pts.add(v(Mth.lerp(k, s[0], e2[0]) + (r.nextDouble() - 0.5) * 0.35, Mth.lerp(k, s[1], e2[1]) + (r.nextDouble() - 0.5) * 0.15, Mth.lerp(k, s[2], e2[2]) + (r.nextDouble() - 0.5) * 0.35));
                    }
                    pts.add(e2);
                    poly(g, pts, false, 0.05, c0, c1, flash, flash);
                    orb(g, e2, 0.07, c1, flash);
                }
            }
            default -> {   // three rings turning around each other, like a gyroscope, with a glowing core
                double cy = y + 1.0;
                for (int ring = 0; ring < 3; ring++) {
                    double ax = t * (1.1 - ring * 0.3) + ring * 1.05, ay = t * (ring * 0.6 - 0.4) + ring;
                    List<double[]> pts = new ArrayList<>();
                    for (int i = 0; i < 26; i++) {
                        double a = i * Math.PI * 2 / 26;
                        double[] q = rot(Math.cos(a) * 0.95 * sc, Math.sin(a) * 0.95 * sc, 0, ax, ay);
                        pts.add(v(x + q[0], cy + q[1], z + q[2]));
                    }
                    poly(g, pts, true, 0.035, mix(c0, c1, ring / 2f), mix(c1, c0, ring / 2f), 0.95f, 0.5f);
                }
                orb(g, v(x, cy, z), 0.09, c0, 0.9f);
            }
        }
    }

    // ---------------------------------------------------------------- behind your feet
    private static void trail(GuiGraphics g, A a, double px, double py, double pz, int c0, int c1, float t) {
        if (a.hist.size() < 3) return;
        List<double[]> h = new ArrayList<>(a.hist);
        h.set(h.size() - 1, v(px, py, pz));
        for (int lane = 0; lane < 2; lane++) {
            List<double[]> pts = new ArrayList<>();
            for (int i = h.size() - 1; i >= 0; i -= 2) {   // every second point is plenty for a smooth ribbon
                double[] p = h.get(i);
                double wave = Math.sin(i * 0.7 - t * 8 + lane * 2) * 0.06;
                pts.add(0, v(p[0] + wave, p[1] + 0.15 + lane * 0.5, p[2] + wave));
            }
            poly(g, pts, false, 0.065 - lane * 0.015, c1, c0, 0f, 1f - lane * 0.3f);
        }
    }

    // ---------------------------------------------------------------- wings made of light
    private static void wings(GuiGraphics g, Entity ent, double x, double y, double z, int c0, int c1, float t, float sc) {
        Minecraft mc = Minecraft.getInstance();
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double yaw = Math.toRadians(ent.getYRot());
        if (ent instanceof net.minecraft.world.entity.LivingEntity le) yaw = Math.toRadians(Mth.rotLerp(pt, le.yBodyRotO, le.yBodyRot));
        double backX = Math.sin(yaw), backZ = -Math.cos(yaw), rightX = -Math.cos(yaw), rightZ = -Math.sin(yaw);
        double flap = Math.sin(t * 3.0) * 0.22;
        final int N = 8;
        for (int side = -1; side <= 1; side += 2) {
            double[] root = v(x + backX * 0.26, y + 1.1, z + backZ * 0.26);
            double[][] ends = new double[N][];
            for (int k = 0; k < N; k++) {
                // feather k: from the top edge of the wing (k=0) down to the short ones at the bottom
                double th = 1.25 - k * 0.2 + flap;
                double len = (k == 0 ? 1.2 : 1.55 - k * 0.12) * sc;
                double[] p = root;
                double[] prev = root;
                for (int j = 1; j <= 3; j++) {
                    double s = j / 3.0, thj = th - 0.45 * s * (0.4 + k * 0.1);   // feathers bend downwards
                    double dx = rightX * side * Math.cos(thj) + backX * (0.35 + 0.05 * k), dy = Math.sin(thj), dz = rightZ * side * Math.cos(thj) + backZ * (0.35 + 0.05 * k);
                    double m = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    p = v(prev[0] + dx / m * len / 3, prev[1] + dy / m * len / 3, prev[2] + dz / m * len / 3);
                    float fade = k == 0 ? 1f : 0.85f - 0.2f * (j - 1);
                    line(g, prev, p, (k == 0 ? 0.07 : 0.15) * (1 - 0.35 * s), mix(c0, c1, k / (float) (N - 1)), fade);
                    prev = p;
                }
                ends[k] = p;
            }
            // the edge of the wing: a thin bright line through the feather tips
            for (int k = 0; k + 1 < N; k++) line(g, ends[k], ends[k + 1], 0.03, c0, 0.9f);
        }
    }
}
