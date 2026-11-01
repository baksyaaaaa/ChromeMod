package com.chromeclient;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** The Right Shift menu, drawn in the launcher's flat dark style. Changes apply instantly and are saved when you close it. */
public class MenuScreen extends Screen {
    private static final int PW = 300, PAD = 12, GAP = 6, ROW = 22, ROW_GAP = 4;

    private record Toggle(String name, BooleanSupplier get, Consumer<Boolean> set) {}

    private final Hud hud;
    private final List<Toggle> toggles = new ArrayList<>();
    private boolean wasDown = true;   // true at first so a held button doesn't count as a click
    private boolean openEditor = false;
    // animation state: how "on" each switch is and how hovered each card is (0..1), eased every frame
    private final float[] onAnim = new float[32], hovAnim = new float[32];
    private float doneHov = 0f;
    private long lastNanos = System.nanoTime();
    private boolean primed = false;
    private int heldBtn = -1;                 // which timer +/- button is being held (they repeat while held)
    private long heldSince = 0, lastRepeat = 0;
    private float openT = 0f;   // 0..1, the menu fades in

    public MenuScreen(Hud hud) {
        super(Component.literal("Chrome Client"));
        this.hud = hud;
        Config c = Config.get();
        toggles.add(new Toggle("FPS", () -> c.fps, v -> c.fps = v));
        toggles.add(new Toggle("CPS", () -> c.cps, v -> c.cps = v));
        toggles.add(new Toggle("Ping", () -> c.ping, v -> c.ping = v));
        toggles.add(new Toggle("Coordinates", () -> c.coords, v -> c.coords = v));
        toggles.add(new Toggle("Direction", () -> c.direction, v -> c.direction = v));
        toggles.add(new Toggle("Clock", () -> c.clock, v -> c.clock = v));
        toggles.add(new Toggle("Keystrokes", () -> c.keystrokes, v -> c.keystrokes = v));
        toggles.add(new Toggle("Armor status", () -> c.armor, v -> c.armor = v));
        toggles.add(new Toggle("Zoom (hold C)", () -> c.zoom, v -> c.zoom = v));
        toggles.add(new Toggle("Auto sprint", () -> c.autoSprint, v -> c.autoSprint = v));
        toggles.add(new Toggle("Move HUD", null, null));   // not a switch: opens the editor
    }

    private int panelHeight() {
        int rows = (toggles.size() + 1) / 2;
        return 34 + rows * (ROW + ROW_GAP) + (ROW + ROW_GAP) + 2 * (ROW + ROW_GAP) + 8 + 26 + PAD;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);   // just the background
        Minecraft mc = Minecraft.getInstance();
        Config c = Config.get();

        int ph = panelHeight();
        float s = Math.min(1f, (height - 8f) / ph);
        int vw = Math.round(width / s);
        int vh = Math.round(height / s);
        int mx = Math.round(mouseX / s), my = Math.round(mouseY / s);
        boolean down = EditHudScreen.leftDown(mc);
        boolean click = down && !wasDown;
        wasDown = down;

        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        float ease = 1f - (float) Math.exp(-dt * 18f);

        openT += (1f - openT) * (1f - (float) Math.exp(-dt * 14f));
        Ui.fade = openT;

        int px = (vw - PW) / 2, py = (vh - ph) / 2;
        g.pose().pushMatrix();
        g.pose().scale(s, s);

        Ui.glass(g, px, py, PW, ph);
        Ui.rect(g, px + PAD, py + 12, 3, 10, Ui.ACCENT);
        g.drawString(font, Component.literal("Chrome Client").withStyle(ChatFormatting.BOLD),
                px + PAD + 9, py + 13, Ui.mul(Ui.TEXT), false);

        int cw = (PW - 2 * PAD - GAP) / 2;
        int y = py + 34;
        for (int i = 0; i < toggles.size(); i++) {
            Toggle t = toggles.get(i);
            int x = px + PAD + (i % 2) * (cw + GAP);
            boolean hover = Ui.in(mx, my, x, y, cw, ROW);
            hovAnim[i] += ((hover ? 1f : 0f) - hovAnim[i]) * ease;
            Ui.rect(g, x, y, cw, ROW, Ui.lerp(Ui.SURFACE, Ui.HOVER, hovAnim[i]));
            Ui.text(g, font, t.name(), x + 8, y + (ROW - 8) / 2, Ui.TEXT);
            if (t.get() == null) {   // Move HUD
                Ui.text(g, font, ">", x + cw - 8 - font.width(">") + Math.round(hovAnim[i] * 2), y + (ROW - 8) / 2, Ui.MUTED);
                if (click && hover) openEditor = true;
            } else {
                boolean on = t.get().getAsBoolean();
                if (!primed) onAnim[i] = on ? 1f : 0f;
                onAnim[i] += ((on ? 1f : 0f) - onAnim[i]) * ease;
                float a = onAnim[i];
                float tx = x + cw - 8 - 30, ty = y + (ROW - 16) / 2f;
                Ui.rrect(g, tx, ty, 30, 16, 8, Ui.lerp(0xFF4A505A, Ui.ACCENT, a));        // pill track
                float kx = tx + 2 + 14 * a;
                Ui.rrect(g, kx, ty + 2, 12, 12, 6, 0xFFFFFFFF);                            // round knob
                if (click && hover) t.set().accept(!on);
            }
            if (i % 2 == 1) y += ROW + ROW_GAP;
        }
        if (toggles.size() % 2 == 1) y += ROW + ROW_GAP;

        // text colour: a row of small swatches
        int cardW = PW - 2 * PAD;
        Ui.rect(g, px + PAD, y, cardW, ROW, Ui.SURFACE);
        Ui.text(g, font, "Text color", px + PAD + 8, y + (ROW - 8) / 2, Ui.TEXT);
        int sx = px + PAD + cardW - 8 - Config.COLORS.length * 14 - (Config.COLORS.length - 1) * 4;
        for (int i = 0; i < Config.COLORS.length; i++) {
            int bx = sx + i * 18, by = y + (ROW - 14) / 2;
            if (i == c.color) Ui.rect(g, bx - 2, by - 2, 18, 18, Ui.TEXT);
            Ui.rect(g, bx, by, 14, 14, Config.COLORS[i]);
            if (click && Ui.in(mx, my, bx - 2, by - 2, 18, 18)) c.color = i;
        }
        y += ROW + ROW_GAP;

        // pay timer: status + start/stop, then minutes and seconds with - / + (hold to repeat)
        PayTimer pt = PayTimer.get();
        Ui.rect(g, px + PAD, y, cardW, ROW, Ui.SURFACE);
        Ui.text(g, font, "Pay timer", px + PAD + 8, y + (ROW - 8) / 2, Ui.TEXT);
        String status = pt.running ? PayTimer.time(pt.remainingMs()) : (pt.finished ? "Done" : "Ready");
        Ui.text(g, font, status, px + PAD + 16 + font.width("Pay timer"), y + (ROW - 8) / 2, pt.running ? Ui.ACCENT : Ui.MUTED);
        int bx2 = px + PAD + cardW - 6 - 52, by2 = y + (ROW - 16) / 2;
        boolean hStart = Ui.in(mx, my, bx2, by2, 52, 16);
        Ui.button(g, font, pt.running ? "Stop" : "Start", bx2, by2, 52, 16, hStart, false);
        if (click && hStart) {
            if (pt.running) pt.stop();
            else pt.start(c.payTimerSeconds);
        }
        if (pt.finished && !pt.running) {
            int cx2 = bx2 - 4 - 44;
            boolean hClear = Ui.in(mx, my, cx2, by2, 44, 16);
            Ui.button(g, font, "Clear", cx2, by2, 44, 16, hClear, false);
            if (click && hClear) pt.clear();
        }
        y += ROW + ROW_GAP;

        long nowMs = System.currentTimeMillis();
        String[] names = {"Minutes", "Seconds"};
        for (int k = 0; k < 2; k++) {
            int x = px + PAD + k * (cw + GAP);
            Ui.rect(g, x, y, cw, ROW, Ui.SURFACE);
            Ui.text(g, font, names[k], x + 8, y + (ROW - 8) / 2, Ui.TEXT);
            int value = k == 0 ? c.payTimerSeconds / 60 : c.payTimerSeconds % 60;
            int plusX = x + cw - 22, valX = plusX - 26, minusX = valX - 18, by3 = y + (ROW - 16) / 2;
            String vs = String.format("%02d", value);
            Ui.text(g, font, vs, valX + (26 - font.width(vs)) / 2, y + (ROW - 8) / 2, Ui.TEXT);
            for (int d = 0; d < 2; d++) {
                int id = k * 2 + d, bxx = d == 0 ? minusX : plusX;
                boolean hov = Ui.in(mx, my, bxx, by3, 16, 16);
                Ui.button(g, font, d == 0 ? "-" : "+", bxx, by3, 16, 16, hov, false);
                boolean fire = false;
                if (click && hov) {
                    heldBtn = id;
                    heldSince = lastRepeat = nowMs;
                    fire = true;
                } else if (down && heldBtn == id && hov && nowMs - heldSince > 400 && nowMs - lastRepeat > 70) {
                    lastRepeat = nowMs;
                    fire = true;
                }
                if (fire) {
                    int step = (k == 0 ? 60 : 5) * (d == 0 ? -1 : 1);
                    c.payTimerSeconds = Math.max(5, Math.min(99999, c.payTimerSeconds + step));
                }
            }
        }
        if (!down) heldBtn = -1;
        y += ROW + ROW_GAP;

        y += 8;

        // buttons
        boolean hDone = Ui.in(mx, my, px + PAD, y, cardW, 26);
        doneHov += ((hDone ? 1f : 0f) - doneHov) * ease;
        Ui.button(g, font, "Done", px + PAD, y, cardW, 26, doneHov, false);
        if (click && hDone) onClose();

        g.pose().popMatrix();
        Ui.fade = 1f;
        primed = true;

        if (openEditor) {
            openEditor = false;
            Config.save();
            mc.setScreen(new EditHudScreen(hud));
        }
    }

    /** No blur, just a light dim. */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, ((int) (0x38 * openT) << 24));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Config.save();
        super.onClose();
    }
}
