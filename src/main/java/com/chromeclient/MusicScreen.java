package com.chromeclient;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The music drop-down: the "now singing" pill at the top centre drops down into a small player (play / pause, previous,
 * next, seek, volume). Open it with the music key (J) or from the Right Shift menu. It controls the YT Music mod.
 */
public class MusicScreen extends Screen {
    private static final int W = 270, H = 108;

    private final Screen parent;
    private boolean wasDown = true;   // true at first so a held button doesn't count as a click
    private int dragging = 0;         // 1 = seek bar, 2 = volume bar
    private float openT = 0f;
    private long lastNanos = System.nanoTime();
    private final float[] hov = new float[6];
    private int lastPos = -1;
    private long lastPosNanos = 0;
    private int volume = -1;

    public MusicScreen(Screen parent) {
        super(Component.literal("Music"));
        this.parent = parent;
    }

    private static void icon(GuiGraphics g, int kind, int x, int y, int col) {
        switch (kind) {
            case 0 -> {   // previous
                g.fill(x, y, x + 2, y + 8, col);
                for (int i = 0; i < 4; i++) {
                    int cx = x + 2 + 2 * (3 - i);
                    g.fill(cx, y + i, cx + 2, y + 8 - i, col);
                }
            }
            case 1 -> {   // play
                for (int i = 0; i < 4; i++) g.fill(x + 2 * i, y + i, x + 2 * i + 2, y + 8 - i, col);
            }
            case 2 -> {   // pause
                g.fill(x + 1, y, x + 3, y + 8, col);
                g.fill(x + 5, y, x + 7, y + 8, col);
            }
            default -> {  // next
                for (int i = 0; i < 4; i++) g.fill(x + 2 * i, y + i, x + 2 * i + 2, y + 8 - i, col);
                g.fill(x + 8, y, x + 10, y + 8, col);
            }
        }
    }

    private static String fmt(int s) {
        s = Math.max(0, s);
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        Minecraft mc = Minecraft.getInstance();
        boolean down = EditHudScreen.leftDown(mc);
        boolean click = down && !wasDown;
        wasDown = down;
        if (!down) dragging = 0;

        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        float ease = 1f - (float) Math.exp(-dt * 18f);
        openT += (1f - openT) * (1f - (float) Math.exp(-dt * 11f));
        Ui.fade = Mth.clamp(openT * 1.4f, 0f, 1f);

        YtBridge.Track t = YtBridge.current();
        boolean has = t != null && t.title() != null && !t.title().isBlank();
        int pos = t == null ? 0 : t.position();
        if (pos != lastPos) {
            lastPos = pos;
            lastPosNanos = now;
        }
        double estPos = has ? pos + (t.playing() ? Math.min(1.0, (now - lastPosNanos) / 1e9) : 0.0) : 0;
        int dur = has ? Math.max(1, t.duration()) : 1;

        // it drops down out of the top centre
        int w = Math.round(Mth.lerp(openT, 190f, W));
        int h = Math.round(Mth.lerp(openT, 18f, H));
        int x = (width - w) / 2, y = 6;
        Ui.rect(g, x, y, w, h, 0xE60E0B16);
        Ui.rect(g, x + 1, y + 1, w - 2, 1, 0x22FFFFFF);

        if (openT > 0.55f) {
            int px = x + 12, rw = w - 24;
            // title + artist
            String head = !has ? "Nothing playing" : (t.artist() == null || t.artist().isBlank() ? t.title() : t.title() + " - " + t.artist());
            while (head.length() > 1 && font.width(head) > rw) head = head.substring(0, head.length() - 1);
            g.drawString(font, Component.literal(head).withStyle(ChatFormatting.BOLD), px, y + 9, Ui.mul(0xFFFFFFFF), false);

            // the line being sung, filling in as it goes
            String line = Lyrics.lineText();
            if (has && !line.isBlank()) {
                double ls = Lyrics.lineStart(), le = Lyrics.lineEnd();
                double prog = le > ls ? Mth.clamp((Lyrics.songTimeNow() - ls) / (le - ls), 0.0, 1.0) : 1.0;
                while (line.length() > 1 && font.width(line) > rw) line = line.substring(0, line.length() - 1);
                int k = (int) Math.round(line.length() * prog);
                String done = line.substring(0, k);
                g.drawString(font, done, px, y + 22, Ui.mul(0xFFB79BFF), false);
                g.drawString(font, line.substring(k), px + font.width(done), y + 22, Ui.mul(0x80FFFFFF), false);
            } else if (!has) {
                Ui.text(g, font, "Open the full player to pick a song", px, y + 22, Ui.MUTED);
            }

            // seek bar
            int by = y + 40;
            boolean hSeek = Ui.in(mouseX, mouseY, px - 2, by - 5, rw + 4, 12);
            if (click && hSeek && has) dragging = 1;
            if (dragging == 1 && down && has) {
                YtBridge.seek(Mth.clamp((mouseX - px) / (double) rw, 0.0, 1.0));
            }
            float f = (float) Mth.clamp(estPos / dur, 0.0, 1.0);
            Ui.rect(g, px, by, rw, 3, Ui.OFF);
            Ui.rect(g, px, by, Math.max(3f, rw * f), 3, 0xFFB79BFF);
            Ui.rect(g, px + rw * f - 3, by - 2, 6, 7, Ui.TEXT);
            Ui.text(g, font, fmt((int) estPos), px, by + 7, Ui.MUTED);
            String total = fmt(has ? t.duration() : 0);
            Ui.text(g, font, total, px + rw - font.width(total), by + 7, Ui.MUTED);

            // buttons: previous, play / pause, next
            int cy = y + 74, cx = x + w / 2;
            int[] bx = {cx - 38, cx - 11, cx + 16};
            for (int i = 0; i < 3; i++) {
                boolean hv = Ui.in(mouseX, mouseY, bx[i], cy, 22, 20);
                hov[i] += ((hv ? 1f : 0f) - hov[i]) * ease;
                Ui.rect(g, bx[i], cy, 22, 20, Ui.lerp(Ui.SURFACE, Ui.HOVER, hov[i]));
                int ic = i == 1 ? (has && t.playing() ? 2 : 1) : (i == 0 ? 0 : 3);
                icon(g, ic, bx[i] + (ic == 3 ? 6 : 8), cy + 6, Ui.mul(0xFFFFFFFF));
                if (click && hv) {
                    if (!YtBridge.browserRunning()) YtBridge.ensureBrowser();
                    if (i == 0) YtBridge.prev();
                    else if (i == 1) YtBridge.togglePlay();
                    else YtBridge.next();
                }
            }

            // volume (left) and the full player (right)
            int vx = px, vw = 56;
            if (volume < 0) volume = YtBridge.volume();
            boolean hVol = Ui.in(mouseX, mouseY, vx - 2, cy + 2, vw + 4, 16);
            if (click && hVol) dragging = 2;
            if (dragging == 2 && down) {
                volume = (int) Math.round(Mth.clamp((mouseX - vx) / (double) vw, 0.0, 1.0) * 100);
                YtBridge.setVolume(volume);
            }
            Ui.rect(g, vx, cy + 9, vw, 3, Ui.OFF);
            Ui.rect(g, vx, cy + 9, Math.max(3f, vw * volume / 100f), 3, Ui.ACCENT);
            Ui.rect(g, vx + vw * volume / 100f - 3, cy + 7, 6, 7, Ui.TEXT);
            Ui.text(g, font, volume + "%", vx + vw + 6, cy + 6, Ui.MUTED);

            int fw = 62, fx = px + rw - fw;
            boolean hFull = Ui.in(mouseX, mouseY, fx, cy + 2, fw, 16);
            hov[3] += ((hFull ? 1f : 0f) - hov[3]) * ease;
            Ui.button(g, font, "Full player", fx, cy + 2, fw, 16, hov[3], false);
            if (click && hFull) YtBridge.open();
        }
        Ui.fade = 1f;
    }

    /** no dimming: you see the game behind the drop-down */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
