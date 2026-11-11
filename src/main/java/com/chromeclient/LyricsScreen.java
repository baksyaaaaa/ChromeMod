package com.chromeclient;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;

/** Right Shift menu > Lyrics: the world lyrics (big glowing words around you) and the "now singing" pill. */
public class LyricsScreen extends Screen {
    private static final int PW = 300, PAD = 12, ROW = 22, ROW_GAP = 4;

    /** a switch (get/set) or a slider (sget/sset with min..max) */
    private record Row(String label, BooleanSupplier get, Consumer<Boolean> set,
                       DoubleSupplier sget, Consumer<Double> sset, double min, double max, String fmt) {
        static Row toggle(String label, BooleanSupplier get, Consumer<Boolean> set) {
            return new Row(label, get, set, null, null, 0, 0, null);
        }

        static Row slider(String label, DoubleSupplier get, Consumer<Double> set, double min, double max, String fmt) {
            return new Row(label, null, null, get, set, min, max, fmt);
        }
    }

    private final Screen parent;
    private boolean wasDown = true;   // true at first so a held button doesn't count as a click
    private int dragging = -1;
    private float openT = 0f;
    private long lastNanos = System.nanoTime();
    private float doneHov = 0f;
    private final float[] onAnim = new float[16];
    private boolean primed = false;
    private final List<Row> rows = new ArrayList<>();

    public LyricsScreen(Screen parent) {
        super(Component.literal("Lyrics"));
        this.parent = parent;
        rows.add(Row.toggle("World lyrics", () -> Extras.lyrics, v -> Extras.lyrics = v));
        rows.add(Row.slider("Distance", () -> Extras.lyricsDistance, v -> Extras.lyricsDistance = v.floatValue(), 3, 20, "%.1f"));
        rows.add(Row.slider("Size", () -> Extras.lyricsSize, v -> Extras.lyricsSize = v.floatValue(), 0.5, 3, "%.1f"));
        rows.add(Row.slider("Scatter", () -> Extras.lyricsScatter, v -> Extras.lyricsScatter = v.floatValue(), 0, 1, "%.1f"));
        rows.add(Row.slider("Sync offset", () -> Extras.lyricsSync, v -> Extras.lyricsSync = (float) (Math.round(v * 10) / 10.0), -3, 3, "%+.1f s"));
        rows.add(Row.toggle("Glow", () -> Extras.lyricsGlow, v -> Extras.lyricsGlow = v));
        rows.add(Row.slider("Glow strength", () -> Extras.lyricsGlowStrength, v -> Extras.lyricsGlowStrength = v.floatValue(), 0, 1, "%.1f"));
        rows.add(Row.toggle("Shadow", () -> Extras.lyricsShadow, v -> Extras.lyricsShadow = v));
        rows.add(Row.toggle("Now singing pill", () -> Extras.lyricsPill, v -> Extras.lyricsPill = v));
        rows.add(Row.toggle("Hide YT Music bar", () -> Extras.hideYtBar, v -> Extras.hideYtBar = v));
    }

    private int panelHeight() {
        return 34 + rows.size() * (ROW + ROW_GAP) + 8 + 26 + PAD;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        Minecraft mc = Minecraft.getInstance();
        int ph = panelHeight();
        float s = Math.min(1f, (height - 8f) / ph);
        int vw = Math.round(width / s), vh = Math.round(height / s);
        int mx = Math.round(mouseX / s), my = Math.round(mouseY / s);
        boolean down = EditHudScreen.leftDown(mc);
        boolean click = down && !wasDown;
        wasDown = down;
        if (!down) dragging = -1;

        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        float ease = 1f - (float) Math.exp(-dt * 18f);
        openT += (1f - openT) * (1f - (float) Math.exp(-dt * 14f));
        Ui.fade = openT;

        int px = (vw - PW) / 2, py = (vh - ph) / 2;
        int cardW = PW - 2 * PAD;
        g.pose().pushMatrix();
        g.pose().scale(s, s);

        Ui.glass(g, px, py, PW, ph);
        Ui.rect(g, px + PAD, py + 12, 3, 10, Ui.ACCENT);
        g.drawString(font, Component.literal("Lyrics").withStyle(ChatFormatting.BOLD),
                px + PAD + 9, py + 13, Ui.mul(Ui.TEXT), false);
        int y = py + 34;

        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            Ui.rect(g, px + PAD, y, cardW, ROW, Ui.SURFACE);
            Ui.text(g, font, r.label(), px + PAD + 8, y + (ROW - 8) / 2, Ui.TEXT);
            if (r.get() != null) {   // a switch
                boolean on = r.get().getAsBoolean();
                if (!primed) onAnim[i] = on ? 1f : 0f;
                onAnim[i] += ((on ? 1f : 0f) - onAnim[i]) * ease;
                boolean hov = Ui.in(mx, my, px + PAD, y, cardW, ROW);
                if (hov) Ui.rect(g, px + PAD, y, cardW, ROW, Ui.HOVER);
                float tx = px + PAD + cardW - 8 - 30, ty = y + (ROW - 16) / 2f;
                Ui.rect(g, tx, ty, 30, 16, Ui.lerp(Ui.OFF, Ui.ACCENT, onAnim[i]));
                Ui.rect(g, tx + 2 + onAnim[i] * 14, ty + 2, 12, 12, Ui.TEXT);
                if (click && hov) r.set().accept(!on);
            } else {                 // a slider
                int tx = px + PAD + 96, tw = cardW - 96 - 52, ty = y + ROW / 2 - 2;
                boolean hov = Ui.in(mx, my, tx - 4, y, tw + 8, ROW);
                if (click && hov) dragging = i;
                if (dragging == i && down) {
                    double f = Mth.clamp((mx - tx) / (double) tw, 0.0, 1.0);
                    r.sset().accept(r.min() + f * (r.max() - r.min()));
                }
                double v = r.sget().getAsDouble();
                float f = (float) Mth.clamp((v - r.min()) / (r.max() - r.min()), 0.0, 1.0);
                Ui.rect(g, tx, ty, tw, 4, Ui.OFF);
                Ui.rect(g, tx, ty, Math.max(4f, tw * f), 4, Ui.ACCENT);
                Ui.rect(g, tx + tw * f - 3, y + ROW / 2f - 6, 6, 12, Ui.TEXT);
                String shown = String.format(r.fmt(), v);
                Ui.text(g, font, shown, px + PAD + cardW - 8 - font.width(shown), y + (ROW - 8) / 2, Ui.MUTED);
            }
            y += ROW + ROW_GAP;
        }
        y += 8;
        primed = true;

        boolean hDone = Ui.in(mx, my, px + PAD, y, cardW, 26);
        doneHov += ((hDone ? 1f : 0f) - doneHov) * ease;
        Ui.button(g, font, "Done", px + PAD, y, cardW, 26, doneHov, false);
        if (click && hDone) onClose();

        g.pose().popMatrix();
        Ui.fade = 1f;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, ((int) (0x28 * openT) << 24));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Extras.save();
        Minecraft.getInstance().setScreen(parent);
    }
}
