package com.chromeclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** A small screen with a text box to type the buddy's skin UUID or name (ctrl+V works too). Nothing has to be copied first. */
public class BuddyInputScreen extends Screen {
    private final Screen parent;
    private final boolean skin;
    private EditBox box;
    private String error = "";

    public BuddyInputScreen(Screen parent, boolean skin) {
        super(Component.literal(skin ? "Buddy skin" : "Buddy name"));
        this.parent = parent;
        this.skin = skin;
    }

    @Override
    protected void init() {
        int w = 220, x = width / 2 - w / 2, y = height / 2 - 14;
        box = new EditBox(font, x, y, w, 20, Component.literal(""));
        box.setMaxLength(skin ? 40 : 16);
        box.setValue(skin ? Buddy.skinFull() : Buddy.name());
        addRenderableWidget(box);
        setInitialFocus(box);
    }

    private void apply() {
        boolean ok = skin ? Buddy.setSkin(box.getValue()) : Buddy.setName(box.getValue());
        if (ok) {
            onClose();
        } else {
            error = skin ? "That isn't a valid UUID" : "Type a name (letters, digits, _)";
        }
    }

    private boolean wasDown = true;   // true: the click that opened this screen doesn't count

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        int w = 220, x = width / 2 - w / 2, y = height / 2 - 14;
        Ui.glass(g, x - 14, y - 44, w + 28, 118);
        Ui.text(g, font, getTitle().getString(), x, y - 32, Ui.TEXT);
        String t = skin ? "Type the skin's UUID (or paste it with Ctrl+V)" : "Type the buddy's name";
        Ui.textFit(g, font, t, x, y - 18, w, Ui.MUTED);
        super.render(g, mouseX, mouseY, delta);
        boolean down = EditHudScreen.leftDown(Minecraft.getInstance());
        boolean click = down && !wasDown;
        wasDown = down;
        boolean hs = Ui.in(mouseX, mouseY, x, y + 30, 106, 20), hc = Ui.in(mouseX, mouseY, x + 114, y + 30, 106, 20);
        Ui.button(g, font, "Save", x, y + 30, 106, 20, hs, true);
        Ui.button(g, font, "Cancel", x + 114, y + 30, 106, 20, hc, false);
        if (!error.isEmpty()) Ui.textFit(g, font, error, x, y + 56, w, 0xFFFF6B6B);
        if (click && hs) apply();
        else if (click && hc) onClose();
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
