package com.chromeclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

/** gives the normal game buttons (in every menu) the dark glass look of Chrome Client's main menu */
public final class MenuStyle {
    private static final Style POPPINS = Style.EMPTY.withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath(ChromeClientMod.MOD_ID, "poppins")));

    private MenuStyle() {}

    public static boolean applies(AbstractButton b) {
        if (!Extras.customTitle) return false;
        if (b.getAlpha() <= 0.01f) return false;   // invisible hit areas (our own main menu draws itself)
        return b.getClass() == Button.class || b instanceof CycleButton;
    }

    public static void draw(AbstractButton b, GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        int x = b.getX(), y = b.getY(), w = b.getWidth(), h = b.getHeight();
        boolean hover = b.active && b.isHoveredOrFocused();
        int fill = !b.active ? 0x55101014 : hover ? 0x99282830 : 0x88141418;
        Ui.rrect(g, x, y, w, h, 2.5f, fill);
        Ui.rring(g, x, y, w, h, 2.5f, hover ? 0x55FFFFFF : 0x2AFFFFFF);
        float sc = 0.6f;   // the Poppins font is cut at 16, scaled down to about the normal text size
        Component m = b.getMessage().copy().withStyle(POPPINS);
        float maxW = (w - 8) / sc;
        if (mc.font.width(m) > maxW) {
            String t = b.getMessage().getString();
            while (t.length() > 1 && mc.font.width(Component.literal(t + "...").withStyle(POPPINS)) > maxW) t = t.substring(0, t.length() - 1);
            m = Component.literal(t + "...").withStyle(POPPINS);
        }
        float tw = mc.font.width(m) * sc;
        int col = b.active ? 0xFFFFFFFF : 0xFF8A8A92;
        g.pose().pushMatrix();
        g.pose().translate(x + (w - tw) / 2f, y + h / 2f - 1.3f * sc);
        g.pose().scale(sc, sc);
        g.drawString(mc.font, m, 0, 0, col, false);
        g.pose().popMatrix();
    }
}
