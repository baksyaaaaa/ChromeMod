package com.chromeclient;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** The Chrome Client logo: a glassy blue "C" (one 256x256 texture, drawn at any size). */
final class Logo {
    static final Identifier TEX = Identifier.fromNamespaceAndPath("chromeclient", "textures/gui/logo.png");

    static final Identifier TEX_C = Identifier.fromNamespaceAndPath("chromeclient", "textures/gui/logo_c.png");

    private Logo() { }

    /** just the glassy C, no tile behind it (for small spots on top of panels) */
    static void drawC(GuiGraphics g, float x, float y, float size) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(size / 256f, size / 256f);
        g.blit(RenderPipelines.GUI_TEXTURED, TEX_C, 0, 0, 0f, 0f, 256, 256, 256, 256);
        g.pose().popMatrix();
    }

    /** (x, y) top-left and size in GUI coordinates */
    static void draw(GuiGraphics g, float x, float y, float size) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(size / 256f, size / 256f);
        g.blit(RenderPipelines.GUI_TEXTURED, TEX, 0, 0, 0f, 0f, 256, 256, 256, 256);
        g.pose().popMatrix();
    }
}
