package com.chromeclient;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Draws your picked hat, cape and wings on your own player in the world.
 * Each piece is a small coloured box (a white texture tinted per box). Hats ride on the head; the cape and wings hang
 * from the body and move: the cape swings back when you run, the wings flap, the halo floats and spins.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class CosmeticLayer extends RenderLayer {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("ChromeCosmetics");
    private static boolean logged = false;
    private static final Identifier TEX = Identifier.fromNamespaceAndPath(ChromeClientMod.MOD_ID, "textures/entity/cosmetic.png");

    private static final int STATIC = 0, CAPE = 1, WING_L = 2, WING_R = 3, HALO = 4;

    private record Piece(ModelPart part, int tint, int role, Identifier tex) {}

    private static double otherSpeed(Minecraft mc, int id) {
        var e = mc.level.getEntity(id);
        return e == null ? 0.0 : Math.min(0.4, Math.hypot(e.getX() - e.xo, e.getZ() - e.zo));
    }

    private static long lastNanos = 0;
    private static float smoothSpeed = 0f, flapPhase = 0f, clock = 0f;

    private static final Map<Integer, List<Piece>> CACHE = new HashMap<>();

    public CosmeticLayer(RenderLayerParent parent) {
        super(parent);
    }

    /**
     * boxes (half-pixel units) turned into model parts that hang from a pivot (in Minecraft pixels).
     * All boxes of the same colour go into ONE model part, so a wing with 60 boxes is a handful of draw calls instead
     * of 60 (that was the lag). The list is only built once, the first time the piece is needed.
     */
    private static List<Piece> pieces(int key, Supplier<List<Cosmetics.Box>> boxes, int role, float px, float py, float pz) {
        List<Piece> got = CACHE.get(key);
        if (got != null) return got;
        Map<Integer, CubeListBuilder> byColor = new LinkedHashMap<>();
        int n = 0;
        for (Cosmetics.Box b : boxes.get()) {
            // later boxes are pushed out a hair so two faces never sit on the same plane (that made the textures flicker)
            float grow = 0.004f * (n++ % 24);
            byColor.computeIfAbsent(b.color(), c -> CubeListBuilder.create().texOffs(0, 0)).addBox(
                    b.x() / 2f - 8f - px, b.y() / 2f - 8f - py, b.z() / 2f - pz,
                    b.w() / 2f, b.h() / 2f, Math.max(0.4f, b.d() / 2f), new CubeDeformation(grow));
        }
        List<Piece> out = new ArrayList<>();
        for (Map.Entry<Integer, CubeListBuilder> e : byColor.entrySet()) {
            MeshDefinition mesh = new MeshDefinition();
            mesh.getRoot().addOrReplaceChild("b", e.getValue(), PartPose.offset(px, py, pz));
            out.add(new Piece(LayerDefinition.create(mesh, 16, 16).bakeRoot().getChild("b"), e.getKey(), role, TEX));
        }
        CACHE.put(key, out);
        return out;
    }

    /**
     * A wing made of layered painted planks (feathers, membranes, panels; see WingPlanks and the wing_N.png textures).
     * All of them hang under ONE model part, so a wing is a single draw.
     * side = -1 for the wing on the player's right (model -x), +1 for the other one.
     */
    private static List<Piece> featherWing(int key, int kind, int side) {
        List<Piece> got = CACHE.get(key);
        if (got != null) return got;
        float[][] pl = WingPlanks.planks(kind, side < 0 ? 1 : 0);
        int[] tex = WingPlanks.TEX[kind];
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot().addOrReplaceChild("w", CubeListBuilder.create(), PartPose.offset(side * 1.5f, 2.5f, 2.6f));
        for (int i = 0; i < pl.length; i++) {
            float[] f = pl[i];
            CubeListBuilder b = CubeListBuilder.create().texOffs((int) f[9], (int) f[10]).addBox(-f[0] / 2f, -f[1] / 2f, -f[2] / 2f, f[0], f[1], f[2]);
            root.addOrReplaceChild("p" + i, b, PartPose.offsetAndRotation(f[3], f[4], f[5], f[6], f[7], f[8]));
        }
        List<Piece> out = new ArrayList<>();
        out.add(new Piece(LayerDefinition.create(mesh, tex[0], tex[1]).bakeRoot().getChild("w"), 0xFFFFFFFF, side < 0 ? WING_L : WING_R,
                Identifier.fromNamespaceAndPath(ChromeClientMod.MOD_ID, "textures/entity/wing_" + kind + ".png")));
        CACHE.put(key, out);
        return out;
    }

    /**
     * A cape of cloth: four pieces hanging one under the other (each is the child of the one above), so when you run it
     * bends and sways like fabric instead of swinging as one board. Textured with a painted cape_N.png.
     */
    private static List<Piece> clothCape(int key, int kind) {
        List<Piece> got = CACHE.get(key);
        if (got != null) return got;
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition cur = mesh.getRoot().addOrReplaceChild("c", CubeListBuilder.create(), PartPose.offset(0f, 0f, 2.2f));
        for (int s = 0; s < 4; s++) {
            CubeListBuilder b = CubeListBuilder.create().texOffs(0, s * 5).addBox(-5f, 0f, 0f, 10f, 4f, 1f, new CubeDeformation(0f, 0f, -0.35f));
            cur = cur.addOrReplaceChild("s" + s, b, s == 0 ? PartPose.ZERO : PartPose.offset(0f, 4f, 0f));
        }
        List<Piece> out = new ArrayList<>();
        out.add(new Piece(LayerDefinition.create(mesh, 64, 32).bakeRoot().getChild("c"), 0xFFFFFFFF, CAPE,
                Identifier.fromNamespaceAndPath(ChromeClientMod.MOD_ID, "textures/entity/cape_" + kind + ".png")));
        CACHE.put(key, out);
        return out;
    }

    // ------------------------------------------------------------ elytra with the cape's pattern
    /** the two elytra wings, same shape as the game's (10 x 20 x 2, a bit puffed up), textured with the cape art */
    private static ModelPart elytra(int kind) {
        List<Piece> got = CACHE.get(7000 + kind);
        if (got != null) return got.get(0).part();
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot().addOrReplaceChild("e", CubeListBuilder.create(), PartPose.ZERO);
        CubeDeformation puff = new CubeDeformation(1.0f);
        root.addOrReplaceChild("l", CubeListBuilder.create().texOffs(22, 0).addBox(-10f, 0f, 0f, 10f, 20f, 2f, puff), PartPose.offsetAndRotation(5f, 0f, 0f, 0.2617994f, 0f, -0.2617994f));
        root.addOrReplaceChild("r", CubeListBuilder.create().texOffs(22, 0).mirror().addBox(0f, 0f, 0f, 10f, 20f, 2f, puff), PartPose.offsetAndRotation(-5f, 0f, 0f, 0.2617994f, 0f, 0.2617994f));
        List<Piece> out = new ArrayList<>();
        out.add(new Piece(LayerDefinition.create(mesh, 64, 32).bakeRoot().getChild("e"), 0xFFFFFFFF, STATIC,
                Identifier.fromNamespaceAndPath(ChromeClientMod.MOD_ID, "textures/entity/cape_" + kind + ".png")));
        CACHE.put(7000 + kind, out);
        return out.get(0).part();
    }

    /** poses the wings with the game's own elytra numbers (folded, tucked when sneaking, spread when gliding), as its elytra model does */
    private static void poseElytra(ModelPart root, AvatarRenderState av) {
        float yo = av.isCrouching ? 3f : 0f;
        ModelPart l = root.getChild("l"), r = root.getChild("r");
        l.setPos(5f, yo, 0f); l.setRotation(av.elytraRotX, av.elytraRotY, av.elytraRotZ);
        r.setPos(-5f, yo, 0f); r.setRotation(av.elytraRotX, -av.elytraRotY, -av.elytraRotZ);
    }

    /**
     * Swings the cape exactly like the game's own capes: one stiff board that hangs 6 degrees off the back and swings out
     * with how fast you run (capeFlap), leans into turns (capeLean) and rolls sideways when you strafe (capeLean2).
     * The numbers come from the game's own render state, the same ones its cape layer uses.
     */
    private static void vanillaCape(ModelPart root, AvatarRenderState av) {
        float flap = av.capeFlap, lean = av.capeLean, lean2 = av.capeLean2;
        if (av.isCrouching) flap = Math.max(0f, flap - 25f);   // the body already leans forward when you sneak
        float x = 6f + (lean / 2f + flap) * 0.5f;   // half the game's swing: it flew up too high when walking
        root.setRotation((float) Math.toRadians(Mth.clamp(x, 0f, 70f)), (float) Math.toRadians(-lean2 / 2f), (float) Math.toRadians(lean2 / 2f));
        ModelPart p = root;
        for (int s = 0; s < 4; s++) {
            p = p.getChild("s" + s);
            p.setRotation(0f, 0f, 0f);
        }
    }

    // A menu preview (the Chrome Client wardrobe / main menu character) switches this on while it draws its figure: then the
    // figure wears your saved picks (or the item you point at) even though there is no world and no player.
    private static volatile boolean previewing;
    private static volatile int pvHat = -1, pvCape = -1, pvWings = -1;

    /** called by the menu mod around drawing a preview figure; -1 = use the saved pick for that slot */
    public static void setPreview(boolean on, int hat, int cape, int wings) {
        previewing = on;
        pvHat = hat; pvCape = cape; pvWings = wings;
    }

    // The lobby's big character (the menu mod's 3D scene has its own little world with no player in it): the avatar with this
    // entity id is you, so it wears your picks, or the item you point at in the wardrobe.
    private static volatile int lobbyId = Integer.MIN_VALUE;
    private static volatile int lobHat = -1, lobCape = -1, lobWings = -1;

    public static void setLobby(int id, int hat, int cape, int wings) {
        lobbyId = id;
        lobHat = hat; lobCape = cape; lobWings = wings;
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector queue, int light, EntityRenderState state, float yRot, float xRot) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (!(state instanceof AvatarRenderState av)) return;
            final boolean lobby = av.id == lobbyId;   // the lobby scene swaps in its own player, so mc.player is NOT null there
            final boolean preview = previewing || lobby;
            HatMesh.preview = preview;
            if (!preview && (mc.player == null || mc.level == null)) return;
            var self = preview ? null : mc.level.getEntity(av.id);
            if (self != null && self.isInvisible()) return;   // invisible players show nothing of theirs
            boolean local = preview || av.id == mc.player.getId();
            int hat, cape, wings;
            double speed;
            if (preview) {
                int oh = lobby ? lobHat : pvHat, oc = lobby ? lobCape : pvCape, ow = lobby ? lobWings : pvWings;
                hat = oh >= 0 ? oh : Cosmetics.chosen[0];
                cape = oc >= 0 ? oc : Cosmetics.chosen[1];
                wings = ow >= 0 ? ow : Cosmetics.chosen[2];
                light = 0xF000F0;   // a menu figure has no world light: draw the pieces fully lit so they are not dark
            } else if (local) {
                hat = Cosmetics.chosen[0]; cape = Cosmetics.chosen[1]; wings = Cosmetics.chosen[2];
            } else {   // another player using the mod: what the service says they wear
                if (!CosmeticSync.on() || !(mc.level.getEntity(av.id) instanceof net.minecraft.world.entity.player.Player other)) return;
                if (other.distanceToSqr(mc.player) > 48.0 * 48.0) return;   // too far to see: don't draw (saves frames in crowds)
                CosmeticSync.Remote r = CosmeticSync.get(other.getUUID());
                if (r == null) return;
                hat = Extras.seeHats ? r.hat() : 0; cape = Extras.seeCapes ? r.cape() : 0; wings = Extras.seeWings ? r.wings() : 0;
            }
            if (!(getParentModel() instanceof PlayerModel pm)) {
                if (!logged) { logged = true; LOG.info("layer reached a player but model is {}", getParentModel().getClass().getName()); }
                return;
            }
            if (!logged) { logged = true; LOG.info("layer drawing: hat={} cape={} wings={}", hat, cape, wings); }

            // the clock and the speed are smoothed: the raw speed jumps every tick, which made the wings (and cape) jerk
            long nanos = System.nanoTime();
            if (lastNanos != 0 && nanos != lastNanos) {
                float dt = Math.min(0.1f, (nanos - lastNanos) / 1e9f);
                float target = mc.player == null ? 0f : (float) mc.player.getDeltaMovement().horizontalDistance();
                smoothSpeed += (target - smoothSpeed) * Math.min(1f, dt * 6f);
                flapPhase += dt * (2.4f + smoothSpeed * 9f);      // phase adds up, so a changing speed never makes it jump
                clock += dt;
            }
            lastNanos = nanos;
            if (hat == 0 && cape == 0 && wings == 0) return;
            float t = clock % 6000f;
            speed = local ? smoothSpeed : otherSpeed(mc, av.id);

            if (hat > 0) {
                pose.pushPose();
                pm.head.translateAndRotate(pose);
                int hl = light;
                if (hat == 1 || hat == 4) {   // halo and void ring float, spin slowly and glow
                    pose.translate(0f, -((float) Math.sin(t * 2.0) * 0.7f + 0f) / 16f, 0f);
                    pose.mulPose(com.mojang.math.Axis.YP.rotation(hat == 4 ? -t * 0.6f : t * 0.9f));
                    hl = 0xF000F0;
                }
                HatMesh.get(hat).draw(pose, queue, hl);
                pose.popPose();
            }
            if (cape > 0 || wings > 0) {
                pose.pushPose();
                pm.body.translateAndRotate(pose);
                boolean elytraOn = cape > 0 && !preview && mc.level.getEntity(av.id) instanceof LivingEntity wearer && wearer.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA);
                if (elytraOn) {   // the cape's pattern on the elytra, like the game does for normal capes
                    ModelPart root = elytra(cape);
                    poseElytra(root, av);
                    av.chestEquipment = ItemStack.EMPTY;   // this layer runs first: the game's own grey elytra is not drawn under ours
                    pose.popPose();
                    pose.pushPose();
                    pose.translate(0f, 0f, 0.125f);
                    queue.submitModelPart(root, pose, RenderTypes.entityCutoutNoCull(Identifier.fromNamespaceAndPath(ChromeClientMod.MOD_ID, "textures/entity/cape_" + cape + ".png")), light, OverlayTexture.NO_OVERLAY,
                            null, false, false, 0xFFFFFFFF, null, 0);
                    pose.popPose();
                    pose.pushPose();
                    pm.body.translateAndRotate(pose);
                }
                if (cape > 0 && !elytraOn) {
                    List<Piece> cp = clothCape(2000 + cape, cape);
                    for (Piece p : cp) vanillaCape(p.part(), av);
                    // every piece is lit like the TOP of the player's head (the brightest face): one fixed normal pointing up
                    // (menu figures: pointing at the camera, the way the menu is lit)
                    final int lt = light;
                    final boolean menu = preview;
                    org.joml.Vector3f fn = menu ? pose.last().transformNormal(0f, 0f, 1f, new org.joml.Vector3f()) : new org.joml.Vector3f(0f, 1f, 0f);
                    for (Piece p : cp) {
                        final ModelPart part = p.part();
                        final int tint = p.tint();
                        queue.submitCustomGeometry(pose, RenderTypes.entitySolid(p.tex()), (pp, vc) -> {
                            PoseStack ps = new PoseStack();
                            ps.last().set(pp);
                            part.render(ps, new FixedNormal(vc, fn.x, fn.y, fn.z), lt, OverlayTexture.NO_OVERLAY, tint);
                        });
                    }
                }
                if (wings > 0) {
                    final int w = wings;
                    float flap = 0.38f + 0.2f * (float) Math.sin(flapPhase);
                    pose.pushPose();
                    pose.translate(0f, 0.2f, 0f);   // sits on the back, lower between the shoulder blades
                    for (int side = -1; side <= 1; side += 2) {   // -1 = the wing on your right, +1 = the other one
                        pose.pushPose();
                        pose.translate(side * 1.5f / 16f, 2.5f / 16f, 2.6f / 16f);   // the shoulder hinge
                        pose.mulPose(com.mojang.math.Axis.YP.rotation(-side * flap));
                        HatMesh.load("wing_" + w + (side < 0 ? "_R" : "_L")).draw(pose, queue, light);
                        pose.popPose();
                    }
                    pose.popPose();
                }
                pose.popPose();
            }
        } catch (Throwable t) {
            if (!logged) { logged = true; LOG.error("cosmetics failed", t); }
        }
    }

    private static void draw(PoseStack pose, SubmitNodeCollector queue, int light, List<Piece> list) {
        for (Piece p : list) {
            queue.submitModelPart(p.part(), pose, RenderTypes.entityCutoutNoCull(p.tex()), light, OverlayTexture.NO_OVERLAY,
                    null, false, false, p.tint(), null, 0);
        }
    }
}
