package com.chromeclient;

import com.mojang.blaze3d.vertex.VertexConsumer;

/** Passes everything to the real consumer but gives every vertex the same normal, so a whole piece is lit evenly. */
final class FixedNormal implements VertexConsumer {
    private final VertexConsumer to;
    private final float nx, ny, nz;

    FixedNormal(VertexConsumer to, float nx, float ny, float nz) {
        this.to = to; this.nx = nx; this.ny = ny; this.nz = nz;
    }

    @Override public VertexConsumer addVertex(float x, float y, float z) { to.addVertex(x, y, z); return this; }
    @Override public VertexConsumer setColor(int r, int g, int b, int a) { to.setColor(r, g, b, a); return this; }
    @Override public VertexConsumer setColor(int argb) { to.setColor(argb); return this; }
    @Override public VertexConsumer setUv(float u, float v) { to.setUv(u, v); return this; }
    @Override public VertexConsumer setUv1(int u, int v) { to.setUv1(u, v); return this; }
    @Override public VertexConsumer setUv2(int u, int v) { to.setUv2(u, v); return this; }
    @Override public VertexConsumer setLineWidth(float w) { to.setLineWidth(w); return this; }
    @Override public VertexConsumer setNormal(float x, float y, float z) { to.setNormal(nx, ny, nz); return this; }
}
