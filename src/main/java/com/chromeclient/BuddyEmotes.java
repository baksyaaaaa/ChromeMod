package com.chromeclient;

import net.minecraft.util.Mth;

/**
 * The buddy's few emotes: a double jump, jogging on the spot and looking around. Each one is a pose that changes over
 * time (t is in ticks since it started). The player model mixin applies them to the buddy only, blended in and out.
 * Angles are radians as the model uses them: arm xRot -PI/2 = straight forward, -PI = straight up.
 */
public final class BuddyEmotes {
    public static final int NONE = 0, JUMP = 1, JOG = 2, LOOK = 3, GRIDDY = 4, LEVITATE = 5, STAR = 8;   // 4..7 are for the player's emote wheel
    public static final int COUNT = 3;
    private static final int[] LENGTH = {0, 44, 80, 90, 180, 600, 0, 0, 600};

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

    /** how far the whole player is lifted while floating (on the star or levitating), in model pixels (1/16 block) */
    public static float lift(int emote, float t) {
        if (emote == STAR) return weight(STAR, t) * (12f + 1.6f * Mth.sin(t * 0.15f));
        if (emote == LEVITATE) return weight(LEVITATE, t) * (16f + 1.8f * Mth.sin(t * 0.1f));
        return 0f;
    }

    private static float ss(float x) {
        x = Mth.clamp(x, 0f, 1f);
        return x * x * (3f - 2f * x);
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
            case GRIDDY -> {   // the Griddy: arms wide and flapping, feet shuffling, head nodding to the beat
                float s = Mth.sin(t * 0.7f);
                p[RAX] = -0.15f + 0.12f * s;
                p[LAX] = -0.15f - 0.12f * s;
                p[RAZ] = 1.25f + 0.28f * s;
                p[LAZ] = -1.25f + 0.28f * s;
                p[RLX] = 0.35f * s;
                p[LLX] = -0.35f * s;
                p[RLZ] = 0.2f + 0.1f * Math.max(0f, s);
                p[LLZ] = -0.2f - 0.1f * Math.max(0f, -s);
                p[HX] = 0.12f * Mth.sin(t * 1.4f);
                p[HZ] = 0.12f * s;
            }
            case LEVITATE -> {   // The Honored One: floating cross-legged, one hand up making the sign, calm head
                p[RLX] = -1.3f;
                p[LLX] = -1.3f;
                p[RLZ] = 0.55f;
                p[LLZ] = -0.55f;
                p[RAX] = -1.45f;
                p[RAZ] = 0.1f;
                p[RAY] = -0.35f;
                p[LAX] = -0.85f;
                p[LAZ] = -0.2f;
                p[HX] = -0.08f;
                p[HY] = 0.15f * Mth.sin(t * 0.04f);
            }
            case STAR -> {   // sitting on the star like on a horse: legs forward, arms out for balance
                float sw = 0.06f * Mth.sin(t * 0.12f);
                p[RAZ] = 1.2f + sw;
                p[LAZ] = -1.2f - sw;
                p[RAX] = -0.25f;
                p[LAX] = -0.25f;
                p[RLX] = -1.4137f;
                p[LLX] = -1.4137f;
                p[RLZ] = 0.0785f;
                p[LLZ] = -0.0785f;
                p[HX] = -0.12f;
            }
            default -> { }
        }
        return p;
    }
}
