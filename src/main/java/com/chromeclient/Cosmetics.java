package com.chromeclient;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Wardrobe: hats, capes, wings and particle effects. Every item is a list of small coloured boxes, drawn flat in the menu
 * and as real 3D pieces on your player (see CosmeticLayer). Box units are half pixels: 2 units = 1 Minecraft pixel.
 * The head spans x 8..24, y 0..16, z -8..8 (front is negative z); the body spans x 8..24, y 16..40, z -4..4.
 */
public final class Cosmetics {
    public static final String[] CATS = {"Hats", "Capes", "Wings"};
    private static String[] capeNames() {
        String[] mine = {"None", "Midnight", "Snow", "Twisted", "Cherry"};
        String[] extra = {"Aurora", "Ember", "Abyss", "Galaxy"};   // newer ones go after the others so saved picks keep their number
        String[] all = new String[mine.length + CapeArt.NAMES.length + extra.length];
        System.arraycopy(mine, 0, all, 0, mine.length);
        System.arraycopy(CapeArt.NAMES, 0, all, mine.length, CapeArt.NAMES.length);
        System.arraycopy(extra, 0, all, mine.length + CapeArt.NAMES.length, extra.length);
        return all;
    }
    private static final String[] CAPES = capeNames();

    public static final String[][] ITEMS = {
            {"None", "Halo", "Cat ears", "Bunny ears", "Void ring"},
            CAPES,
            {"None", "Angel", "Void bat", "Butterfly", "Crystal", "Phoenix"},
            {"None", "Sparkles", "Hearts", "Flames", "Snow", "Magic", "Notes", "Soul fire", "Petals", "Electric", "Glow"},
    };
    public static final String[] WHERE = {"Around you", "Wings"};

    /** chosen item per category (0 = none); for Effects it is the particle type */
    public static final int[] chosen = new int[4];
    public static boolean effectOn = true;       // master switch for the particles
    public static int effectWhere = 0;
    public static float effectAmount = 0.5f;

    private Cosmetics() {}

    // ---------------- saving ----------------
    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("chrome-cosmetics.json");
    }

    public static void load() {
        try {
            Path f = file();
            if (!Files.isRegularFile(f)) return;
            JsonObject o = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            for (int i = 0; i < CATS.length; i++) {
                String k = CATS[i].toLowerCase();
                if (o.has(k)) chosen[i] = Math.max(0, Math.min(ITEMS[i].length - 1, o.get(k).getAsInt()));
            }
            if (o.has("effectOn")) effectOn = o.get("effectOn").getAsBoolean();
            if (o.has("effectWhere")) effectWhere = Math.max(0, Math.min(WHERE.length - 1, o.get("effectWhere").getAsInt()));
            if (o.has("effectAmount")) effectAmount = Math.max(0.1f, Math.min(1f, o.get("effectAmount").getAsFloat()));
            CosmeticSync.fromJson(o);
        } catch (Exception ignored) {
            // defaults
        }
    }

    public static void save() {
        try {
            JsonObject o = new JsonObject();
            for (int i = 0; i < CATS.length; i++) o.addProperty(CATS[i].toLowerCase(), chosen[i]);
            o.addProperty("effectOn", effectOn);
            o.addProperty("effectWhere", effectWhere);
            o.addProperty("effectAmount", effectAmount);
            CosmeticSync.toJson(o);
            Files.writeString(file(), o.toString());
        } catch (Exception ignored) {
            // can't save: still works this session
        }
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(Cosmetics::tick);
    }

    // ---------------- the pieces ----------------
    /** one box: x, y, w, h in half pixels (like the menu drawing), d = thickness, z = where it starts (front is negative) */
    public record Box(int x, int y, int w, int h, float d, float z, int color) {}

    private static final class L {
        final List<Box> l = new ArrayList<>();

        L a(int x, int y, int w, int h, float d, float z, int c) {
            l.add(new Box(x, y, w, h, d, z, c));
            return this;
        }

        /** the box and its mirror image on the other side of the middle (x = 16) */
        L m(int x, int y, int w, int h, float d, float z, int c) {
            a(x, y, w, h, d, z, c);
            return a(32 - x - w, y, w, h, d, z, c);
        }
    }

    private static int sh(int c, float f) {
        int r = Math.min(255, Math.round(((c >> 16) & 0xFF) * f)), g = Math.min(255, Math.round(((c >> 8) & 0xFF) * f)), b = Math.min(255, Math.round((c & 0xFF) * f));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    /** flat menu preview of hat i, taken from the real 3D planks (see HatData) */
    public static List<Box> hatBoxes(int i) {
        List<Box> out = new ArrayList<>();
        for (int[] b : HatData.preview(i)) out.add(new Box(b[0], b[1], b[2], b[3], 1f, -5f, b[4]));
        return out;
    }

    /** cape strips from top to bottom, each a little darker/lighter: a smooth gradient made of boxes */
    private static void gradientCape(L o, int top, int bottom, int rows) {
        int h = 44 / rows;
        for (int k = 0; k < rows; k++) {
            o.a(7, 16 + k * h, 18, h, 1.2f, 4.6f, mix(top, bottom, k / (float) (rows - 1)));
        }
    }

    public static List<Box> capeBoxes(int i) {
        L o = new L();
        switch (i) {
            case 1 -> {   // midnight with stars
                gradientCape(o, 0xFF1B1B4A, 0xFF0A0A22, 9);
                int[][] st = {{9, 22}, {20, 26}, {13, 33}, {22, 40}, {10, 46}, {17, 52}, {23, 55}, {12, 57}};
                for (int[] s : st) o.a(s[0], s[1], 2, 2, 0.5f, 5.9f, 0xFFFFF4B0);
                o.a(6, 59, 20, 2, 1.6f, 4.5f, 0xFF8B6CFF);
                o.m(6, 16, 2, 44, 1.6f, 4.5f, 0xFF8B6CFF);
            }
            case 2 -> {   // snow with fur collar
                gradientCape(o, 0xFFFFFFFF, 0xFFB5D3F0, 9);
                o.a(5, 14, 22, 6, 2.2f, 4.3f, 0xFFFFFFFF);
                for (int k = 0; k < 6; k++) o.a(6 + k * 3 + (k % 2), 19, 2, 2, 1.0f, 5.5f, 0xFFDCE8F6);
                o.a(6, 59, 20, 2, 1.6f, 4.5f, 0xFF8FB4E8);
            }
            case 3 -> {   // twisted: the real cape, pixel for pixel
                o.a(6, 16, 8, 3, 1.2f, 4.6f, 0xFF152E44);
                o.a(14, 16, 4, 3, 1.2f, 4.6f, 0xFFFDFBA6);
                o.a(18, 16, 8, 3, 1.2f, 4.6f, 0xFF152E44);
                o.a(6, 19, 2, 3, 1.2f, 4.6f, 0xFF517E67);
                o.a(8, 19, 2, 3, 1.2f, 4.6f, 0xFFA0A39C);
                o.a(10, 19, 4, 3, 1.2f, 4.6f, 0xFFCFDDA0);
                o.a(14, 19, 4, 3, 1.2f, 4.6f, 0xFFFDFBA6);
                o.a(18, 19, 4, 3, 1.2f, 4.6f, 0xFFCFDDA0);
                o.a(22, 19, 2, 3, 1.2f, 4.6f, 0xFFA0A39C);
                o.a(24, 19, 2, 3, 1.2f, 4.6f, 0xFF517E67);
                o.a(6, 22, 2, 3, 1.2f, 4.6f, 0xFF152E44);
                o.a(8, 22, 6, 3, 1.2f, 4.6f, 0xFF0D1925);
                o.a(14, 22, 4, 3, 1.2f, 4.6f, 0xFFA0A39C);
                o.a(18, 22, 6, 3, 1.2f, 4.6f, 0xFF0D1925);
                o.a(24, 22, 2, 3, 1.2f, 4.6f, 0xFF152E44);
                o.a(6, 25, 2, 3, 1.2f, 4.6f, 0xFF152E44);
                o.a(8, 25, 6, 3, 1.2f, 4.6f, 0xFF0D1925);
                o.a(14, 25, 4, 3, 1.2f, 4.6f, 0xFFFDFBA6);
                o.a(18, 25, 6, 3, 1.2f, 4.6f, 0xFF0D1925);
                o.a(24, 25, 2, 3, 1.2f, 4.6f, 0xFF152E44);
                o.a(6, 28, 4, 3, 1.2f, 4.6f, 0xFF517E67);
                o.a(10, 28, 4, 3, 1.2f, 4.6f, 0xFFA0A39C);
                o.a(14, 28, 4, 3, 1.2f, 4.6f, 0xFFFDFBA6);
                o.a(18, 28, 4, 3, 1.2f, 4.6f, 0xFFA0A39C);
                o.a(22, 28, 4, 3, 1.2f, 4.6f, 0xFF517E67);
                o.a(6, 31, 2, 3, 1.2f, 4.6f, 0xFF152E44);
                o.a(8, 31, 6, 3, 1.2f, 4.6f, 0xFF0D1925);
                o.a(14, 31, 4, 3, 1.2f, 4.6f, 0xFFA0A39C);
                o.a(18, 31, 6, 3, 1.2f, 4.6f, 0xFF0D1925);
                o.a(24, 31, 2, 3, 1.2f, 4.6f, 0xFF152E44);
                o.a(6, 34, 2, 3, 1.2f, 4.6f, 0xFF152E44);
                o.a(8, 34, 6, 3, 1.2f, 4.6f, 0xFF0D1925);
                o.a(14, 34, 4, 3, 1.2f, 4.6f, 0xFFFDFBA6);
                o.a(18, 34, 6, 3, 1.2f, 4.6f, 0xFF0D1925);
                o.a(24, 34, 2, 3, 1.2f, 4.6f, 0xFF152E44);
                o.a(6, 37, 2, 3, 1.2f, 4.6f, 0xFF152E44);
                o.a(8, 37, 2, 3, 1.2f, 4.6f, 0xFF0D1925);
                o.a(10, 37, 2, 3, 1.2f, 4.6f, 0xFF517E67);
                o.a(12, 37, 2, 3, 1.2f, 4.6f, 0xFFCFDDA0);
                o.a(14, 37, 4, 3, 1.2f, 4.6f, 0xFFFDFBA6);
                o.a(18, 37, 2, 3, 1.2f, 4.6f, 0xFFCFDDA0);
                o.a(20, 37, 2, 3, 1.2f, 4.6f, 0xFF517E67);
                o.a(22, 37, 2, 3, 1.2f, 4.6f, 0xFF0D1925);
                o.a(24, 37, 2, 3, 1.2f, 4.6f, 0xFF152E44);
                o.a(6, 40, 2, 3, 1.2f, 4.6f, 0xFF152E44);
                o.a(8, 40, 6, 3, 1.2f, 4.6f, 0xFF0D1925);
                o.a(14, 40, 4, 3, 1.2f, 4.6f, 0xFFA0A39C);
                o.a(18, 40, 6, 3, 1.2f, 4.6f, 0xFF0D1925);
                o.a(24, 40, 2, 3, 1.2f, 4.6f, 0xFF152E44);
                o.a(6, 43, 2, 3, 1.2f, 4.6f, 0xFF152E44);
                o.a(8, 43, 6, 3, 1.2f, 4.6f, 0xFF0D1925);
                o.a(14, 43, 4, 3, 1.2f, 4.6f, 0xFFFDFBA6);
                o.a(18, 43, 6, 3, 1.2f, 4.6f, 0xFF0D1925);
                o.a(24, 43, 2, 3, 1.2f, 4.6f, 0xFF152E44);
                o.a(6, 46, 2, 3, 1.2f, 4.6f, 0xFF152E44);
                o.a(8, 46, 4, 3, 1.2f, 4.6f, 0xFF0D1925);
                o.a(12, 46, 2, 3, 1.2f, 4.6f, 0xFFA0A39C);
                o.a(14, 46, 4, 3, 1.2f, 4.6f, 0xFFFDFBA6);
                o.a(18, 46, 2, 3, 1.2f, 4.6f, 0xFFA0A39C);
                o.a(20, 46, 4, 3, 1.2f, 4.6f, 0xFF0D1925);
                o.a(24, 46, 2, 3, 1.2f, 4.6f, 0xFF152E44);
                o.a(6, 49, 2, 3, 1.2f, 4.6f, 0xFF152E44);
                o.a(8, 49, 6, 3, 1.2f, 4.6f, 0xFF0D1925);
                o.a(14, 49, 4, 3, 1.2f, 4.6f, 0xFFA0A39C);
                o.a(18, 49, 6, 3, 1.2f, 4.6f, 0xFF0D1925);
                o.a(24, 49, 2, 3, 1.2f, 4.6f, 0xFF152E44);
                o.a(6, 52, 4, 3, 1.2f, 4.6f, 0xFF152E44);
                o.a(10, 52, 2, 3, 1.2f, 4.6f, 0xFF0D1925);
                o.a(12, 52, 2, 3, 1.2f, 4.6f, 0xFF152E44);
                o.a(14, 52, 4, 3, 1.2f, 4.6f, 0xFFCFDDA0);
                o.a(18, 52, 2, 3, 1.2f, 4.6f, 0xFF0D1925);
                o.a(20, 52, 6, 3, 1.2f, 4.6f, 0xFF152E44);
                o.a(6, 55, 8, 3, 1.2f, 4.6f, 0xFF152E44);
                o.a(14, 55, 6, 3, 1.2f, 4.6f, 0xFF0D1925);
                o.a(20, 55, 6, 3, 1.2f, 4.6f, 0xFF152E44);
                o.a(6, 58, 2, 3, 1.2f, 4.6f, 0xFF2A5E73);
                o.a(8, 58, 4, 3, 1.2f, 4.6f, 0xFF152E44);
                o.a(12, 58, 4, 3, 1.2f, 4.6f, 0xFF2A5E73);
                o.a(16, 58, 4, 3, 1.2f, 4.6f, 0xFF152E44);
                o.a(20, 58, 2, 3, 1.2f, 4.6f, 0xFF2A5E73);
                o.a(22, 58, 2, 3, 1.2f, 4.6f, 0xFF152E44);
                o.a(24, 58, 2, 3, 1.2f, 4.6f, 0xFF2A5E73);
                o.a(6, 61, 2, 3, 1.2f, 4.6f, 0xFF2A5E73);
                o.a(8, 61, 2, 3, 1.2f, 4.6f, 0xFF152E44);
                o.a(10, 61, 6, 3, 1.2f, 4.6f, 0xFF2A5E73);
                o.a(16, 61, 2, 3, 1.2f, 4.6f, 0xFF152E44);
                o.a(18, 61, 8, 3, 1.2f, 4.6f, 0xFF2A5E73);
            }
            case 4 -> {   // cherry
                gradientCape(o, 0xFFFFD9E6, 0xFFFF9FC2, 9);
                int[][] bl = {{9, 24}, {20, 30}, {12, 38}, {21, 45}, {10, 52}};
                for (int[] p : bl) o.a(p[0], p[1], 3, 3, 0.5f, 5.9f, 0xFFFFFFFF);
                o.a(6, 59, 20, 2, 1.6f, 4.5f, 0xFFD26E96);
            }
            case 24 -> gradientCape(o, 0xFF0A2433, 0xFF2EE8A0, 9);
            case 25 -> gradientCape(o, 0xFF4A0B05, 0xFFF08A1C, 9);
            case 26 -> gradientCape(o, 0xFF0E5A7E, 0xFF020812, 9);
            case 27 -> gradientCape(o, 0xFF0B0620, 0xFF9A6CE0, 9);
            default -> { return CapeArt.boxes(i); }
        }
        return o.l;
    }

    /** one (left) wing, as seen from behind; the right one is its mirror image. y runs down from the shoulder. */
    /** flat menu preview of both wings of wing `i`, taken from the real 3D planks (see WingPlanks) */
    public static List<Box> wingBoxes(int i) {
        List<Box> out = new ArrayList<>();
        for (int[] b : WingPlanks.preview(i)) out.add(new Box(b[0], b[1], b[2], b[3], 1f, 8f, b[4]));
        return out;
    }

    public static List<Box> mirrored(List<Box> left) {
        List<Box> out = new ArrayList<>();
        for (Box b : left) out.add(new Box(32 - b.x() - b.w(), b.y(), b.w(), b.h(), b.d(), b.z(), b.color()));
        return out;
    }

    // ---------------- drawing the menu preview ----------------
    private static void r(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + h, Ui.mul(c));
    }

    private static void flat(GuiGraphics g, List<Box> boxes) {
        List<Box> sorted = new ArrayList<>(boxes);
        sorted.sort(Comparator.comparingDouble((Box b) -> -(b.z() + b.d() / 2)));   // far ones first
        g.pose().pushMatrix();
        g.pose().scale(0.5f, 0.5f);
        for (Box b : sorted) r(g, b.x(), b.y(), b.w(), b.h(), b.color());
        g.pose().popMatrix();
    }

    /** the whole player at scale u with its top-left corner at (ox, oy) */
    public static void drawAvatar(GuiGraphics g, float ox, float oy, float u, int hat, int cape, int wings) {
        g.pose().pushMatrix();
        g.pose().translate(ox, oy);
        g.pose().scale(u, u);
        if (wings > 0) {
            flat(g, wingBoxes(wings));
        }
        if (cape > 0) flat(g, capeBoxes(cape));
        r(g, 4, 20, 4, 12, 0xFF3A2F8F); r(g, 8, 20, 4, 12, 0xFF3A2F8F);
        r(g, 4, 30, 4, 2, 0xFF55555F); r(g, 8, 30, 4, 2, 0xFF55555F);
        r(g, 4, 8, 8, 12, 0xFF1FA6A6);
        r(g, 0, 8, 4, 12, 0xFFC69C6D); r(g, 12, 8, 4, 12, 0xFFC69C6D);
        r(g, 0, 8, 4, 4, 0xFF1FA6A6); r(g, 12, 8, 4, 4, 0xFF1FA6A6);
        r(g, 4, 0, 8, 8, 0xFFC69C6D);
        r(g, 4, 0, 8, 2, 0xFF4A3322);
        r(g, 5, 4, 2, 1, 0xFFFFFFFF); r(g, 9, 4, 2, 1, 0xFFFFFFFF);
        r(g, 6, 4, 1, 1, 0xFF3B2A8F); r(g, 9, 4, 1, 1, 0xFF3B2A8F);
        if (hat > 0) flat(g, hatBoxes(hat));
        g.pose().popMatrix();
    }

    /** just the head with a hat, for the little icons */
    public static void drawHead(GuiGraphics g, float ox, float oy, float u, int hat) {
        g.pose().pushMatrix();
        g.pose().translate(ox, oy);
        g.pose().scale(u, u);
        r(g, 4, 0, 8, 8, 0xFFC69C6D);
        r(g, 4, 0, 8, 2, 0xFF4A3322);
        r(g, 5, 4, 2, 1, 0xFFFFFFFF); r(g, 9, 4, 2, 1, 0xFFFFFFFF);
        r(g, 6, 4, 1, 1, 0xFF3B2A8F); r(g, 9, 4, 1, 1, 0xFF3B2A8F);
        if (hat > 0) flat(g, hatBoxes(hat));
        g.pose().popMatrix();
    }

    /** a small preview of item `idx` of category `cat`, centred in the box (x, y, w, h) */
    public static void icon(GuiGraphics g, int cat, int idx, int x, int y, int w, int h) {
        if (idx == 0) {   // "None": a thin cross
            int cx = x + w / 2, cy = y + h / 2;
            Ui.rect(g, cx - 7, cy - 1, 14, 2, Ui.MUTED);
            return;
        }
        if (cat == 3) {   // effect: a few dots in its colour
            int c = effectColor(idx);
            long t = System.currentTimeMillis() / 90;
            for (int k = 0; k < 7; k++) {
                double a = k * 0.9 + t * 0.05;
                float rx = (float) Math.cos(a * 1.3) * (w * 0.32f), ry = (float) Math.sin(a * 1.7) * (h * 0.3f);
                int s = 2 + (k % 3);
                Ui.rect(g, x + w / 2f + rx - s / 2f, y + h / 2f + ry - s / 2f, s, s, c);
            }
            return;
        }
        if (cat == 1) {   // the cape itself: the back panel of its texture, as it hangs behind you
            int ph = h, pw = ph * 10 / 16;
            if (pw > w) { pw = w; ph = pw * 16 / 10; }
            int dx = x + (w - pw) / 2, dy = y + (h - ph) / 2;
            g.fill(dx - 1, dy - 1, dx + pw + 1, dy + ph + 1, 0x66000000);
            var capeTex = net.minecraft.resources.Identifier.fromNamespaceAndPath(ChromeClientMod.MOD_ID, "textures/entity/cape_" + idx + ".png");
            for (int sgm = 0; sgm < 4; sgm++) {   // the cape is 4 stacked pieces; each piece's back face sits 5 rows below the last
                int y0 = dy + ph * sgm / 4, y1 = dy + ph * (sgm + 1) / 4;
                g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, capeTex,
                        dx, y0, 144f, (sgm * 5 + 1) * 12f, pw, y1 - y0, 120, 48, 768, 384);
            }
            return;
        }
        if (cat == 0) {
            float u = Math.min(w / 18f, h / 20f);
            drawHead(g, x + (w - 16 * u) / 2f, y + h / 2f - 1 * u, u, idx);
        } else {
            float u = Math.min(w / (cat == 2 ? 36f : 26f), h / 36f);
            drawAvatar(g, x + (w - 16 * u) / 2f, y + (h - 32 * u) / 2f, u, 0, cat == 1 ? idx : 0, cat == 2 ? idx : 0);
        }
    }

    public static int effectColor(int e) {
        return switch (e) {
            case 1 -> 0xFFF4F7FF;
            case 2 -> 0xFFFF5C8A;
            case 3 -> 0xFFFF9429;
            case 4 -> 0xFFDDF1FF;
            case 5 -> 0xFFB48CFF;
            case 6 -> 0xFF7DF05A;
            case 7 -> 0xFF4AD7E8;
            case 8 -> 0xFFFFB7D5;
            case 9 -> 0xFF8FD3FF;
            default -> 0xFFFFF08A;
        };
    }

    // ---------------- particles around you ----------------
    private static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        ClientLevel lv = mc.level;
        if (true || !effectOn || chosen[3] == 0 || p == null || lv == null || mc.isPaused()) return;
        emit(p, chosen[3], effectWhere, effectAmount, lv);
    }

    /** one tick of particles for one player (you, or another player using the mod) */
    public static void emit(net.minecraft.world.entity.player.Player p, int e, int where, float amount, ClientLevel lv) {
        if (true || e <= 0 || p.isInvisible()) return;   // cosmetic particles are removed
        Aura.update(p, e, where, amount);   // the effect is one shape, drawn by Aura

    }
}
