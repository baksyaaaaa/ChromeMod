package com.chromeclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** The emote wheel (B): move the mouse onto one and click it. Esc closes it. */
public final class EmoteWheelScreen extends Screen {
    private static final String[] NAMES = {"Wave", "Dance", "Cheer", "Bow", "Jump", "Jog"};
    private static final int[] IDS = {BuddyEmotes.WAVE, BuddyEmotes.DANCE, BuddyEmotes.CHEER, BuddyEmotes.BOW, BuddyEmotes.JUMP, BuddyEmotes.JOG};
    private static final int R = 62, HALF = 26;

    private boolean wasDown = true, pendingClick = false;
    private final float[] hov = new float[NAMES.length];
    private float openT = 0f;
    private long last = System.nanoTime();

    public EmoteWheelScreen() {
        super(Component.literal("Emotes"));
    }

    /** the game's own click event, as a backup for polling the button */
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) pendingClick = true;
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        Minecraft mc = Minecraft.getInstance();
        boolean down = EditHudScreen.leftDown(mc);
        boolean click = (down && !wasDown) || pendingClick;
        pendingClick = false;
        wasDown = down;

        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - last) / 1e9f);
        last = now;
        openT += (1f - openT) * (1f - (float) Math.exp(-dt * 14f));
        float ease = 1f - (float) Math.exp(-dt * 18f);
        Ui.fade = 1f;

        int cx = width / 2, cy = height / 2;
        Ui.rrect(g, cx - R - HALF - 10, cy - R - HALF - 10, 2 * (R + HALF + 10), 2 * (R + HALF + 10), R + HALF + 10, Ui.mul(0x99101018));
        Ui.textCenter(g, font, "Emotes", cx - 30, cy - 4, 60, Ui.TEXT);

        int chosen = -1;
        for (int i = 0; i < NAMES.length; i++) {
            double a = -Math.PI / 2 + i * 2 * Math.PI / NAMES.length;
            int ix = cx + (int) Math.round(Math.cos(a) * R * openT), iy = cy + (int) Math.round(Math.sin(a) * R * openT);
            boolean h = Ui.in(mouseX, mouseY, ix - HALF, iy - HALF, 2 * HALF, 2 * HALF);
            hov[i] += ((h ? 1f : 0f) - hov[i]) * ease;
            Ui.rrect(g, ix - HALF, iy - HALF, 2 * HALF, 2 * HALF, HALF, Ui.lerp(Ui.SURFACE, Ui.ACCENT, hov[i]));
            Ui.textCenter(g, font, NAMES[i], ix - HALF, iy - 4, 2 * HALF, Ui.lerp(Ui.TEXT, 0xFF1A1430, hov[i]));
            if (h) chosen = i;
        }
        if (click && chosen >= 0) {
            PlayerEmotes.play(IDS[chosen]);
            onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
