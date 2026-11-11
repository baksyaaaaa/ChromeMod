package com.chromeclient;

/**
 * Freelook: hold the key and the mouse turns only the camera, not your character, so you can look around while
 * you keep walking the same way. Let go and the camera snaps back. The mixins feed the mouse in and add the
 * offsets to the camera.
 */
public final class Freelook {
    public static volatile boolean active = false;
    /** set by the camera mixin as soon as it runs: until then the mouse is never taken away from you */
    public static volatile boolean hooked = false;
    public static float yaw = 0f, pitch = 0f;   // how far the camera is turned away from where your character looks (degrees)

    /** called every tick with whether the key is held (and nothing else has the mouse) */
    public static void update(boolean want) {
        want = want && Extras.freelook;
        if (want == active) return;
        active = want;
        yaw = 0f;
        pitch = 0f;
    }

    /** mouse movement while it is active (the same units the player's own turn() gets) */
    public static void turn(double dYaw, double dPitch) {
        yaw += (float) (dYaw * 0.15);
        pitch += (float) (dPitch * 0.15);
        // stop exactly where the camera stops (straight up / straight down): before, the value kept growing past that
        // point and you had to turn it all the way back before the camera moved again, which felt like it was locked
        var p = net.minecraft.client.Minecraft.getInstance().player;
        float base = p == null ? 0f : p.getXRot();
        pitch = Math.max(-90f - base, Math.min(90f - base, pitch));
    }
}
