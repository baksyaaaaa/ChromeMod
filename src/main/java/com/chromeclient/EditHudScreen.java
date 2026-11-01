package com.chromeclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Drag a box to move it, drag any corner to resize it (the opposite corner stays put).
 * The left mouse button state is read directly, so no input overrides are needed.
 */
public class EditHudScreen extends Screen {
    private final Hud hud;
    private int dragging = -1;          // index into Hud.IDS being moved, -1 = none
    private int resizing = -1;          // index being resized
    private int selected = -1;          // the box you clicked: stays selected only while the cursor is on it
    private int grabX, grabY;           // where inside the box you grabbed it
    private float startScale;
    private int startDist;              // how far the grab point was from the fixed (opposite) corner
    private int corner = 0;             // 0 top-left, 1 top-right, 2 bottom-left, 3 bottom-right
    private int anchorX, anchorY;       // the opposite corner stays where it is while you resize
    private boolean wasDown = true;     // true at first so a held button doesn't count as a click

    public EditHudScreen(Hud hud) {
        super(Component.literal("Move HUD"));
        this.hud = hud;
    }

    /** Is the left mouse button held? Asks the window directly, and the game's own flag as a backup. */
    static boolean leftDown(Minecraft mc) {
        try {
            long window = GLFW.glfwGetCurrentContext();   // the game window (this runs on the render thread)
            if (window != 0L && GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS) return true;
        } catch (Throwable ignored) {
            // fall through to the game's flag
        }
        try {
            return mc.mouseHandler.isLeftPressed();
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean inBox(int[] b, int mx, int my) {
        return Ui.in(mx, my, b[0], b[1], b[2], b[3]);
    }

    /** is the cursor on this box (its body, or one of its corner dots which stick out a little)? */
    private static boolean hit(int[] b, int mx, int my) {
        return inBox(b, mx, my) || cornerAt(b, mx, my) >= 0;
    }

    /** corner of the box the cursor is on (0 TL, 1 TR, 2 BL, 3 BR), or -1 */
    private static int cornerAt(int[] b, int mx, int my) {
        int best = -1;
        double bestD = 25;   // within about 5 px
        for (int c = 0; c < 4; c++) {
            int cx = b[0] + ((c & 1) == 1 ? b[2] : 0), cy = b[1] + (c >= 2 ? b[3] : 0);
            double d = (mx - cx) * (mx - cx) + (my - cy) * (my - cy);
            if (d <= bestD) {
                bestD = d;
                best = c;
            }
        }
        return best;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        Minecraft mc = Minecraft.getInstance();
        boolean down = leftDown(mc);
        boolean click = down && !wasDown;
        wasDown = down;

        // the two buttons at the bottom middle
        int by = height / 2;
        boolean hReset = Ui.in(mouseX, mouseY, width / 2 - 105, by, 100, 22);
        boolean hDone = Ui.in(mouseX, mouseY, width / 2 + 5, by, 100, 22);
        if (click && hReset) {
            Config.get().offsets.clear();
            Config.get().scales.clear();
        } else if (click && hDone) {
            onClose();
            return;
        } else if (click) {
            // the selected box gets first pick when boxes overlap, then the rest from the top one down
            // pressing again on the selected box, where other boxes overlap it, cycles the selection to the next one
            // under the cursor (and around again); otherwise the top-most box under the cursor is picked.
            int[] order = new int[Hud.IDS.length];
            int n = 0;
            boolean keep = selected >= 0 && hit(hud.bounds(Hud.IDS[selected], mc), mouseX, mouseY);
            if (keep) {
                int next = -1;   // next box below the selected one at this spot, wrapping around
                for (int d = 1; d < Hud.IDS.length && next < 0; d++) {
                    int i = ((selected - d) % Hud.IDS.length + Hud.IDS.length) % Hud.IDS.length;
                    if (hit(hud.bounds(Hud.IDS[i], mc), mouseX, mouseY)) next = i;
                }
                order[n++] = next >= 0 ? next : selected;   // nothing else under the cursor: keep this one
            }
            int first = n > 0 ? order[0] : -1;
            for (int i = Hud.IDS.length - 1; i >= 0; i--) if (i != first) order[n++] = i;
            for (int k = 0; k < n && resizing < 0 && dragging < 0; k++) {
                int i = order[k];
                int[] bx = hud.bounds(Hud.IDS[i], mc);
                int cn = cornerAt(bx, mouseX, mouseY);
                if (cn >= 0) {   // a corner dot resizes
                    resizing = i;
                    selected = i;
                    corner = cn;
                    startScale = Config.get().scale(Hud.IDS[i]);
                    anchorX = bx[0] + ((cn & 1) == 1 ? 0 : bx[2]);    // the opposite corner
                    anchorY = bx[1] + (cn >= 2 ? 0 : bx[3]);
                    startDist = Math.max(4, Math.abs(mouseX - anchorX));
                } else if (inBox(bx, mouseX, mouseY)) {   // the body moves
                    dragging = i;
                    selected = i;
                    grabX = mouseX - bx[0];
                    grabY = mouseY - bx[1];
                }
            }
        }
        // a box only stays selected while the cursor is on it
        if (dragging < 0 && resizing < 0 && selected >= 0 && !hit(hud.bounds(Hud.IDS[selected], mc), mouseX, mouseY)) selected = -1;
        if (!down) {
            dragging = -1;
            resizing = -1;
        }
        if (dragging >= 0) {
            String id = Hud.IDS[dragging];
            int[] b = hud.bounds(id, mc);
            int[] o = Config.get().off(id);
            int wantX = Math.max(0, Math.min(width - b[2], mouseX - grabX));
            int wantY = Math.max(0, Math.min(height - b[3], mouseY - grabY));
            o[0] += wantX - b[0];
            o[1] += wantY - b[1];
        }
        if (resizing >= 0) {
            String id = Hud.IDS[resizing];
            int[] base = hud.base(id, mc);
            Config.get().setScale(id, startScale * Math.max(1, Math.abs(mouseX - anchorX)) / startDist);
            float sc = Config.get().scale(id);
            int w = Math.round(base[2] * sc), h = Math.round(base[3] * sc);
            // keep the opposite corner pinned: work out where the box's top-left must be
            int x0 = (corner & 1) == 1 ? anchorX : anchorX - w;
            int y0 = corner >= 2 ? anchorY : anchorY - h;
            int[] o = Config.get().off(id);
            o[0] = x0 - base[0];
            o[1] = y0 - base[1];
        }

        // boxes: quiet outlines, only the one you're on gets colour, a name tag and a resize dot
        int tagIndex = -1;   // the box that gets the highlight, name tag and dots
        if (dragging >= 0) tagIndex = dragging;
        else if (resizing >= 0) tagIndex = resizing;
        else if (selected >= 0) tagIndex = selected;   // still under the cursor, so it keeps priority over boxes overlapping it
        else {
            for (int i = 0; i < Hud.IDS.length; i++) {
                if (hit(hud.bounds(Hud.IDS[i], mc), mouseX, mouseY)) tagIndex = i;   // top-most box under the cursor
            }
        }
        for (int i = 0; i < Hud.IDS.length; i++) {
            if (i == tagIndex) continue;   // drawn last so it sits on top
            int[] b = hud.bounds(Hud.IDS[i], mc);
            boolean on = hud.enabled(Hud.IDS[i]);
            Ui.rring(g, b[0], b[1], b[2], b[3], 2, on ? 0x55FFFFFF : 0x22FFFFFF);
        }
        if (tagIndex >= 0) {
            int[] b = hud.bounds(Hud.IDS[tagIndex], mc);
            Ui.rrect(g, b[0], b[1], b[2], b[3], 2, 0x221BD96A);
            Ui.rring(g, b[0], b[1], b[2], b[3], 2, Ui.ACCENT);
            for (int c = 0; c < 4; c++) {   // a resize dot on every corner
                int cx = b[0] + ((c & 1) == 1 ? b[2] : 0), cy = b[1] + (c >= 2 ? b[3] : 0);
                Ui.rrect(g, cx - 3, cy - 3, 7, 7, 3.5f, c == (resizing == tagIndex ? corner : cornerAt(b, mouseX, mouseY)) ? 0xFFFFFFFF : Ui.ACCENT);
            }
            String label = Hud.LABELS[tagIndex] + (hud.enabled(Hud.IDS[tagIndex]) ? "" : " (off)");
            if (resizing == tagIndex) label += "  " + Math.round(Config.get().scale(Hud.IDS[tagIndex]) * 100) + "%";
            int tw = font.width(label) + 10;
            int tx = Math.max(2, Math.min(width - tw - 2, b[0]));
            int ty = b[1] >= 18 ? b[1] - 16 : b[1] + b[3] + 4;
            Ui.rrect(g, tx, ty, tw, 13, 3, 0xE0101216);
            Ui.text(g, font, label, tx + 5, ty + 3, Ui.TEXT);
        }

        // one small glass bar in the middle with the hint and the two buttons
        int panelY = by - 30;
        Ui.glass(g, width / 2 - 112, panelY, 224, 62);
        String hint = "Drag a box to move it, drag a corner to resize";
        Ui.text(g, font, hint, width / 2 - font.width(hint) / 2, panelY + 10, Ui.MUTED);
        Ui.button(g, font, "Reset", width / 2 - 105, by, 100, 22, hReset, false);
        Ui.button(g, font, "Done", width / 2 + 5, by, 100, 22, hDone, false);
    }

    /** No blur behind the editor, so you can see the real HUD while you place things (just a light dim). */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0x30000000);
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
