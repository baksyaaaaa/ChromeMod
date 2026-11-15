package com.chromeboost;

import net.minecraft.client.Minecraft;

/**
 * Keeps the frame rate near a target by moving the render distance down (and back up) one chunk at a time. It only ever
 * moves between your own render distance and the minimum you set, waits between steps (every change reloads chunks), and
 * hands your own value back when you leave the world or switch the module off. If you change the render distance yourself,
 * that becomes the new value to return to.
 */
final class AdaptiveDistance {
    private static final int SAMPLE_TICKS = 100;      // 5 seconds of frame rates are averaged
    private static final int COOLDOWN_TICKS = 400;    // at most one step every 20 seconds

    private static int baseline = -1;      // your own render distance
    private static int lastSet = -1;       // what this module set last
    private static long sum;
    private static int count, cooldown;

    private AdaptiveDistance() {}

    static void tick(Minecraft mc) {
        Settings s = Settings.get();
        if (mc.level == null || mc.player == null || !s.adaptive) {
            restore(mc);
            return;
        }
        int now = mc.options.renderDistance().get();
        if (baseline < 0 || (lastSet >= 0 && now != lastSet)) {   // first run, or you changed it yourself
            baseline = now;
            lastSet = -1;
        }
        if (mc.screen != null || !mc.isWindowActive()) {          // menus and background have other frame rates
            sum = 0; count = 0;
            return;
        }
        sum += mc.getFps();
        count++;
        if (cooldown > 0) cooldown--;
        if (count < SAMPLE_TICKS) return;
        double avg = (double) sum / count;
        sum = 0; count = 0;
        if (cooldown > 0) return;

        int limit = mc.options.framerateLimit().get();
        double raiseAt = Math.min(s.targetFps * 1.3, limit >= 260 ? Double.MAX_VALUE : limit * 0.95);
        int min = Math.min(s.minDistance, baseline);
        if (avg < s.targetFps * 0.85 && now > min) {
            set(mc, now - 1);
        } else if (avg >= raiseAt && now < baseline) {
            set(mc, now + 1);
        }
    }

    private static void set(Minecraft mc, int value) {
        lastSet = value;
        cooldown = COOLDOWN_TICKS;
        mc.options.renderDistance().set(value);
    }

    /** gives your own render distance back */
    static void restore(Minecraft mc) {
        if (baseline >= 0 && lastSet >= 0 && mc.options.renderDistance().get() == lastSet) {
            mc.options.renderDistance().set(baseline);
        }
        baseline = -1;
        lastSet = -1;
        sum = 0; count = 0; cooldown = 0;
    }
}
