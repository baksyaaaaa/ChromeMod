package com.chromeclient;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * The little buddy: a client-side-only player (nobody else sees it, the server never hears of it) that wears the skin
 * of any account you give it, smaller and with a big head. It walks after you and does idle animations when you stop,
 * like the sidekicks in Fortnite. All its moving happens inside its own tick (aiStep) so the game smooths it
 * between ticks and plays the normal walking animation.
 */
public final class BuddyPlayer extends RemotePlayer {
    private static final double NEAR = 1.9, FAR = 3.4;   // starts walking when you are further than FAR, stops inside NEAR

    /** which account's skin it wears, and the name shown above it */
    public final UUID skinUuid;
    public final String buddyName;
    private final Supplier<PlayerSkinRenderCache.RenderInfo> skin;

    private boolean started = false;
    private boolean walking = false;
    private double vx, vz;            // walking speed (blocks per tick)
    private double groundY;           // the last height you stood at on the ground (used if there is no block under it)
    private double baseY;             // its own height: it stands on whatever block is under it
    private int mode = BuddyEmotes.NONE;   // current emote
    private int lastMode = BuddyEmotes.NONE;
    private float size = 0.5f;
    private int modeTicks = 0;
    private int idleTicks = 0;
    private int nextIdleAt = 60;

    public BuddyPlayer(ClientLevel level, UUID skinUuid, String name, float size) {
        super(level, new GameProfile(UUID.randomUUID(), name));
        this.skinUuid = skinUuid;
        this.buddyName = name;
        // the same lookup the game uses for player heads: shows a default skin until the real one has loaded
        this.skin = Minecraft.getInstance().playerSkinRenderCache().createLookup(ResolvableProfile.createUnresolved(skinUuid));
        // a very high id so it can never clash with a real entity from the server
        setId(Buddy.BUDDY_ID);
        setNoGravity(true);
        this.noPhysics = true;
        setSize(size);
    }

    public void setSize(float size) {
        this.size = size;
        AttributeInstance scale = getAttribute(Attributes.SCALE);
        if (scale != null) scale.setBaseValue(size);
    }

    @Override
    public PlayerSkin getSkin() {
        return skin.get().playerSkin();
    }

    @Override
    public boolean isPushable() {
        return false;   // you can't shove it, and it can't shove you
    }

    @Override
    public void push(Entity other) {
        // never shoves anything
    }

    @Override
    public void push(double x, double y, double z) {
        // and nothing shoves it
    }

    // nothing can collide with it (no @Override on purpose: the exact method shape differs between versions)
    public boolean canBeCollidedWith(Entity other) {
        return false;
    }

    public boolean canBeCollidedWith() {
        return false;
    }

    public boolean canCollideWith(Entity other) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;   // your crosshair goes through it
    }

    @Override
    public void aiStep() {
        think();
        super.aiStep();
    }

    /** one tick of behaviour: follow you, or do something cute when it's next to you */
    private void think() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Vec3 pp = p.position();
        Vec3 here = position();
        if (!started) {
            started = true;
            groundY = baseY = here.y;
        }
        if (p.onGround()) groundY = pp.y;

        // remember last tick's body/head angles so the game can blend between ticks
        this.yBodyRotO = this.yBodyRot;
        this.yHeadRotO = this.yHeadRot;

        double dx = pp.x - here.x, dz = pp.z - here.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > 24 || pp.y - here.y > 4.5 || here.y - pp.y > 14) {   // left far behind or below you (teleport, tower...): pop back next to you
            Vec3 look = p.getLookAngle();
            setPos(pp.x - look.x * NEAR, pp.y, pp.z - look.z * NEAR);
            groundY = baseY = pp.y;
            vx = vz = 0;
            here = position();
            dx = pp.x - here.x;
            dz = pp.z - here.z;
            dist = Math.sqrt(dx * dx + dz * dz);
        }

        if (!walking && dist > FAR) walking = true;
        if (walking && dist < NEAR) walking = false;

        // speed: eases up and down instead of jumping, faster the further behind it is
        double wantSpeed = walking ? Math.min(0.40, 0.14 + (dist - NEAR) * 0.10) : 0.0;
        double ux = dist > 1.0E-4 ? dx / dist : 0, uz = dist > 1.0E-4 ? dz / dist : 0;
        vx += (ux * wantSpeed - vx) * 0.25;
        vz += (uz * wantSpeed - vz) * 0.25;
        double speed = Math.sqrt(vx * vx + vz * vz);
        boolean moving = speed > 0.02;

        // stands on the real ground under itself (not at your height): steps up stairs and slabs, drops off ledges
        double ground = groundAt(here.x + vx, here.z + vz, baseY);
        if (Double.isNaN(ground)) ground = groundY;
        baseY += Mth.clamp((ground - baseY) * 0.45, -0.7, 0.4);
        double hop = 0.0;

        float yaw = getYRot();
        float headYaw = yaw;
        float pitch = 0f;
        float towardYou = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
        double sink = 0.0;

        if (moving) {
            endIdle();
            yaw = turn(yaw, (float) (Mth.atan2(vz, vx) * (180.0 / Math.PI)) - 90.0f, 28f);
            headYaw = yaw;   // head stays with the body while walking
            setSprinting(speed > 0.24);
            hop = 0.05 * (size / 0.5f) * Math.abs(Math.sin(tickCount * 0.5));   // little bounce with every waddle step
        } else {
            setSprinting(false);
            vx = vz = 0;
            idleTicks++;
            yaw = turn(yaw, towardYou, 14f);   // faces you
            headYaw = yaw;
            if (mode == BuddyEmotes.NONE) {
                if (idleTicks > nextIdleAt) {   // pick an emote, never the same one twice in a row
                    int pick;
                    do {
                        pick = 1 + rnd.nextInt(BuddyEmotes.COUNT);
                    } while (pick == lastMode);
                    startEmote(pick);
                }
            } else {
                modeTicks++;
                hop = BuddyEmotes.hop(mode, modeTicks, size);
                sink = BuddyEmotes.sink(mode, modeTicks, size);
                if (modeTicks >= BuddyEmotes.length(mode)) endIdle();
            }
        }

        // if you walk into it, it slides out from under you instead of fighting you for the same spot
        double sx = 0, sz = 0;
        if (dist < 0.9 && dist > 1.0E-4) {
            double away = (0.9 - dist) * 0.35;
            sx = -dx / dist * away;
            sz = -dz / dist * away;
        }
        setPos(here.x + vx + sx, baseY + hop + sink, here.z + vz + sz);
        setDeltaMovement(Vec3.ZERO);
        setOnGround(hop <= 0.0);
        setYRot(yaw);
        setYBodyRot(yaw + (moving ? 9f * (float) Math.sin(tickCount * 0.5) : 0f));   // body sways side to side while walking
        Buddy.walking = moving;
        setYHeadRot(headYaw);
        setXRot(pitch);
    }

    /** height of the top of the ground at x,z: the highest solid surface up to 1.25 blocks above `from`, looking down; NaN if none */
    private double groundAt(double x, double z, double from) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        int top = Mth.floor(from + 1.5);
        for (int by = top; by > top - 28; by--) {
            BlockPos pos = new BlockPos(bx, by, bz);
            VoxelShape shape = level().getBlockState(pos).getCollisionShape(level(), pos);
            if (shape.isEmpty()) continue;
            double surface = by + shape.max(Direction.Axis.Y);
            if (surface <= from + 1.25) return surface;   // anything higher is a wall: keep looking lower
        }
        return Double.NaN;
    }

    private void startEmote(int emote) {
        mode = emote;
        modeTicks = 0;
        Buddy.emoteStart = this.tickCount;   // the model reads these to pose the buddy
        Buddy.emote = emote;
    }

    private void endIdle() {
        if (mode != BuddyEmotes.NONE) {
            lastMode = mode;
            nextIdleAt = 50 + ThreadLocalRandom.current().nextInt(110);
        }
        mode = BuddyEmotes.NONE;
        Buddy.emote = BuddyEmotes.NONE;
        modeTicks = 0;
        idleTicks = 0;
    }

    /** turn `from` towards `to` by at most `max` degrees (max 0 = stay as is) */
    private static float turn(float from, float to, float max) {
        if (max <= 0f) return from;
        return from + Mth.clamp(Mth.wrapDegrees(to - from), -max, max);
    }
}
