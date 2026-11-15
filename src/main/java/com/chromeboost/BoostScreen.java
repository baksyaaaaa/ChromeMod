package com.chromeboost;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** the settings menu (F10): one button per option, click to switch / step to the next value */
public final class BoostScreen extends Screen {
    private final Screen parent;

    public BoostScreen(Screen parent) {
        super(Component.literal("Chrome Boost"));
        this.parent = parent;
    }

    private interface Label { String text(); }

    private void toggle(int x, int y, int w, String name, java.util.function.BooleanSupplier get, java.util.function.Consumer<Boolean> set, String note) {
        Label l = () -> name + ": " + (get.getAsBoolean() ? "ON" : "OFF") + (note == null ? "" : " (" + note + ")");
        Button b = Button.builder(Component.literal(l.text()), btn -> {
            set.accept(!get.getAsBoolean());
            btn.setMessage(Component.literal(l.text()));
            Settings.save();
        }).bounds(x, y, w, 20).build();
        addRenderableWidget(b);
    }

    private void steps(int x, int y, int w, String name, int[] values, IntSupplier get, IntConsumer set, String unit, String zero) {
        Label l = () -> {
            int v = get.getAsInt();
            return name + ": " + (v == 0 && zero != null ? zero : v + unit);
        };
        Button b = Button.builder(Component.literal(l.text()), btn -> {
            int cur = get.getAsInt(), next = values[0];
            for (int i = 0; i < values.length; i++) {
                if (values[i] == cur) { next = values[(i + 1) % values.length]; break; }
                if (values[i] > cur) { next = values[i]; break; }
            }
            set.accept(next);
            btn.setMessage(Component.literal(l.text()));
            Settings.save();
        }).bounds(x, y, w, 20).build();
        addRenderableWidget(b);
    }

    @Override
    protected void init() {
        Settings s = Settings.get();
        int colW = 150, gap = 8, left = width / 2 - colW - gap / 2, right = width / 2 + gap / 2, y = 44, row = 24;
        String ec = Compat.entityCullingBy(), pc = Compat.particlesBy();

        toggle(left, y, colW, "Entity culling", () -> s.entityCull, v -> s.entityCull = v, ec != null ? ec + " does it" : null);
        steps(left, y + row, colW, "Item distance", new int[]{0, 16, 24, 32, 48, 64}, () -> s.itemDistance, v -> s.itemDistance = v, " m", "off");
        steps(left, y + row * 2, colW, "Mob distance", new int[]{0, 48, 64, 96, 128, 192}, () -> s.mobDistance, v -> s.mobDistance = v, " m", "off");

        toggle(right, y, colW, "Particle limit", () -> s.particleCull, v -> s.particleCull = v, pc != null ? pc + " does it" : null);
        steps(right, y + row, colW, "Particle range", new int[]{0, 16, 24, 32, 48, 64}, () -> s.particleDistance, v -> s.particleDistance = v, " m", "off");
        steps(right, y + row * 2, colW, "Particles per tick", new int[]{0, 100, 200, 400, 800, 1500}, () -> s.particleCap, v -> s.particleCap = v, "", "off");

        int y2 = y + row * 3 + 12;
        toggle(left, y2, colW, "Background FPS cap", () -> s.backgroundLimit, v -> s.backgroundLimit = v, null);
        steps(right, y2, colW, "Background FPS", new int[]{5, 10, 15, 20, 30, 60}, () -> s.backgroundFps, v -> s.backgroundFps = v, "", null);

        toggle(left, y2 + row, colW, "Adaptive render dist.", () -> s.adaptive, v -> s.adaptive = v, null);
        steps(right, y2 + row, colW, "Target FPS", new int[]{30, 45, 60, 90, 120, 144}, () -> s.targetFps, v -> s.targetFps = v, "", null);
        steps(left, y2 + row * 2, colW, "Lowest distance", new int[]{4, 6, 8, 10, 12}, () -> s.minDistance, v -> s.minDistance = v, " chunks", null);

        int y3 = y2 + row * 3 + 12;
        toggle(left, y3, colW, "Memory warning", () -> s.memoryWarning, v -> s.memoryWarning = v, null);
        toggle(right, y3, colW, "Clean up on leave", () -> s.cleanOnLeave, v -> s.cleanOnLeave = v, null);

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(width / 2 - 60, y3 + row + 14, 120, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        g.drawString(font, title, width / 2 - font.width(title) / 2, 16, 0xFFFFFFFF, true);
        Component sub = Component.literal("Memory in use: " + MemoryCare.info() + (Compat.engineMod() ? "   |   works with the engine mods you have installed" : ""));
        g.drawString(font, sub, width / 2 - font.width(sub) / 2, 29, 0xFFB8B8B8, false);
        Component tip = Component.literal("Tip: for fewer GC stutters add to your launcher's Java arguments:  -XX:+UseG1GC -XX:MaxGCPauseMillis=50 -Xms2G -Xmx4G");
        int tw = Math.min(font.width(tip), width - 20);
        g.drawString(font, tip, Math.max(10, width / 2 - tw / 2), height - 16, 0xFF8A8A92, false);
    }

    @Override
    public void onClose() {
        Settings.save();
        minecraft.setScreen(parent);
    }
}
