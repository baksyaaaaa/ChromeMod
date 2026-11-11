package com.chromeclient;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** A small screen with a text box: type something, Save applies it. */
public class TextInputScreen extends Screen {
    private final Screen parent;
    private final String hint, initial;
    private final int maxLen;
    private final Consumer<String> onSave;
    private EditBox box;

    public TextInputScreen(Screen parent, String title, String hint, String initial, int maxLen, Consumer<String> onSave) {
        super(Component.literal(title));
        this.parent = parent;
        this.hint = hint;
        this.initial = initial;
        this.maxLen = maxLen;
        this.onSave = onSave;
    }

    @Override
    protected void init() {
        int w = 300, x = width / 2 - w / 2, y = height / 2 - 14;
        box = new EditBox(font, x, y, w, 20, Component.literal(""));
        box.setMaxLength(maxLen);
        box.setValue(initial);
        addRenderableWidget(box);
        setInitialFocus(box);
    }

    private boolean wasDown = true;   // true: the click that opened this screen doesn't count

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        int w = 300, x = width / 2 - w / 2, y = height / 2 - 14;
        Ui.glass(g, x - 14, y - 44, w + 28, 108);
        Ui.text(g, font, getTitle().getString(), x, y - 32, Ui.TEXT);
        Ui.textFit(g, font, hint, x, y - 18, w, Ui.MUTED);
        super.render(g, mouseX, mouseY, delta);
        boolean down = EditHudScreen.leftDown(net.minecraft.client.Minecraft.getInstance());
        boolean click = down && !wasDown;
        wasDown = down;
        boolean hs = Ui.in(mouseX, mouseY, x, y + 30, 146, 20), hc = Ui.in(mouseX, mouseY, x + 154, y + 30, 146, 20);
        Ui.button(g, font, "Save", x, y + 30, 146, 20, hs, true);
        Ui.button(g, font, "Cancel", x + 154, y + 30, 146, 20, hc, false);
        if (click && hs) { onSave.accept(box.getValue()); onClose(); }
        else if (click && hc) onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
