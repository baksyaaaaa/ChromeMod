package com.chromeclient;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Right Shift menu > Buddy: switch it on or off, set its skin and name, and keep presets of buddies to switch between. */
public class BuddyScreen extends Screen {
    private static final int PW = 300, PAD = 12, ROW = 22, ROW_GAP = 4;

    private final Screen parent;
    private boolean wasDown = true;   // true at first so a held button doesn't count as a click
    private float openT = 0f;
    private long lastNanos = System.nanoTime();
    private float doneHov = 0f, onAnim = -1f;
    private String msg = "";
    private long msgUntil = 0;
    private boolean presetOpen = false;       // the presets dropdown
    private int ddX, ddY, ddW, ddH;           // where its list was drawn last frame (so clicks on it don't hit what is under it)

    public BuddyScreen(Screen parent) {
        super(Component.literal("Buddy"));
        this.parent = parent;
    }

    private int panelHeight() {
        return 34 + 5 * (ROW + ROW_GAP) + 8 + 26 + PAD;
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
        boolean rawClick = click;
        if (presetOpen && Ui.in(mx, my, ddX, ddY, ddW, ddH)) click = false;   // the open list takes this click
        wasDown = down;

        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        float ease = 1f - (float) Math.exp(-dt * 18f);
        openT += (1f - openT) * (1f - (float) Math.exp(-dt * 14f));
        Ui.fade = openT;
        long nowMs = System.currentTimeMillis();

        int px = (vw - PW) / 2, py = (vh - ph) / 2;
        int cardW = PW - 2 * PAD;
        g.pose().pushMatrix();
        g.pose().scale(s, s);

        Ui.glass(g, px, py, PW, ph);
        Ui.rect(g, px + PAD, py + 12, 3, 10, Ui.ACCENT);
        g.drawString(font, Component.literal("Buddy").withStyle(ChatFormatting.BOLD),
                px + PAD + 9, py + 13, Ui.mul(Ui.TEXT), false);
        int y = py + 34;

        // on / off
        boolean on = Buddy.enabled();
        if (onAnim < 0f) onAnim = on ? 1f : 0f;
        onAnim += ((on ? 1f : 0f) - onAnim) * ease;
        boolean hRow = Ui.in(mx, my, px + PAD, y, cardW, ROW);
        Ui.rect(g, px + PAD, y, cardW, ROW, hRow ? Ui.HOVER : Ui.SURFACE);
        Ui.text(g, font, "Buddy", px + PAD + 8, y + (ROW - 8) / 2, Ui.TEXT);
        float tx = px + PAD + cardW - 8 - 30, ty = y + (ROW - 16) / 2f;
        Ui.rect(g, tx, ty, 30, 16, Ui.lerp(Ui.OFF, Ui.ACCENT, onAnim));
        Ui.rect(g, tx + 2 + onAnim * 14, ty + 2, 12, 12, Ui.TEXT);
        if (click && hRow) Buddy.setEnabled(!on);
        y += ROW + ROW_GAP;

        // skin and name (typed in a small screen)
        String[] label = {"Skin", "Name"};
        String[] value = {Buddy.skinText(), Buddy.name()};
        for (int k = 0; k < 2; k++) {
            Ui.rect(g, px + PAD, y, cardW, ROW, Ui.SURFACE);
            Ui.text(g, font, label[k], px + PAD + 8, y + (ROW - 8) / 2, Ui.TEXT);
            int bw = 56, bx = px + PAD + cardW - 6 - bw, by = y + (ROW - 16) / 2;
            String shown = value[k];
            int room = bx - 6 - (px + PAD + 16 + font.width(label[k]));
            while (shown.length() > 1 && font.width(shown) > room) shown = shown.substring(0, shown.length() - 1);
            Ui.text(g, font, shown, px + PAD + 16 + font.width(label[k]), y + (ROW - 8) / 2, Ui.MUTED);
            boolean hov = Ui.in(mx, my, bx, by, bw, 16);
            Ui.button(g, font, "Change", bx, by, bw, 16, hov, false);
            if (click && hov) mc.setScreen(new BuddyInputScreen(this, k == 0));
            y += ROW + ROW_GAP;
        }

        // presets: a dropdown of saved buddies, plus Save / Del
        int rowY = y, fieldX, fieldW;
        {
            Ui.rect(g, px + PAD, y, cardW, ROW, Ui.SURFACE);
            Ui.text(g, font, "Presets", px + PAD + 8, y + (ROW - 8) / 2, Ui.TEXT);
            int by = y + (ROW - 16) / 2;
            int delX = px + PAD + cardW - 6 - 30, saveX = delX - 4 - 36;
            fieldX = px + PAD + 16 + font.width("Presets");
            fieldW = saveX - 6 - fieldX;
            int n = Buddy.presetCount();
            boolean msgOn = nowMs < msgUntil;
            String shown = msgOn ? msg
                    : n == 0 ? "none saved" : (Buddy.presetIndex() >= 0 ? Buddy.presetName(Buddy.presetIndex()) : "choose...");
            boolean hField = Ui.in(mx, my, fieldX, by, fieldW, 16);
            Ui.rect(g, fieldX, by, fieldW, 16, hField || presetOpen ? Ui.HOVER : Ui.SURFACE);
            int room = fieldW - 22;
            while (shown.length() > 1 && font.width(shown) > room) shown = shown.substring(0, shown.length() - 1);
            Ui.text(g, font, shown, fieldX + 6, by + 4, msgOn ? Ui.ACCENT : Ui.TEXT);
            Ui.text(g, font, presetOpen ? "^" : "v", fieldX + fieldW - 10, by + 4, Ui.MUTED);
            boolean hSave = Ui.in(mx, my, saveX, by, 36, 16), hDel = Ui.in(mx, my, delX, by, 30, 16);
            Ui.button(g, font, "Save", saveX, by, 36, 16, hSave, false);
            Ui.button(g, font, "Del", delX, by, 30, 16, hDel, false);
            if (click && hField && n > 0) {
                presetOpen = !presetOpen;
                click = false;
            } else if (rawClick && presetOpen && !Ui.in(mx, my, ddX, ddY, ddW, ddH)) {
                presetOpen = false;   // clicked somewhere else: close it
            }
            if (click && hSave) {
                msg = Buddy.savePreset() ? "Saved \"" + Buddy.name() + "\"" : "Set a skin first";
                msgUntil = nowMs + 2000;
            }
            if (click && hDel && Buddy.presetIndex() >= 0) {
                String gone = Buddy.presetName(Buddy.presetIndex());
                Buddy.deletePreset(Buddy.presetIndex());
                msg = "Deleted \"" + gone + "\"";
                msgUntil = nowMs + 2000;
            }
            y += ROW + ROW_GAP;
        }

        // a hint line
        Ui.text(g, font, "Type a skin UUID, name it, then Save to keep it as a preset.", px + PAD + 2, y + (ROW - 8) / 2, Ui.MUTED);
        y += ROW + ROW_GAP + 8;

        boolean hDone = Ui.in(mx, my, px + PAD, y, cardW, 26);
        doneHov += ((hDone ? 1f : 0f) - doneHov) * ease;
        Ui.button(g, font, "Done", px + PAD, y, cardW, 26, doneHov, false);
        if (click && hDone) onClose();

        // the open presets list, drawn last so it sits on top of everything
        if (presetOpen && Buddy.presetCount() > 0) {
            int cnt = Math.min(8, Buddy.presetCount()), itemH = 16;
            ddX = fieldX;
            ddY = rowY + (ROW - 16) / 2 + 17;
            ddW = fieldW;
            ddH = cnt * itemH + 2;
            Ui.rect(g, ddX - 1, ddY - 1, ddW + 2, ddH + 2, 0xFF3A3F47);
            Ui.rect(g, ddX, ddY, ddW, ddH, 0xFF161A21);
            for (int i = 0; i < cnt; i++) {
                int iy = ddY + 1 + i * itemH;
                boolean hi = Ui.in(mx, my, ddX, iy, ddW, itemH);
                if (hi) Ui.rect(g, ddX, iy, ddW, itemH, 0xFF2A2F37);
                String nm = Buddy.presetName(i);
                while (nm.length() > 1 && font.width(nm) > ddW - 12) nm = nm.substring(0, nm.length() - 1);
                Ui.text(g, font, nm, ddX + 6, iy + 4, i == Buddy.presetIndex() ? Ui.ACCENT : Ui.TEXT);
                if (rawClick && hi) {
                    Buddy.usePreset(i);
                    presetOpen = false;
                }
            }
        } else {
            ddX = ddY = ddW = ddH = 0;
        }

        g.pose().popMatrix();
        Ui.fade = 1f;
    }

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
        Minecraft.getInstance().setScreen(parent);
    }
}
