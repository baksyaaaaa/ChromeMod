package com.chromeclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The look of the Chrome launcher: dark glass, light edges, round corners.
 * Shapes are drawn at the screen's real pixel size (not the chunky GUI pixels) with soft anti-aliased corners, so they come out smooth.
 */
final class Ui {
    static int BG = 0x8A120E22, SURFACE = 0x1FFFFFFF, HOVER = 0x3AFFFFFF, BORDER = 0x55C9B8FF;   // glass: mostly see-through
    static int ACCENT = 0xFF8B6CFF, TEXT = 0xFFFFFFFF, MUTED = 0xFFB8B8B8, OFF = 0xFF3A3F47, INK = 0xFF0B0714;

    /** the menu themes: Evolution (purple), Neon, Ocean, Rose, Light */
    static final String[] THEMES = {"Evolution", "Neon", "Ocean", "Rose", "Light"};
    static int theme = 0;

    static void setTheme(int t) {
        theme = Math.floorMod(t, THEMES.length);
        switch (theme) {
            case 1 -> { BG = 0x8A06140E; SURFACE = 0x1FFFFFFF; HOVER = 0x3AFFFFFF; BORDER = 0x5560FFB0; ACCENT = 0xFF30F0A0; TEXT = 0xFFFFFFFF; MUTED = 0xFFB8B8B8; OFF = 0xFF2F4A3F; INK = 0xFF03120B; }
            case 2 -> { BG = 0x8A0A1424; SURFACE = 0x1FFFFFFF; HOVER = 0x3AFFFFFF; BORDER = 0x5590C8FF; ACCENT = 0xFF4DA3FF; TEXT = 0xFFFFFFFF; MUTED = 0xFFB8B8B8; OFF = 0xFF2F3F54; INK = 0xFF050B16; }
            case 3 -> { BG = 0x8A220E1A; SURFACE = 0x1FFFFFFF; HOVER = 0x3AFFFFFF; BORDER = 0x55FFB8D8; ACCENT = 0xFFFF6FA8; TEXT = 0xFFFFFFFF; MUTED = 0xFFB8B8B8; OFF = 0xFF54303F; INK = 0xFF16050E; }
            case 4 -> { BG = 0xD8F3F0FB; SURFACE = 0x22000000; HOVER = 0x33000000; BORDER = 0x66503C9C; ACCENT = 0xFF6F4DFF; TEXT = 0xFF1B1730; MUTED = 0xFF5F5A78; OFF = 0xFFB9B4CF; INK = 0xFFFFFFFF; }
            default -> { BG = 0x8A120E22; SURFACE = 0x1FFFFFFF; HOVER = 0x3AFFFFFF; BORDER = 0x55C9B8FF; ACCENT = 0xFF8B6CFF; TEXT = 0xFFFFFFFF; MUTED = 0xFFB8B8B8; OFF = 0xFF3A3F47; INK = 0xFF0B0714; }
        }
    }

    /** the accent colour with a given see-through level (0..255) */
    static int accA(int alpha) {
        return (alpha << 24) | (ACCENT & 0xFFFFFF);
    }

    private Ui() {}

    /** 0..1 see-through multiplier for everything drawn (used to fade a screen in) */
    static float fade = 1f;

    static int mul(int c) {
        return fade >= 1f ? c : withAlpha(c, fade);
    }

    /** real screen pixels per GUI pixel (capped at 2 so a big GUI scale doesn't mean thousands of tiny rectangles) */
    private static double k() {
        try {
            return Math.min(2.0, Math.max(1.0, (double) Minecraft.getInstance().getWindow().getGuiScale()));
        } catch (Throwable t) {
            return 1.0;
        }
    }

    /** blend two colours (t = 0 gives a, t = 1 gives b), alpha included */
    static int lerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int out = 0;
        for (int shift = 24; shift >= 0; shift -= 8) {
            int ca = (a >>> shift) & 0xFF, cb = (b >>> shift) & 0xFF;
            out |= (Math.round(ca + (cb - ca) * t) & 0xFF) << shift;
        }
        return out;
    }

    private static int withAlpha(int c, double f) {
        int a = (int) Math.round(((c >>> 24) & 0xFF) * f);
        return (a << 24) | (c & 0xFFFFFF);
    }

    /** pixels to cut off the left/right of row dy for a round corner of radius r (all in real pixels) */
    private static double cut(int dy, int h, int r) {
        int d = dy < r ? r - dy : (dy >= h - r ? dy - (h - r) + 1 : 0);
        if (d == 0) return 0;
        double dd = d - 0.5;
        return r - Math.sqrt(Math.max(0, (double) r * r - dd * dd));
    }

    /** filled rounded rectangle. Rows that cut the same amount are merged into one rectangle, so it stays cheap to draw. */
    static void rrect(GuiGraphics g, float x, float y, float w, float h, float r, int c) {
        double k = k();
        int X = (int) Math.round(x * k), Y = (int) Math.round(y * k);
        int W = (int) Math.round(w * k), H = (int) Math.round(h * k);
        int R = Math.max(0, Math.min((int) Math.round(r * k), Math.min(W, H) / 2));
        c = mul(c);
        g.pose().pushMatrix();
        g.pose().scale((float) (1.0 / k), (float) (1.0 / k));
        if (R > 0) {
            int runStart = 0, runIn = (int) Math.ceil(cut(0, H, R));
            for (int dy = 1; dy <= R; dy++) {
                int in = dy < R ? (int) Math.ceil(cut(dy, H, R)) : -1;
                if (in != runIn) {
                    g.fill(X + runIn, Y + runStart, X + W - runIn, Y + dy, c);
                    g.fill(X + runIn, Y + H - dy, X + W - runIn, Y + H - runStart, c);
                    runStart = dy;
                    runIn = in;
                }
            }
        }
        if (H - 2 * R > 0) g.fill(X, Y + R, X + W, Y + H - R, c);
        g.pose().popMatrix();
    }

    /** the same shape as a thin outline (one real pixel wide) */
    static void rring(GuiGraphics g, float x, float y, float w, float h, float r, int c) {
        double k = k();
        int X = (int) Math.round(x * k), Y = (int) Math.round(y * k);
        int W = (int) Math.round(w * k), H = (int) Math.round(h * k);
        int R = Math.max(0, Math.min((int) Math.round(r * k), Math.min(W, H) / 2));
        c = mul(c);
        g.pose().pushMatrix();
        g.pose().scale((float) (1.0 / k), (float) (1.0 / k));
        int in0 = (int) Math.ceil(cut(0, H, R));
        g.fill(X + in0, Y, X + W - in0, Y + 1, c);
        g.fill(X + in0, Y + H - 1, X + W - in0, Y + H, c);
        for (int dy = 1; dy < R; dy++) {
            int in = (int) Math.ceil(cut(dy, H, R));
            int len = Math.max(1, (int) Math.ceil(cut(dy - 1, H, R)) - in);
            g.fill(X + in, Y + dy, X + in + len, Y + dy + 1, c);
            g.fill(X + W - in - len, Y + dy, X + W - in, Y + dy + 1, c);
            g.fill(X + in, Y + H - 1 - dy, X + in + len, Y + H - dy, c);
            g.fill(X + W - in - len, Y + H - 1 - dy, X + W - in, Y + H - dy, c);
        }
        if (H - 2 * R > 0) {
            g.fill(X, Y + R, X + 1, Y + H - R, c);
            g.fill(X + W - 1, Y + R, X + W, Y + H - R, c);
        }
        g.pose().popMatrix();
    }

    /** standard rounded shape: round enough that tiny ones turn into pills and circles */
    static void rect(GuiGraphics g, float x, float y, float w, float h, int c) {
        rrect(g, x, y, w, h, 3, c);
    }

    /** a frosted-glass panel: translucent dark fill, thin light edge, soft sheen along the top */
    static void glass(GuiGraphics g, int x, int y, int w, int h) {
        rrect(g, x, y, w, h, 5, BG);
        for (int i = 0; i < 8; i++) {
            int in = 2 + Math.max(0, 3 - i);
            g.fill(x + in, y + 1 + i, x + w - in, y + 2 + i, mul(((0x1E - i * 2) << 24) | 0xFFFFFF));
        }
        rring(g, x, y, w, h, 5, BORDER);
    }

    static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    static void text(GuiGraphics g, Font f, String s, int x, int y, int c) {
        g.drawString(f, s, x, y, mul(c), false);
    }

    /** the text cut short with "..." so it never goes outside a box maxW pixels wide */
    static String fit(Font f, String s, int maxW) {
        if (s == null) return "";
        if (maxW <= 0) return "";
        if (f.width(s) <= maxW) return s;
        int ell = f.width("...");
        String t = s;
        while (!t.isEmpty() && f.width(t) + ell > maxW) t = t.substring(0, t.length() - 1);
        return t.stripTrailing() + "...";
    }

    /** text that stays inside maxW (cut with "...") */
    static void textFit(GuiGraphics g, Font f, String s, int x, int y, int maxW, int c) {
        text(g, f, fit(f, s, maxW), x, y, c);
    }

    /** text centred inside a box of width w that never goes outside it */
    static void textCenter(GuiGraphics g, Font f, String s, int x, int y, int w, int c) {
        String t = fit(f, s, w - 4);
        text(g, f, t, x + (w - f.width(t)) / 2, y, c);
    }

    /** hover is 0..1 so the highlight can fade in and out */
    static void button(GuiGraphics g, Font f, String label, int x, int y, int w, int h, float hover, boolean primary) {
        int bg = primary ? lerp(ACCENT, 0xFFB7A3FF, hover) : lerp(SURFACE, HOVER, hover);
        rrect(g, x, y, w, h, 4, bg);
        if (!primary && hover > 0.02f) rring(g, x, y, w, h, 4, withAlpha(0x66FFFFFF, hover));
        text(g, f, label, x + (w - f.width(label)) / 2, y + (h - 8) / 2, primary ? INK : TEXT);
    }

    static void button(GuiGraphics g, Font f, String label, int x, int y, int w, int h, boolean hover, boolean primary) {
        button(g, f, label, x, y, w, h, hover ? 1f : 0f, primary);
    }
}
