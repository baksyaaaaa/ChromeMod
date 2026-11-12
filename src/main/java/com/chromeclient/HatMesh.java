package com.chromeclient;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

import java.io.DataInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Smooth 3D hat models (lathes, tubes and swept shapes made by the generator, not boxes). Each model is a binary file in
 * assets/chromeclient/meshes: positions in model pixels, smooth normals and a colour per vertex, drawn as quads with a plain
 * white texture so the colours come from the vertices.
 */
public final class HatMesh {
    private static final Identifier WHITE = Identifier.fromNamespaceAndPath(ChromeClientMod.MOD_ID, "textures/entity/mesh_white.png");
    private static final HatMesh NONE = new HatMesh(new float[0], new byte[0], new short[0]);

    private final float[] pos;      // x,y,z per vertex
    private final byte[] data;      // nx,ny,nz,r,g,b per vertex
    private final short[] quads;    // 4 indices per quad

    private HatMesh(float[] pos, byte[] data, short[] quads) {
        this.pos = pos; this.data = data; this.quads = quads;
    }

    public static HatMesh get(int hat) {
        return load("hat_" + hat);
    }

    private static final Map<String, HatMesh> BY_NAME = new HashMap<>();

    /** any model of assets/chromeclient/meshes by file name (without .bin) */
    public static HatMesh load(String name) {
        HatMesh m = BY_NAME.get(name);
        if (m != null) return m;
        try (InputStream in = HatMesh.class.getResourceAsStream("/assets/" + ChromeClientMod.MOD_ID + "/meshes/" + name + ".bin")) {
            if (in == null) { BY_NAME.put(name, NONE); return NONE; }
            DataInputStream d = new DataInputStream(new java.io.BufferedInputStream(in));
            int nv = d.readInt();
            float[] p = new float[nv * 3];
            byte[] dt = new byte[nv * 6];
            for (int i = 0; i < nv; i++) {
                p[i * 3] = d.readFloat(); p[i * 3 + 1] = d.readFloat(); p[i * 3 + 2] = d.readFloat();
                d.readFully(dt, i * 6, 6);
            }
            int nq = d.readInt();
            short[] q = new short[nq * 4];
            for (int i = 0; i < q.length; i++) q[i] = d.readShort();
            if (name.equals("hat_2")) matte(dt);   // cat ears: soft, even lighting instead of shiny highlights
            m = new HatMesh(p, dt, q);
        } catch (Throwable t) {
            m = NONE;
        }
        BY_NAME.put(name, m);
        return m;
    }

    /** takes the shine off a mesh: normals are pulled towards one fixed direction (so every face is lit about the same
     *  and no face catches a bright highlight) and the colours get a little darker */
    private static void matte(byte[] dt) {
        // every vertex gets the SAME normal, so the whole ear is lit evenly and nothing can shine
        float nx = 0f, ny = 1f, nz = 0f;   // straight up: turning your head left/right never changes how it is lit
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        for (int i = 0; i + 5 < dt.length; i += 6) {
            dt[i] = (byte) Math.round(nx / len * 127f);
            dt[i + 1] = (byte) Math.round(ny / len * 127f);
            dt[i + 2] = (byte) Math.round(nz / len * 127f);
            for (int k = 3; k < 6; k++) dt[i + k] = (byte) Math.round((dt[i + k] & 255) * 0.88f);
        }
    }

    /** draws the model in the current pose (the pose is already at the head) */
    public void draw(PoseStack pose, SubmitNodeCollector queue, int light) {
        if (quads.length == 0) return;
        queue.submitCustomGeometry(pose, RenderTypes.entityCutoutNoCull(WHITE), (p, vc) -> {
            for (int i = 0; i < quads.length; i++) {
                int v = quads[i];
                vc.addVertex(p, pos[v * 3] / 16f, pos[v * 3 + 1] / 16f, pos[v * 3 + 2] / 16f)
                        .setColor(data[v * 6 + 3] & 255, data[v * 6 + 4] & 255, data[v * 6 + 5] & 255, 255)
                        .setUv(0.5f, 0.5f)
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(light)
                        .setNormal(p, data[v * 6] / 127f, data[v * 6 + 1] / 127f, data[v * 6 + 2] / 127f);
            }
        });
    }
}
