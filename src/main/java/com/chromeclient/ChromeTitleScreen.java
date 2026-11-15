package com.chromeclient;

import com.mojang.realmsclient.RealmsMainScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Chrome Client's main menu, laid out like a modern client menu: blurred panorama, name in the middle with the
 * main buttons under it, a Quickplay list of your saved servers on the left, your account on the right, small buttons
 * top right. The buttons are normal (invisible) game buttons that we draw over, so clicks and keys work like normal.
 */
public final class ChromeTitleScreen extends Screen {
    private static final int PANEL = 0xA6323236, PANEL_HOVER = 0xC04A4A50, EDGE = 0x33FFFFFF, MUTE = 0xFFB4B4B8;

    private static final class B {
        String label, sub;
        int kind;                 // 0 plain button, 1 server card, 2 small square, 3 header (no click)
        int icon;
        int x, y, w, h;
        Button hit;
        float hov;
    }

    private final List<B> bs = new ArrayList<>();
    private long last = System.nanoTime();
    private int cx, hh, sw;

    public ChromeTitleScreen() {
        super(Component.literal("Chrome Client"));
    }

    private B add(int kind, String label, String sub, int icon, int x, int y, int w, int h, Runnable r) {
        B b = new B();
        b.kind = kind; b.label = label; b.sub = sub; b.icon = icon;
        b.x = x; b.y = y; b.w = w; b.h = h;
        if (r != null) {
            b.hit = Button.builder(Component.literal(label), btn -> r.run()).bounds(x, y, w, h).build();
            b.hit.setAlpha(0f);   // invisible: we draw it ourselves, the game still handles the clicks
            addRenderableWidget(b.hit);
        }
        bs.add(b);
        return b;
    }

    @Override
    protected void init() {
        bs.clear();
        cx = width / 2;
        hh = Math.max(18, Math.round(height * 0.0345f));         // button height
        int gap = Math.max(3, Math.round(height * 0.0125f));
        int bw = Math.max(150, Math.round(width * 0.2f));
        int y0 = Math.round(height * 0.503f);
        add(0, "Singleplayer", null, 0, cx - bw / 2, y0, bw, hh, () -> minecraft.setScreen(new SelectWorldScreen(this)));
        add(0, "Multiplayer", null, 1, cx - bw / 2, y0 + (hh + gap), bw, hh, () -> minecraft.setScreen(new JoinMultiplayerScreen(this)));
        add(0, "Realms", null, 2, cx - bw / 2, y0 + 2 * (hh + gap), bw, hh, () -> minecraft.setScreen(new RealmsMainScreen(this)));
        int half = (bw - gap) / 2;
        add(0, "Settings", null, 3, cx - bw / 2, y0 + 3 * (hh + gap), half, hh, () -> minecraft.setScreen(new OptionsScreen(this, minecraft.options)));
        add(0, "Quit", null, 4, cx - bw / 2 + half + gap, y0 + 3 * (hh + gap), bw - half - gap, hh, () -> minecraft.stop());

        // top right: small square buttons
        int sq = Math.max(16, Math.round(width * 0.02f));
        int m = Math.max(6, Math.round(width * 0.008f));
        add(2, "Fullscreen", null, 5, width - m - 2 * sq - 4, m, sq, sq, () -> minecraft.getWindow().toggleFullScreen());
        add(2, "Close", null, 4, width - m - sq, m, sq, sq, () -> minecraft.stop());

        // left: Quickplay header and your saved servers
        int lw = Math.max(120, Math.round(width * 0.135f));
        int lx = Math.max(8, Math.round(width * 0.031f));
        int qy = Math.round(height * 0.324f);
        add(3, "Quickplay", null, 6, lx, qy, lw, hh, null);
        int ch = Math.max(24, Math.round(height * 0.05f));
        try {
            ServerList sl = new ServerList(minecraft);
            sl.load();
            int n = Math.min(3, sl.size());
            for (int i = 0; i < n; i++) {
                ServerData d = sl.get(i);
                add(1, d.name, d.ip, 0, lx, qy + hh + gap + i * (ch + gap), lw, ch, () ->
                        ConnectScreen.startConnecting(this, minecraft, ServerAddress.parseString(d.ip), d, false, null));
            }
        } catch (Throwable ignored) {
            // no server list: the left side just stays with the header
        }

        // right: account and Host World (no skin render here)
        int rw = lw;
        int rx = width - lx - rw;
        add(3, minecraft.getUser().getName(), null, 7, rx, y0, rw, hh, null);
        add(0, "Host World", null, 8, rx, y0 + hh + gap, rw, hh, () -> minecraft.setScreen(new SelectWorldScreen(this)));
    }

    // ---- tiny icons drawn with rectangles ----

    private static void icon(GuiGraphics g, int kind, float x, float y, int c) {
        switch (kind) {
            case 0 -> { Ui.rrect(g, x + 2.5f, y, 4, 4, 2, c); Ui.rrect(g, x, y + 5, 9, 4, 2, c); }                    // person
            case 1 -> { Ui.rrect(g, x + 4.5f, y, 3, 3, 1.5f, c); Ui.rrect(g, x + 3, y + 4, 6, 5, 2, c);
                        Ui.rrect(g, x, y + 1, 3, 3, 1.5f, c); Ui.rrect(g, x - 1, y + 5, 4, 4, 2, c); }                   // two people
            case 2 -> { Ui.rring(g, x, y, 9, 9, 4.5f, c); g.fill((int) x + 4, (int) y + 1, (int) x + 5, (int) y + 8, c);
                        g.fill((int) x + 1, (int) y + 4, (int) x + 8, (int) y + 5, c); }                                 // globe
            case 3 -> { Ui.rring(g, x, y, 9, 9, 4.5f, c); Ui.rrect(g, x + 3, y + 3, 3, 3, 1.5f, c); }                   // gear
            case 4 -> { for (int i = 0; i < 8; i++) { g.fill((int) x + i, (int) y + i, (int) x + i + 1, (int) y + i + 1, c);
                        g.fill((int) x + 7 - i, (int) y + i, (int) x + 8 - i, (int) y + i + 1, c); } }                    // X
            case 5 -> { int ix = (int) x, iy = (int) y;                                                                  // fullscreen corners
                        g.fill(ix, iy, ix + 3, iy + 1, c); g.fill(ix, iy, ix + 1, iy + 3, c);
                        g.fill(ix + 6, iy, ix + 9, iy + 1, c); g.fill(ix + 8, iy, ix + 9, iy + 3, c);
                        g.fill(ix, iy + 8, ix + 3, iy + 9, c); g.fill(ix, iy + 6, ix + 1, iy + 9, c);
                        g.fill(ix + 6, iy + 8, ix + 9, iy + 9, c); g.fill(ix + 8, iy + 6, ix + 9, iy + 9, c); }
            case 6 -> { int ix = (int) x, iy = (int) y;                                                                  // quickplay arrow
                        g.fill(ix, iy + 4, ix + 7, iy + 5, c);
                        g.fill(ix + 5, iy + 2, ix + 6, iy + 3, c); g.fill(ix + 5, iy + 6, ix + 6, iy + 7, c);
                        g.fill(ix + 6, iy + 3, ix + 7, iy + 4, c); g.fill(ix + 6, iy + 5, ix + 7, iy + 6, c); }
            default -> { Ui.rring(g, x, y, 9, 9, 4.5f, c); }                                                              // ring
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);   // blurred panorama
        Ui.fade = 1f;
        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - last) / 1e9f);
        last = now;
        float ease = 1f - (float) Math.exp(-dt * 16f);

        // the logo and the name in the middle
        float lg = Math.max(22f, height * 0.04f);
        float lx0 = cx - lg / 2f, ly0 = height * 0.325f;
        Ui.rring(g, lx0, ly0, lg, lg, lg / 2f, 0xFFFFFFFF);
        Ui.rring(g, lx0 + 1, ly0 + 1, lg - 2, lg - 2, lg / 2f - 1, 0x55FFFFFF);
        Ui.rrect(g, cx - lg * 0.22f, ly0 + lg * 0.28f, lg * 0.44f, lg * 0.44f, lg * 0.22f, 0xFFFFFFFF);

        String title = "CHROME CLIENT";
        float s = Math.max(1.8f, Math.min(3.2f, height * 0.0045f));
        int track = 1;                                                // a little space between the letters
        float tw = 0;
        for (int i = 0; i < title.length(); i++) tw += font.width(Component.literal(String.valueOf(title.charAt(i))).withStyle(net.minecraft.ChatFormatting.BOLD)) + track;
        tw -= track;
        g.pose().pushMatrix();
        g.pose().translate(cx - tw * s / 2f, height * 0.425f);
        g.pose().scale(s, s);
        float px = 0;
        for (int i = 0; i < title.length(); i++) {
            Component ch = Component.literal(String.valueOf(title.charAt(i))).withStyle(net.minecraft.ChatFormatting.BOLD);
            g.drawString(font, ch, Math.round(px), 0, 0xFFFFFFFF, false);
            px += font.width(ch) + track;
        }
        g.pose().popMatrix();

        for (B b : bs) {
            boolean h = b.hit != null && b.hit.isHoveredOrFocused();
            b.hov += ((h ? 1f : 0f) - b.hov) * ease;
            int fill = Ui.lerp(PANEL, PANEL_HOVER, b.hov);
            Ui.rrect(g, b.x, b.y, b.w, b.h, 4, fill);
            Ui.rring(g, b.x, b.y, b.w, b.h, 4, EDGE);
            int ty = b.y + (b.h - 8) / 2;
            switch (b.kind) {
                case 0 -> {    // icon + label in the middle
                    int lw2 = font.width(b.label) + 9 + 5;
                    int x0 = b.x + (b.w - lw2) / 2;
                    icon(g, b.icon, x0, b.y + (b.h - 9) / 2f, 0xFFFFFFFF);
                    g.drawString(font, b.label, x0 + 14, ty, 0xFFFFFFFF, false);
                }
                case 1 -> {    // server card: letter badge, name, address, arrow
                    int bsz = b.h - 12;
                    Ui.rrect(g, b.x + 6, b.y + 6, bsz, bsz, 4, 0xFF2F6BFF);
                    String ini = b.label.isEmpty() ? "?" : b.label.substring(0, 1).toUpperCase();
                    g.drawString(font, ini, b.x + 6 + (bsz - font.width(ini)) / 2, b.y + 6 + (bsz - 8) / 2, 0xFFFFFFFF, false);
                    int tx = b.x + 6 + bsz + 7, maxW = b.w - (tx - b.x) - 16;
                    Ui.textFit(g, font, b.label, tx, b.y + b.h / 2 - 9, maxW, 0xFFFFFFFF);
                    Ui.textFit(g, font, b.sub, tx, b.y + b.h / 2 + 1, maxW, MUTE);
                    g.drawString(font, ">", b.x + b.w - 10, ty, MUTE, false);
                }
                case 2 -> icon(g, b.icon, b.x + (b.w - 9) / 2f, b.y + (b.h - 9) / 2f, 0xFFFFFFFF);
                default -> {   // header / account: icon, label, little arrow on the right
                    icon(g, b.icon, b.x + 10, b.y + (b.h - 9) / 2f, 0xFFFFFFFF);
                    Ui.textFit(g, font, b.label, b.x + 26, ty, b.w - 48, 0xFFFFFFFF);
                    g.drawString(font, "v", b.x + b.w - 12, ty, MUTE, false);
                }
            }
        }

        // bottom corners
        String ver = FabricLoader.getInstance().getModContainer("chromeclient")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
        Ui.rring(g, 10, height - 13, 9, 9, 4.5f, 0xFFFFFFFF);
        g.drawString(font, Component.literal("CHROME CLIENT").withStyle(net.minecraft.ChatFormatting.BOLD), 24, height - 12, 0xFFFFFFFF, false);
        int vx = 24 + font.width("CHROME CLIENT") + 8;
        g.drawString(font, "Fabric 1.21.11  " + ver, vx, height - 12, MUTE, false);
        String c = "Copyright Mojang AB. Do not distribute!";
        g.drawString(font, c, width - font.width(c) - 8, height - 12, 0xFFB0B0B0, false);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;   // like the normal main menu
    }
}
