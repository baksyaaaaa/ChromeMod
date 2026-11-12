package com.chromeclient;

import net.minecraft.client.Minecraft;

/** The emote your own character is playing right now (picked from the wheel). The model mixin poses it. */
public final class PlayerEmotes {
    public static volatile int emote = BuddyEmotes.NONE;
    public static volatile int start = 0;   // player tick it started on

    public static void play(int id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        start = mc.player.tickCount;
        emote = id;
    }

    /** every tick: ends the emote when it is over, or as soon as you move, jump, hit or use something */
    public static void tick(Minecraft mc) {
        if (emote == BuddyEmotes.NONE) return;
        var p = mc.player;
        if (p == null || !Extras.emotes) { emote = BuddyEmotes.NONE; return; }
        int t = p.tickCount - start;
        boolean moved = p.getDeltaMovement().horizontalDistanceSqr() > 0.004 || !p.onGround() || p.swinging || p.isUsingItem();
        if (t >= BuddyEmotes.length(emote) || (t > 4 && moved)) emote = BuddyEmotes.NONE;
    }
}
