package com.chromeboost;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Memory help that does something real:
 *  - one garbage collection a moment after you leave a world, when a lot of world data has just become garbage (done
 *    in the background, never during play, so it can not cause a stutter);
 *  - a single warning when the game is nearly out of memory, which is the most common cause of constant stutter.
 * (A mod can not change the JVM flags; the menu shows good ones for the launcher.)
 */
final class MemoryCare {
    private static int highTicks;
    private static long lastWarn;

    private MemoryCare() {}

    static void onLeave() {
        if (!Settings.get().cleanOnLeave) return;
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(2000);
                System.gc();
            } catch (Throwable ignored) {
                // nothing to do
            }
        }, "chrome-boost-clean");
        t.setDaemon(true);
        t.start();
    }

    static void tick(Minecraft mc) {
        if (!Settings.get().memoryWarning || mc.player == null) {
            highTicks = 0;
            return;
        }
        if (mc.player.tickCount % 20 != 0) return;   // once a second is enough
        Runtime r = Runtime.getRuntime();
        long max = r.maxMemory(), used = r.totalMemory() - r.freeMemory();
        if (used > max * 0.92) highTicks++;
        else highTicks = 0;
        long now = System.currentTimeMillis();
        if (highTicks >= 20 && now - lastWarn > 600_000L) {   // 20 seconds in a row, at most every 10 minutes
            lastWarn = now;
            mc.player.displayClientMessage(Component.literal("[Chrome Boost] Memory is almost full (" + (used >> 20) + " of " + (max >> 20)
                    + " MB). Give the game more RAM in your launcher (for example -Xmx4G) to stop stutters."), false);
        }
    }

    static String info() {
        Runtime r = Runtime.getRuntime();
        long max = r.maxMemory() >> 20, used = (r.totalMemory() - r.freeMemory()) >> 20;
        return used + " / " + max + " MB";
    }
}
