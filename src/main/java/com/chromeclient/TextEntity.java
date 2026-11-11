package com.chromeclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.joml.Vector3f;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * A real text entity in the world (the same thing a text_display is in vanilla), created only on your screen: nobody
 * else sees it and the server never hears of it. Because it is a normal world object with a fixed rotation, it is
 * drawn in 3D by the game itself: it never turns towards the camera, it hides behind blocks, and it looks right in
 * first and third person.
 *
 * Its settings (text, size, opacity, ...) live in the entity's data fields. Those fields are private, so they are
 * found by what they hold (their starting values), which works whatever the game calls them.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class TextEntity {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("ChromeText3D");
    private static boolean resolved = false;
    private static EntityDataAccessor aText, aLine, aBg, aOpacity, aFlags, aScale, aBright, aView;

    private final Display.TextDisplay e;
    private float lastScale = -1f;
    private int lastOpacity = -1;

    private TextEntity(Display.TextDisplay e) {
        this.e = e;
    }

    /** makes the entity and puts it into the world; null if the game does not allow it */
    public static TextEntity spawn(ClientLevel level, int id, double x, double y, double z) {
        try {
            Display.TextDisplay d = new Display.TextDisplay(EntityType.TEXT_DISPLAY, level);
            d.setId(id);
            d.setNoGravity(true);
            d.setPos(x, y, z);
            d.xo = d.xOld = x;
            d.yo = d.yOld = y;
            d.zo = d.zOld = z;
            resolve(d);
            TextEntity t = new TextEntity(d);
            t.set(aLine, 100000);                // never wrap the lines
            t.set(aBg, 0);                       // no dark box behind the letters
            t.set(aFlags, (byte) 1);             // letters get a soft shadow
            t.set(aBright, 15728880);            // always fully bright (full block + sky light)
            t.set(aView, 4.0f);                  // visible from far away
            level.addEntity(d);
            return t;
        } catch (Throwable ex) {
            LOG.error("could not create the 3D text", ex);
            return null;
        }
    }

    private static void resolve(Display.TextDisplay d) {
        if (resolved) return;
        resolved = true;
        SynchedEntityData data = d.getEntityData();
        for (Field f : Display.TextDisplay.class.getDeclaredFields()) {
            Object v = read(f, data);
            if (v == null) continue;
            if (v instanceof Component) aText = acc(f);
            else if (v instanceof Integer i && i == 200) aLine = acc(f);
            else if (v instanceof Integer i && i == 0x40000000) aBg = acc(f);
            else if (v instanceof Byte b && b == -1) aOpacity = acc(f);
            else if (v instanceof Byte b && b == 0) aFlags = acc(f);
        }
        boolean brightDone = false, viewDone = false;
        for (Field f : Display.class.getDeclaredFields()) {   // in the order they are declared
            Object v = read(f, data);
            if (v == null) continue;
            if (v instanceof Vector3f s && s.x == 1f && s.y == 1f && s.z == 1f && aScale == null) aScale = acc(f);
            else if (v instanceof Integer i && i == -1 && !brightDone) { aBright = acc(f); brightDone = true; }
            else if (v instanceof Float fl && fl == 1.0f && !viewDone) { aView = acc(f); viewDone = true; }
        }
        LOG.info("3D text fields found: text={} opacity={} scale={} flags={} bright={}",
                aText != null, aOpacity != null, aScale != null, aFlags != null, aBright != null);
    }

    private static EntityDataAccessor acc(Field f) {
        try {
            return (EntityDataAccessor) f.get(null);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object read(Field f, SynchedEntityData data) {
        try {
            if (!Modifier.isStatic(f.getModifiers()) || !EntityDataAccessor.class.isAssignableFrom(f.getType())) return null;
            f.setAccessible(true);
            EntityDataAccessor a = (EntityDataAccessor) f.get(null);
            return a == null ? null : data.get(a);
        } catch (Throwable t) {
            return null;
        }
    }

    private void set(EntityDataAccessor a, Object value) {
        if (a == null) return;
        try {
            e.getEntityData().set(a, value);
        } catch (Throwable ignored) {
            // that one setting is skipped
        }
    }

    public void text(Component c) {
        set(aText, c);
    }

    /** size of one letter pixel in blocks is 0.025 * this */
    public void scale(float s) {
        if (Math.abs(s - lastScale) < 0.0005f) return;
        lastScale = s;
        set(aScale, new Vector3f(s, s, s));
    }

    /** 0 = invisible, 1 = solid */
    public void opacity(float a) {
        int v = Math.max(26, Math.min(255, Math.round(a * 255f)));   // below ~26 the game treats it as opaque
        if (v == lastOpacity) return;
        lastOpacity = v;
        set(aOpacity, (byte) v);
    }

    public void bright(boolean full) {
        set(aBright, full ? 15728880 : -1);
    }

    public void shadow(boolean on) {
        set(aFlags, (byte) (on ? 1 : 0));
    }

    /** moves it (smoothly between ticks) and turns it; yaw is where its FRONT points, like a player's yaw */
    public void moveTo(double x, double y, double z, float yawDeg, float pitchDeg) {
        e.xo = e.xOld = e.getX();
        e.yo = e.yOld = e.getY();
        e.zo = e.zOld = e.getZ();
        e.setPos(x, y, z);
        e.yRotO = e.getYRot();
        e.xRotO = e.getXRot();
        e.setYRot(yawDeg);
        e.setXRot(pitchDeg);
    }

    public boolean aliveIn(ClientLevel level) {
        return !e.isRemoved() && e.level() == level;
    }

    public void remove() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && e.level() == mc.level && !e.isRemoved()) {
                mc.level.removeEntity(e.getId(), Entity.RemovalReason.DISCARDED);
            }
        } catch (Throwable ignored) {
            // it goes away with the world anyway
        }
    }
}
