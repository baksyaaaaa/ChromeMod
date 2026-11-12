package com.chromeclient;

import net.minecraft.util.Mth;

/**
 * The buddy's few emotes: a double jump, jogging on the spot and looking around. Each one is a pose that changes over
 * time (t is in ticks since it started). The player model mixin applies them to the buddy only, blended in and out.
 * Angles are radians as the model uses them: arm xRot -PI/2 = straight forward, -PI = straight up.
 */
public final class BuddyEmotes {
    public static final int NONE = 0, JUMP = 1, JOG = 2, LOOK = 3, WAVE = 4, OBSESSION = 5, BOW = 7;   // 4..7 are for the player's emote wheel
    public static final int COUNT = 3;
    private static final int[] LENGTH = {0, 44, 80, 90, 64, 120, 64, 56, 600};

    // indices of the array pose() fills in
    public static final int HX = 0, HY = 1, HZ = 2, RAX = 3, RAY = 4, RAZ = 5, LAX = 6, LAY = 7, LAZ = 8,
            RLX = 9, RLZ = 10, LLX = 11, LLZ = 12;

    private static final float PI = (float) Math.PI;

    public static int length(int emote) {
        return LENGTH[emote];
    }

    /** 0..1: eases in over the first 6 ticks and out over the last 8 */
    public static float weight(int emote, float t) {
        if (emote == NONE) return 0f;
        float w = Mth.clamp(Math.min(t / 6f, (LENGTH[emote] - t) / 8f), 0f, 1f);
        return w * w * (3f - 2f * w);
    }

    /** how far it hops up at this moment (blocks) */
    public static double hop(int emote, float t, float size) {
        float k = size / 0.5f;
        return switch (emote) {
            case JUMP -> t < 32f ? 0.42 * k * Math.abs(Mth.sin(t * PI / 16f)) : 0.0;   // two jumps
            case JOG -> 0.05 * k * Math.abs(Mth.sin(t * 0.9f));
            default -> 0.0;
        };
    }

    /** how far it sinks down, blocks (none of the current emotes sit) */
    public static double sink(int emote, float t, float size) {
        return 0.0;
    }

    public static float[] pose(int emote, float t) {
        float[] p = new float[13];
        p[RAZ] = 0.08f;
        p[LAZ] = -0.08f;
        switch (emote) {
            case JUMP -> {   // arms fly up on every jump
                float u = t < 32f ? Math.abs(Mth.sin(t * PI / 16f)) : 0f;
                p[RAX] = Mth.lerp(u, 0.1f, -2.5f);
                p[LAX] = p[RAX];
                p[RAZ] = 0.2f + 0.3f * u;
                p[LAZ] = -p[RAZ];
                p[RLX] = -0.5f * u;
                p[LLX] = 0.3f * u;
                p[HX] = -0.2f * u;
            }
            case JOG -> {    // running on the spot: legs pumping, arms swinging, head bobbing
                float s = Mth.sin(t * 0.9f);
                p[RLX] = s * 1.2f;
                p[LLX] = -s * 1.2f;
                p[RAX] = -s * 0.95f;
                p[LAX] = s * 0.95f;
                p[RAZ] = 0.12f;
                p[LAZ] = -0.12f;
                p[HX] = 0.05f + 0.05f * Mth.cos(t * 1.8f);
                p[HZ] = 0.05f * s;
            }
            case LOOK -> {   // looks left and right, up and down, hands behind its back
                p[HY] = 1.0f * Mth.sin(t * 0.1f);
                p[HX] = 0.25f * Mth.sin(t * 0.2f) - 0.05f;
                p[RAX] = 0.45f;
                p[LAX] = 0.45f;
                p[RAZ] = 0.02f;
                p[LAZ] = -0.02f;
            }
            case WAVE -> {   // right arm up, waving side to side
                p[RAX] = -2.75f;
                p[RAZ] = 0.15f + 0.5f * Mth.sin(t * 0.75f);
                p[LAX] = 0.05f;
                p[HY] = 0.2f * Mth.sin(t * 0.25f);
                p[HZ] = 0.06f * Mth.sin(t * 0.75f);
            }
            case OBSESSION -> {   // upbeat bouncy dance: alternating arm pumps, then both hands up, head snapping on the beat
                float beat = t * 0.55f;
                float s = Mth.sin(beat);
                boolean up = ((int) (t / 20f)) % 2 == 1;
                if (up) {
                    p[RAX] = -2.6f + 0.35f * s;
                    p[LAX] = -2.6f - 0.35f * s;
                    p[RAZ] = 0.5f + 0.2f * Mth.cos(beat * 2f);
                    p[LAZ] = -p[RAZ];
                } else {
                    p[RAX] = -1.5f + 0.9f * s;
                    p[LAX] = -1.5f - 0.9f * s;
                    p[RAZ] = 0.25f;
                    p[LAZ] = -0.25f;
                }
                p[RLX] = 0.45f * s;
                p[LLX] = -0.45f * s;
                p[HY] = 0.45f * Mth.sin(beat * 0.5f);
                p[HZ] = 0.14f * s;
                p[HX] = 0.1f * Mth.cos(beat * 2f);
            }
            case BOW -> {    // a slow bow: head and arms dip and come back
                float k = Mth.sin(PI * Math.min(1f, t / 46f));
                p[HX] = 0.95f * k;
                p[RAX] = 0.25f * k;
                p[LAX] = 0.25f * k;
            }
            case STAR -> {   // riding the star: arms out for balance, feet apart, head up
                float sw = 0.06f * Mth.sin(t * 0.12f);
                p[RAZ] = 1.35f + sw;
                p[LAZ] = -1.35f - sw;
                p[RAX] = 0.0f;
                p[LAX] = 0.0f;
                p[RLZ] = 0.14f;
                p[LLZ] = -0.14f;
                p[HX] = -0.12f;
            }
            default -> { }
        }
        return p;
    }
}
