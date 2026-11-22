package com.chromeclient;

import com.mojang.realmsclient.RealmsMainScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Chrome Client's main menu. It is laid out on a 1874 x 1050 design (a modern client menu): everything is placed in real
 * screen pixels and scaled by (window height / 1050). The artwork (buttons, icons, texts, logo) comes from one smooth
 * picture (menu_atlas.png, made with the Poppins font) and the changing texts (server names, your name) use the same
 * font. The clickable parts are normal (invisible) game buttons, so clicks and keys work like in the normal menu.
 */
public final class ChromeTitleScreen extends Screen {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("ChromeTitle");
    private static float yaw = -35f;          // how far the model is turned (kept when the menu is opened again)
    private boolean logged, logged3d, loggedDrag;
    private static final Identifier ATLAS = Identifier.fromNamespaceAndPath(ChromeClientMod.MOD_ID, "textures/gui/menu_atlas.png");
    private static final FontDescription MEDIUM = new FontDescription.Resource(Identifier.fromNamespaceAndPath(ChromeClientMod.MOD_ID, "poppins"));
    private static final FontDescription BOLD = new FontDescription.Resource(Identifier.fromNamespaceAndPath(ChromeClientMod.MOD_ID, "poppins_bold"));
    private static final int GREY = 0xFFC8C8CD, WHITE = 0xFFFFFFFF;

    private static final class Item {
        int[] bg, bgH, icon, label;
        float x, y, w, h;      // design coordinates (1874 x 1050)
        int anchor;            // 0 = x measured from the left, 1 = from the middle (937), 2 = from the right
        int kind;              // 0 button, 1 server card, 2 square, 3 header, 4 account
        String name, sub;
        byte[] iconBytes;                 // the server's own icon (png) if it has one
        net.minecraft.resources.Identifier iconTex;
        boolean iconTried;
        Button hit;
    }

    private final List<Item> items = new ArrayList<>();
    private double gs = 1;
    /** the other main-menu look: your skin walks in, stops, the camera zooms and your saved servers appear */
    private static boolean sceneMode;
    private long sceneStart = System.currentTimeMillis();
    private float sceneP = 1f;   // 0..1: how far the server list has faded in

    public ChromeTitleScreen() {
        super(Component.literal("Chrome Client"));
    }

    // ---- design -> real pixels ----
    private float f() {
        return minecraft.getWindow().getHeight() / 1050f;
    }

    private float rx(float x, int anchor) {
        float W = minecraft.getWindow().getWidth(), f = f();
        return switch (anchor) {
            case 1 -> W / 2f + (x - 937f) * f;
            case 2 -> W - (1874f - x) * f;
            default -> x * f;
        };
    }

    private Item add(int kind, int anchor, float x, float y, float w, float h, int[] bg, int[] bgH, int[] icon, int[] label,
                     String name, String sub, Runnable click) {
        Item it = new Item();
        it.kind = kind; it.anchor = anchor; it.x = x; it.y = y; it.w = w; it.h = h;
        it.bg = bg; it.bgH = bgH; it.icon = icon; it.label = label; it.name = name; it.sub = sub;
        if (click != null) {
            float f = f();
            it.hit = Button.builder(Component.literal(name == null ? "" : name), btn -> click.run())
                    .bounds(Math.round(rx(x, anchor) / (float) gs), Math.round(y * f / (float) gs),
                            Math.max(1, Math.round(w * f / (float) gs)), Math.max(1, Math.round(h * f / (float) gs))).build();
            it.hit.setAlpha(0f);   // invisible: we draw it ourselves, the game still handles the clicks
            addRenderableWidget(it.hit);
        }
        items.add(it);
        return it;
    }

    @Override
    protected void init() {
        items.clear();
        gs = Math.max(1.0, minecraft.getWindow().getGuiScale());
        sceneStart = System.currentTimeMillis();
        if (sceneMode) { initScene(); return; }
        add(5, 2, 1561f, 24f, 179f, 38f, MenuSprites.BG_HALF, MenuSprites.BG_HALF_H, null, null, "Servers", null, () -> { sceneMode = true; minecraft.setScreen(new ChromeTitleScreen()); });
        // the middle column
        add(0, 1, 751f, 528f, 372f, 38f, MenuSprites.BG_WIDE, MenuSprites.BG_WIDE_H, MenuSprites.I_PERSON, MenuSprites.T_SINGLE, null, null,
                () -> minecraft.setScreen(new SelectWorldScreen(this)));
        add(0, 1, 751f, 578.5f, 372f, 38f, MenuSprites.BG_WIDE, MenuSprites.BG_WIDE_H, MenuSprites.I_PEOPLE, MenuSprites.T_MULTI, null, null,
                () -> minecraft.setScreen(new JoinMultiplayerScreen(this)));
        add(0, 1, 751f, 629f, 372f, 38f, MenuSprites.BG_WIDE, MenuSprites.BG_WIDE_H, MenuSprites.I_GLOBE, MenuSprites.T_REALMS, null, null,
                () -> minecraft.setScreen(new RealmsMainScreen(this)));
        add(0, 1, 751f, 679f, 179f, 38f, MenuSprites.BG_HALF, MenuSprites.BG_HALF_H, MenuSprites.I_GEAR, MenuSprites.T_SETTINGS, null, null,
                () -> minecraft.setScreen(new OptionsScreen(this, minecraft.options)));
        add(0, 1, 944f, 679f, 179f, 38f, MenuSprites.BG_HALF, MenuSprites.BG_HALF_H, MenuSprites.I_MODS, MenuSprites.T_MODS, null, null,
                () -> { if (ChromeClientMod.hudInstance != null) minecraft.setScreen(new ClickGui(ChromeClientMod.hudInstance)); });

        // top right
        add(2, 2, 1764f, 24f, 36f, 36f, MenuSprites.BG_SQ, MenuSprites.BG_SQ_H, MenuSprites.I_FULL, null, null, null,
                () -> minecraft.getWindow().toggleFullScreen());
        add(2, 2, 1811f, 24f, 36f, 36f, MenuSprites.BG_SQ, MenuSprites.BG_SQ_H, MenuSprites.I_QUIT, null, null, null,
                () -> minecraft.stop());

        // left: Quickplay header and your saved servers
        add(3, 0, 58f, 340f, 254f, 36f, MenuSprites.BG_SIDE, MenuSprites.BG_SIDE, MenuSprites.I_QUICK, MenuSprites.T_QUICK, null, null, null);
        try {
            ServerList sl = new ServerList(minecraft);
            sl.load();
            int n = Math.min(3, sl.size());
            for (int i = 0; i < n; i++) {
                ServerData d = sl.get(i);
                Item card = add(1, 0, 58f, 388f + 65f * i, 254f, 53f, MenuSprites.BG_CARD, MenuSprites.BG_CARD_H, null, null, d.name, d.ip,
                        () -> ConnectScreen.startConnecting(this, minecraft, ServerAddress.parseString(d.ip), d, false, null));
                try { card.iconBytes = d.getIconBytes(); } catch (Throwable ignored) { }
            }
        } catch (Throwable ignored) {
            // no server list: the left side just stays with the header
        }

        // right: your account and Host World
        add(4, 2, 1561f, 530f, 254f, 37f, MenuSprites.BG_SIDE, MenuSprites.BG_SIDE, null, null, minecraft.getUser().getName(), null, null);
        add(0, 2, 1561f, 580f, 254f, 37f, MenuSprites.BG_SIDE, MenuSprites.BG_SIDE_H, MenuSprites.I_HOST, MenuSprites.T_HOST, null, null,
                () -> minecraft.setScreen(new SelectWorldScreen(this)));
    }

    /** the walking-skin scene: buttons and your saved servers */
    private void initScene() {
        add(5, 2, 1561f, 24f, 179f, 38f, MenuSprites.BG_HALF, MenuSprites.BG_HALF_H, null, null, "Main menu", null, () -> { sceneMode = false; minecraft.setScreen(new ChromeTitleScreen()); });
        add(2, 2, 1764f, 24f, 36f, 36f, MenuSprites.BG_SQ, MenuSprites.BG_SQ_H, MenuSprites.I_FULL, null, null, null,
                () -> minecraft.getWindow().toggleFullScreen());
        add(2, 2, 1811f, 24f, 36f, 36f, MenuSprites.BG_SQ, MenuSprites.BG_SQ_H, MenuSprites.I_QUIT, null, null, null,
                () -> minecraft.stop());
        try {
            ServerList sl = new ServerList(minecraft);
            sl.load();
            int n = Math.min(7, sl.size());
            for (int i = 0; i < n; i++) {
                ServerData d = sl.get(i);
                Item card = add(1, 1, 1003f, 470f + 65f * i, 254f, 53f, MenuSprites.BG_CARD, MenuSprites.BG_CARD_H, null, null, d.name, d.ip,
                        () -> ConnectScreen.startConnecting(this, minecraft, ServerAddress.parseString(d.ip), d, false, null));
                try { card.iconBytes = d.getIconBytes(); } catch (Throwable ignored) { }
                card.hit.active = false;   // only clickable once the list has appeared
            }
        } catch (Throwable ignored) {
            // no server list
        }
    }

    // ---- drawing ----

    /** draws a piece of the atlas; (x, y) in real pixels, pieces are baked at twice the design size */
    private void spr(GuiGraphics g, int[] s, float x, float y) {
        float f = f();
        g.pose().pushMatrix();
        g.pose().scale((float) (1.0 / gs), (float) (1.0 / gs));
        g.pose().translate(x, y);
        g.pose().scale(f / 2f, f / 2f);
        g.blit(RenderPipelines.GUI_TEXTURED, ATLAS, 0, 0, (float) s[0], (float) s[1], s[2], s[3], MenuSprites.ATLAS_W, MenuSprites.ATLAS_H);
        g.pose().popMatrix();
    }

    /** the same, placed by design coordinates; w/h of the piece in design pixels = texture size / 2 */
    private void sprAt(GuiGraphics g, int[] s, float dx, float dy, int anchor) {
        spr(g, s, rx(dx, anchor), dy * f());
    }

    private static float dw(int[] s) {
        return s[2] / 2f;
    }

    private static float dh(int[] s) {
        return s[3] / 2f;
    }

    /** a text in the menu font: (x, cy) real pixels, left aligned, centred on cy; size in design pixels */
    private void dyn(GuiGraphics g, String text, float x, float cy, float size, boolean bold, int color, float maxW) {
        float f = f(), k = size * f / 16f;
        Style st = Style.EMPTY.withFont(bold ? BOLD : MEDIUM);
        Component c = Component.literal(text).withStyle(st);
        if (font.width(c) * k > maxW) {
            String t = text;
            while (t.length() > 1 && font.width(Component.literal(t + "...").withStyle(st)) * k > maxW) t = t.substring(0, t.length() - 1);
            c = Component.literal(t + "...").withStyle(st);
        }
        g.pose().pushMatrix();
        g.pose().scale((float) (1.0 / gs), (float) (1.0 / gs));
        g.pose().translate(x, cy - 1.3f * k);
        g.pose().scale(k, k);
        g.drawString(font, c, 0, 0, color, false);
        g.pose().popMatrix();
    }

    private float textW(String text, float size, boolean bold) {
        return font.width(Component.literal(text).withStyle(Style.EMPTY.withFont(bold ? BOLD : MEDIUM))) * size / 16f;   // design pixels
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);   // blurred panorama
        g.fill(0, 0, width, height, 0x1CFFFFFF);   // a light wash: lifts the dark parts of the moving background much more than the bright ones
        float f = f();

        if (sceneMode) {
            renderScene(g);
        } else if (!model3d(g)) profile(g);
        // logo and name in the middle
        if (!sceneMode) {   // the glassy logo, centred on where the old one was
            float lw = 112f * f;
            g.pose().pushMatrix();
            g.pose().scale((float) (1.0 / gs), (float) (1.0 / gs));
            Logo.draw(g, rx(937f - 56f, 1), (362f - 56f) * f, lw);
            g.pose().popMatrix();
        }
        if (!sceneMode) sprAt(g, MenuSprites.T_TITLE, 937f - dw(MenuSprites.T_TITLE) / 2f, 466f - dh(MenuSprites.T_TITLE) / 2f, 1);

        for (Item it : items) {
            float x = rx(it.x, it.anchor), y = it.y * f;
            if (sceneMode && it.kind == 1) {
                if (sceneP <= 0f) continue;
                x += (1f - sceneP) * 90f * f;   // slides in from the right
                if (it.hit != null) it.hit.active = sceneP >= 0.98f;
            }
            boolean hover = it.hit != null && it.hit.isHoveredOrFocused() && it.hit.active;
            spr(g, hover ? it.bgH : it.bg, x, y);
            switch (it.kind) {
                case 0 -> {    // icon + label as a group in the middle
                    float gw = 16f + 9f + dw(it.label), gx = (it.w - gw) / 2f;
                    spr(g, it.icon, x + gx * f, y + (it.h - 16f) / 2f * f);
                    spr(g, it.label, x + (gx + 25f) * f, y + (it.h - 22f) / 2f * f);
                }
                case 2 -> spr(g, it.icon, x + (it.w - 16f) / 2f * f, y + (it.h - 16f) / 2f * f);
                case 5 -> {    // a text button
                    float tw = textW(it.name, 14f, true);
                    dyn(g, it.name, x + (it.w - tw) / 2f * f, y + it.h / 2f * f, 14f, true, WHITE, (it.w - 20f) * f);
                }
                case 3 -> {    // Quickplay header
                    spr(g, it.icon, x + 19f * f, y + (it.h - 16f) / 2f * f);
                    spr(g, it.label, x + (it.w - dw(it.label)) / 2f * f, y + (it.h - 22f) / 2f * f);
                    spr(g, MenuSprites.CHEV_D, x + (it.w - 28f) * f, y + (it.h - 6f) / 2f * f);
                }
                case 1 -> {    // saved server
                    float bx = x + 10f * f, by = y + 4.5f * f, bs = 44f * f;
                    int hue = Math.abs(it.name.hashCode());
                    int col = 0xFF000000 | (Ui.lerp(0xFF2F6BFF, 0xFF8B5CF6, (hue % 100) / 100f) & 0xFFFFFF);
                    net.minecraft.resources.Identifier icon = serverIcon(it);
                    if (icon != null) {
                        g.pose().pushMatrix();
                        g.pose().scale((float) (1.0 / gs), (float) (1.0 / gs));
                        g.pose().translate(bx, by);
                        g.pose().scale(bs / 64f, bs / 64f);
                        g.blit(RenderPipelines.GUI_TEXTURED, icon, 0, 0, 0f, 0f, 64, 64, 64, 64);
                        g.pose().popMatrix();
                    } else {
                        roundBox(g, bx, by, bs, bs, 8f * f, col);
                        String ini = it.name.isEmpty() ? "?" : it.name.substring(0, 1).toUpperCase();
                        dyn(g, ini, bx + bs / 2f - textW(ini, 17f, true) * f / 2f, by + bs / 2f, 17f, true, WHITE, 40f * f);
                    }
                    dyn(g, it.name, x + 59f * f, y + 17f * f, 15f, true, WHITE, (it.w - 59f - 36f) * f);
                    dyn(g, it.sub, x + 59f * f, y + 37f * f, 13f, false, GREY, (it.w - 59f - 36f) * f);
                    spr(g, MenuSprites.CHEV_R, x + (it.w - 22f) * f, y + (it.h - 10f) / 2f * f);
                }
                case 4 -> {    // account
                    float tw = textW(it.name, 15f, true);
                    dyn(g, it.name, x + (it.w - tw) / 2f * f, y + it.h / 2f * f, 15f, true, WHITE, (it.w - 70f) * f);
                }
                default -> { }
            }
        }

        // bottom corners
        g.pose().pushMatrix();
        g.pose().scale((float) (1.0 / gs), (float) (1.0 / gs));
        Logo.drawC(g, (45f - 15f) * f, (1021f - 15f) * f, 30f * f);
        g.pose().popMatrix();
        spr(g, MenuSprites.T_FOOT, 60f * f, 1012f * f);
        String ver = FabricLoader.getInstance().getModContainer("chromeclient")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
        dyn(g, "Fabric 1.21.11  " + ver, (60f + dw(MenuSprites.T_FOOT) + 12f) * f, 1021f * f, 12f, false, GREY, 300f * f);
        spr(g, MenuSprites.T_COPY, rx(1874f - 34f - dw(MenuSprites.T_COPY), 2), 1014f * f);
    }

    // ---- your real skin, fetched straight from Mojang (the game's own lookup can hand back a default skin on this screen) ----
    private static volatile com.mojang.blaze3d.platform.NativeImage pendingSkin;
    private static volatile boolean pendingSlim;
    private static boolean skinFetchStarted;
    private static Identifier ownTex;
    private static boolean ownSlim;

    private void startSkinFetch() {
        if (skinFetchStarted) return;
        skinFetchStarted = true;
        final String id = minecraft.getUser().getProfileId().toString().replace("-", "");
        Thread t = new Thread(() -> {
            try {
                java.net.http.HttpClient hc = java.net.http.HttpClient.newHttpClient();
                String body = hc.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + id)).build(),
                        java.net.http.HttpResponse.BodyHandlers.ofString()).body();
                com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(body).getAsJsonObject();
                String value = root.getAsJsonArray("properties").get(0).getAsJsonObject().get("value").getAsString();
                com.google.gson.JsonObject skin = com.google.gson.JsonParser.parseString(new String(java.util.Base64.getDecoder().decode(value)))
                        .getAsJsonObject().getAsJsonObject("textures").getAsJsonObject("SKIN");
                String url = skin.get("url").getAsString();
                boolean slim = skin.has("metadata") && skin.getAsJsonObject("metadata").has("model")
                        && skin.getAsJsonObject("metadata").get("model").getAsString().equals("slim");
                byte[] png = hc.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(url)).build(),
                        java.net.http.HttpResponse.BodyHandlers.ofByteArray()).body();
                com.mojang.blaze3d.platform.NativeImage img = com.mojang.blaze3d.platform.NativeImage.read(new java.io.ByteArrayInputStream(png));
                pendingSlim = slim;
                pendingSkin = img;
                LOG.info("fetched your skin from Mojang: {} ({}x{}, {})", url, img.getWidth(), img.getHeight(), slim ? "slim" : "wide");
            } catch (Throwable e) {
                LOG.warn("could not fetch your skin from Mojang, using the game's lookup", e);
            }
        }, "chrome-skin-fetch");
        t.setDaemon(true);
        t.start();
    }

    /** turns a finished download into a texture (on the render thread) */
    private void pollSkin() {
        com.mojang.blaze3d.platform.NativeImage img = pendingSkin;
        if (img == null) return;
        pendingSkin = null;
        try {
            Identifier id = Identifier.fromNamespaceAndPath(ChromeClientMod.MOD_ID, "own_skin");
            minecraft.getTextureManager().register(id, new net.minecraft.client.renderer.texture.DynamicTexture(() -> "chrome_own_skin", img));
            ownTex = id;
            ownSlim = pendingSlim;
        } catch (Throwable e) {
            LOG.warn("could not register your skin texture", e);
        }
    }

    private net.minecraft.client.model.player.PlayerModel model3dSlim;

    private net.minecraft.world.entity.player.PlayerSkin cachedSkin;
    private java.lang.reflect.Method skinMethod;

    /** your skin; found by method signature (GameProfile -> skin / future of skin) so it does not depend on the exact method name */
    private net.minecraft.world.entity.player.PlayerSkin skin() throws Exception {
        Object sm = minecraft.getSkinManager();
        if (skinMethod == null) {
            for (java.lang.reflect.Method m : sm.getClass().getMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (p.length != 1 || p[0] != com.mojang.authlib.GameProfile.class) continue;
                Class<?> r = m.getReturnType();
                if (r == net.minecraft.world.entity.player.PlayerSkin.class || r == java.util.concurrent.CompletableFuture.class || r == java.util.Optional.class) {
                    skinMethod = m;
                    break;
                }
            }
            if (skinMethod == null) return null;
        }
        Object o = skinMethod.invoke(sm, minecraft.getGameProfile());
        if (o instanceof java.util.concurrent.CompletableFuture<?> cf) o = cf.getNow(null);
        if (o instanceof java.util.Optional<?> op) o = op.orElse(null);
        if (o instanceof net.minecraft.world.entity.player.PlayerSkin ps) cachedSkin = ps;
        return cachedSkin;
    }


    private net.minecraft.resources.Identifier skinTex;

    /** the texture of your skin: found by type (the skin object holds an asset whose path is an Identifier) */
    private net.minecraft.resources.Identifier skinTexture() {
        if (skinTex != null) return skinTex;
        try {
            net.minecraft.world.entity.player.PlayerSkin skin = skin();
            if (skin != null) skinTex = skin.body().texturePath();
        } catch (Throwable ignored) {
            // no skin picture: the model is just not drawn
        }
        return skinTex;
    }

    /** one rectangle of the skin picture, drawn flipped so the profile looks to the left; (x, y) real pixels, k real pixels per skin pixel */
    private void part(GuiGraphics g, net.minecraft.resources.Identifier tex, int u, int v, int w, int h, float x, float y, float k) {
        g.pose().pushMatrix();
        g.pose().scale((float) (1.0 / gs), (float) (1.0 / gs));
        g.pose().translate(x + w * k, y);
        g.pose().scale(-k, k);
        g.blit(RenderPipelines.GUI_TEXTURED, tex, 0, 0, (float) u, (float) v, w, h, 64, 64);
        g.pose().popMatrix();
    }

    private net.minecraft.client.model.player.PlayerModel model3d;

    /** your real player model (3D, same renderer the game uses in its own menus), cropped to head and shoulders */
    private boolean model3d(GuiGraphics g) {
        try {
            startSkinFetch();
            pollSkin();
            net.minecraft.resources.Identifier tex;
            net.minecraft.client.model.player.PlayerModel mdl;
            if (model3d == null) {
                model3d = new net.minecraft.client.model.player.PlayerModel(
                        minecraft.getEntityModels().bakeLayer(net.minecraft.client.model.geom.ModelLayers.PLAYER), false);
                model3dSlim = new net.minecraft.client.model.player.PlayerModel(
                        minecraft.getEntityModels().bakeLayer(net.minecraft.client.model.geom.ModelLayers.PLAYER_SLIM), true);
            }
            if (ownTex != null) {
                tex = ownTex;
                mdl = ownSlim ? model3dSlim : model3d;
            } else {   // until the download finishes: the game's own lookup
                net.minecraft.world.entity.player.PlayerSkin skin = skin();
                if (skin == null) return false;
                tex = skin.body().texturePath();
                mdl = model3d;
            }
            float f = f();
            float hr = 490f * f;                                   // height of the whole body area, real pixels
            float cx = rx(1688f, 2), y0 = 330f * f;
            int x0 = Math.round((cx - hr / 2f) / (float) gs), x1 = Math.round((cx + hr / 2f) / (float) gs);
            int ya = Math.round(y0 / (float) gs), yb = Math.round((y0 + hr) / (float) gs);
            float scale = 0.97f * (yb - ya) / 2.125f;
            // only head and shoulders are shown: cut everything below the box above the account button
            g.enableScissor(Math.round(rx(1561f, 2) / (float) gs), Math.round(300f * f / (float) gs),
                    Math.round(rx(1561f + 254f, 2) / (float) gs), Math.round(529f * f / (float) gs));
            g.submitSkinRenderState(mdl, tex, scale, -5f, yaw, -0.0625f, x0, ya, x1, yb);
            if (!logged3d) { logged3d = true; LOG.info("3D model submitted: texture={} box=({},{})-({},{}) scale={} guiScale={}", tex, x0, ya, x1, yb, scale, gs); }
            // soft shadow under the shoulders: stacked gradients that get narrower, so the cut edge melts into the background
            float gl = rx(1561f, 2) / (float) gs, gr = rx(1561f + 254f, 2) / (float) gs;
            float gcx = (gl + gr) / 2f, gh = (gr - gl) / 2f;
            int gTop = Math.round((529f - 120f) * f / (float) gs), gBot = Math.round(529f * f / (float) gs);
            for (int i = 0; i < 14; i++) {
                float half = gh * (1f - i * 0.045f);
                g.fillGradient(Math.round(gcx - half), gTop, Math.round(gcx + half), gBot, 0x00000000, 0x0E000000);
            }
            g.disableScissor();
            return true;
        } catch (Throwable t) {
            if (!logged) { logged = true; LOG.warn("3D model on the main menu failed, drawing the flat profile instead", t); }
            return false;
        }
    }

    private static float ease(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }

    private static float clamp01(float t) {
        return Math.max(0f, Math.min(1f, t));
    }

    /** turns the limbs of the model like a walk cycle; amount 0 = standing, 1 = full stride */
    private void pose(net.minecraft.client.model.player.PlayerModel m, float phase, float amount) {
        try {
            net.minecraft.client.model.geom.ModelPart root = m.root();
            float sw = (float) Math.sin(phase) * amount;
            net.minecraft.client.model.geom.ModelPart ra = root.getChild("right_arm"), la = root.getChild("left_arm"),
                    rl = root.getChild("right_leg"), ll = root.getChild("left_leg");
            ra.xRot = -sw * 0.85f; la.xRot = sw * 0.85f;
            rl.xRot = sw * 1.0f; ll.xRot = -sw * 1.0f;
            root.getChild("right_sleeve").xRot = ra.xRot; root.getChild("left_sleeve").xRot = la.xRot;
            root.getChild("right_pants").xRot = rl.xRot; root.getChild("left_pants").xRot = ll.xRot;
        } catch (Throwable ignored) {
            // parts not found: the model just stands
        }
    }

    private static void fl(GuiGraphics g, float x, float y, float w, float h, int c) {
        g.fill(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h), c);
    }

    private static int hash(int x, int y) {
        int h = x * 73856093 ^ y * 19349663;
        h ^= h >>> 13; h *= 0x5bd1e995; h ^= h >>> 15;
        return h & 255;
    }

    private static int shade(int c, int d) {
        int r = Math.max(0, Math.min(255, ((c >> 16) & 255) + d)), g = Math.max(0, Math.min(255, ((c >> 8) & 255) + d)),
                b = Math.max(0, Math.min(255, (c & 255) + d));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** rectangle in design units; x is measured from the middle of the screen */
    private void dr(GuiGraphics g, float f, float cx, float x, float y, float w, float h, int c) {
        fl(g, cx + x * f, y * f, w * f, h * f, c);
    }

    /** a Japanese-style timber spawn square, drawn in blocky layers and animated: drifting clouds, swaying pines,
     *  flickering fireplace, swinging lanterns, rising embers, slow camera drift */
    private void drawSpawn(GuiGraphics g, float f, float zoom, float t) {
        int W = minecraft.getWindow().getWidth(), H = minecraft.getWindow().getHeight();
        float cx = W / 2f;
        float pan = (float) Math.sin(t * 0.35f) * 26f;           // slow camera drift
        g.pose().pushMatrix();
        g.pose().scale(1f / (float) gs, 1f / (float) gs);
        // sky
        for (int i = 0; i < 28; i++) {
            float k = i / 27f;
            fl(g, 0, 760f * f * i / 28f, W, 760f * f / 28f + 1,
                    0xFF000000 | ((int) (118 + 70 * k) << 16) | ((int) (166 + 56 * k) << 8) | 255);
        }
        for (int i = 0; i < 6; i++) {                                // clouds
            float x = ((i * 470f + t * 12f) % 2500f) - 700f;
            float y = 90f + (i * 83) % 230;
            dr(g, f, cx, x - 937f + pan * 0.2f, y, 230f, 24f, 0xD9FFFFFF);
            dr(g, f, cx, x - 937f + 40f + pan * 0.2f, y - 18f, 120f, 20f, 0xD9FFFFFF);
        }
        // camera push-in on the pavilion
        g.pose().translate(cx, 700f * f);
        float sc = 1f + 0.2f * zoom;
        g.pose().scale(sc, sc);
        g.pose().translate(-cx, -700f * f);

        // distant pines on the right
        for (int k = 0; k < 4; k++) {
            float bx = 520f + k * 150f + pan * 0.5f, sw = (float) Math.sin(t * 1.1f + k) * 3f;
            float top = 260f + (k * 47) % 90;
            dr(g, f, cx, bx - 937f + 20f, top + 120f, 16f, 330f, 0xFF4B3524);
            for (int tier = 0; tier < 6; tier++) {
                float w = 70f + tier * 36f, y = top + tier * 46f;
                for (int row = 0; row < 3; row++) {
                    float ww = w - row * 18f;
                    int c = (row + tier) % 2 == 0 ? 0xFF2C5A31 : 0xFF244B29;
                    dr(g, f, cx, bx - 937f + 28f - ww / 2f + sw * (1f - tier / 6f), y + row * 14f, ww, 14f, c);
                }
            }
        }
        // left building: slate roof, white plaster, dark timber
        float lx = -900f + pan * 0.7f;
        for (int r = 0; r < 12; r++) {
            float rx = lx + 80f + r * 18f, y = 110f + r * 26f;
            dr(g, f, cx, rx, y, 640f - r * 18f, 26f, r % 2 == 0 ? 0xFF3A3B46 : 0xFF2F303A);
        }
        dr(g, f, cx, lx + 120f, 420f, 580f, 270f, 0xFFE9E7E1);
        for (int i = 0; i < 6; i++) dr(g, f, cx, lx + 120f + i * 116f, 420f, 12f, 270f, 0xFF4A3220);
        dr(g, f, cx, lx + 120f, 420f, 580f, 12f, 0xFF4A3220);
        dr(g, f, cx, lx + 120f, 548f, 580f, 12f, 0xFF4A3220);
        for (int i = 0; i < 4; i++) {
            dr(g, f, cx, lx + 150f + i * 140f, 450f, 70f, 80f, 0xFF5A3A24);
            dr(g, f, cx, lx + 158f + i * 140f, 458f, 54f, 64f, 0xFFC9D8E2);
        }
        dr(g, f, cx, lx + 120f, 640f, 580f, 60f, 0xFF7A4A34);
        // gravel plaza
        for (int j = 0; j < 26; j++) {
            float y = 640f + j * j * 0.62f, h = 6f + j * 1.9f;
            if (y > 1050f) break;
            float cw = 10f + j * 3.2f;
            int row0 = (int) Math.floor((-W / 2f / f) / cw) - 1;
            for (float x = -1100f + (j % 2) * cw / 2f; x < 1100f; x += cw) {
                int n = hash((int) (x / cw) + j * 31, j);
                int base = n < 70 ? 0xFF8F8A8E : n < 150 ? 0xFF9E9A9C : n < 215 ? 0xFFAAA6A6 : 0xFF86828A;
                dr(g, f, cx, x + pan * 0.3f, y, cw + 1f, h + 1f, base);
            }
        }
        // pavilion floor: blue-grey tiles with a few oak inlays
        float fx0 = -300f + pan * 0.6f;
        for (int j = 0; j < 7; j++)
            for (int i = 0; i < 14; i++) {
                float tw = 46f + j * 4f, th = 26f + j * 4f;
                float y = 665f + j * 32f + j * j * 2f, x = fx0 + 20f + i * tw - (j * 12f);
                int n = hash(i, j * 7);
                int c = n % 11 == 0 ? 0xFFB08A52 : ((i + j) & 1) == 0 ? 0xFF9DAFAE : 0xFF93A5A4;
                dr(g, f, cx, x, y, tw - 2f, th - 2f, c);
            }
        // brick chimney and fireplace
        float bx = -20f + pan * 0.6f;
        dr(g, f, cx, bx, 300f, 120f, 360f, 0xFFA4533F);
        for (int r = 0; r < 18; r++) {
            dr(g, f, cx, bx, 300f + r * 20f, 120f, 3f, 0xFF7C3E31);
            for (float xx = (r % 2) * 20f; xx < 120f; xx += 40f) dr(g, f, cx, bx + xx, 300f + r * 20f, 3f, 20f, 0xFF7C3E31);
        }
        dr(g, f, cx, bx + 18f, 540f, 84f, 120f, 0xFF1D1210);                 // fire opening
        for (int i = 0; i < 6; i++) {                                           // flames
            float fh = 36f + (float) (Math.sin(t * 9f + i * 1.7f) * 14f + Math.sin(t * 13f + i) * 8f);
            float fx = bx + 24f + i * 13f;
            dr(g, f, cx, fx, 650f - fh, 12f, fh, 0xFFFF7A1A);
            dr(g, f, cx, fx + 2f, 650f - fh * 0.62f, 8f, fh * 0.62f, 0xFFFFC43A);
            dr(g, f, cx, fx + 4f, 650f - fh * 0.3f, 4f, fh * 0.3f, 0xFFFFF0A0);
        }
        for (int i = 0; i < 10; i++) {                                          // embers rising
            float ph = (t * 0.55f + i * 0.173f) % 1f;
            float ex = bx + 40f + (float) Math.sin(i * 3.1f + ph * 6f) * 40f, ey = 540f - ph * 260f;
            dr(g, f, cx, ex, ey, 5f, 5f, ((int) (255 * (1f - ph)) << 24) | 0xFFA030);
        }
        // fire glow on the floor
        dr(g, f, cx, bx - 60f, 640f, 240f, 90f, 0x1AFF8A20);
        // dark slate under the roof and the crossbeam
        dr(g, f, cx, -330f + pan * 0.6f, 140f, 760f, 190f, 0xFF383842);
        for (int r = 0; r < 9; r++) dr(g, f, cx, -330f + pan * 0.6f, 140f + r * 21f, 760f, 3f, 0xFF2A2A32);
        // oak posts
        float[] px = {-340f, 330f};
        float[] pw = {80f, 110f};
        for (int i = 0; i < 2; i++) {
            float x = px[i] + pan * (i == 0 ? 0.8f : 1.0f);
            dr(g, f, cx, x, 280f, pw[i], 640f, 0xFFA8793F);
            for (int k = 0; k < 9; k++) dr(g, f, cx, x + 6f + k * (pw[i] / 9f), 280f, 5f, 640f, k % 2 == 0 ? 0xFF946830 : 0xFFB8894A);
            dr(g, f, cx, x - 14f, 860f, pw[i] + 28f, 60f, 0xFF5A3B20);       // base block
        }
        // crossbeam
        dr(g, f, cx, -340f + pan * 0.8f, 290f, 800f + pan * 0.2f, 62f, 0xFF8A6030);
        for (int k = 0; k < 4; k++) dr(g, f, cx, -340f + pan * 0.8f, 296f + k * 15f, 800f, 3f, 0xFF6B4A22);
        // big dark-oak roof, stepped diagonal
        for (int r = 0; r < 15; r++) {
            float x = -520f + r * 26f + pan * 0.9f, y = r * 20f - 20f;
            int c = r % 2 == 0 ? 0xFF6B4A28 : 0xFF5C3F20;
            dr(g, f, cx, x, y, 1500f - r * 26f, 20f, c);
            dr(g, f, cx, x, y + 17f, 1500f - r * 26f, 3f, 0xFF3F2A14);
            for (float xx = (r % 2) * 55f; xx < 1500f - r * 26f; xx += 110f) dr(g, f, cx, x + xx, y, 3f, 17f, 0xFF4A3118);
        }
        dr(g, f, cx, 380f + pan * 0.9f, 0f, 120f, 300f, 0xFF7C5830);          // big roof beam end with ring pattern
        for (int k = 0; k < 4; k++) dr(g, f, cx, 395f + k * 12f + pan * 0.9f, 20f + k * 12f, 90f - k * 24f, 90f - k * 24f, k % 2 == 0 ? 0xFF8A6535 : 0xFF6E4C26);
        // hanging chains and lanterns that swing
        float[] lxs = {-170f, 190f};
        for (int i = 0; i < 2; i++) {
            float sw = (float) Math.sin(t * 1.6f + i * 1.9f) * 9f, x = lxs[i] + pan * 0.7f;
            dr(g, f, cx, x + sw * 0.15f, 350f, 5f, 90f, 0xFF3B3B44);
            dr(g, f, cx, x - 9f + sw, 440f, 24f, 34f, 0xFF4A3524);
            dr(g, f, cx, x - 5f + sw, 446f, 16f, 22f, 0xFFFFC857);
            dr(g, f, cx, x - 40f + sw, 420f, 92f, 92f, 0x14FFB84A);
        }
        // front wooden steps and a bush
        for (int k = 0; k < 4; k++) dr(g, f, cx, -330f + k * 6f + pan * 0.9f, 900f + k * 22f, 760f - k * 12f, 22f, k % 2 == 0 ? 0xFF9A6C3A : 0xFF8A5E30);
        for (int i = 0; i < 6; i++)
            for (int j = 0; j < 3; j++)
                dr(g, f, cx, -470f + i * 26f + pan, 910f + j * 22f, 26f, 22f, hash(i, j) > 120 ? 0xFF3F8B35 : 0xFF2F7029);
        g.pose().popMatrix();
        // soft vignette
        g.pose().pushMatrix();
        g.pose().scale(1f / (float) gs, 1f / (float) gs);
        fl(g, 0, 0, W, H, 0x10000000);
        g.pose().popMatrix();
    }

    /** the walking scene: skin walks in from the left, stops, the camera zooms, the saved servers fade in */
    private void renderScene(GuiGraphics g) {
        try {
            startSkinFetch();
            pollSkin();
            if (model3d == null) {
                model3d = new net.minecraft.client.model.player.PlayerModel(
                        minecraft.getEntityModels().bakeLayer(net.minecraft.client.model.geom.ModelLayers.PLAYER), false);
                model3dSlim = new net.minecraft.client.model.player.PlayerModel(
                        minecraft.getEntityModels().bakeLayer(net.minecraft.client.model.geom.ModelLayers.PLAYER_SLIM), true);
            }
            net.minecraft.resources.Identifier tex;
            net.minecraft.client.model.player.PlayerModel mdl;
            if (ownTex != null) { tex = ownTex; mdl = ownSlim ? model3dSlim : model3d; }
            else {
                net.minecraft.world.entity.player.PlayerSkin sk = skin();
                if (sk == null) return;
                tex = sk.body().texturePath(); mdl = model3d;
            }
            float f = f();
            float t = (System.currentTimeMillis() - sceneStart) / 1000f;
            float walkEnd = 2.7f, zoomStart = 3.1f, zoomEnd = 4.1f;
            float walk = clamp01(t / walkEnd);
            float amount = 1f - ease((t - 2.3f) / 0.6f);               // legs stop swinging near the end
            float zoom = ease((t - zoomStart) / (zoomEnd - zoomStart));
            sceneP = ease((t - 3.7f) / 0.9f);
            float phase = t * 9f;
            pose(mdl, phase, t < 3.0f ? amount : 0f);
            // design coordinates, measured from the middle of the screen
            float cxD = Math.max(-260f, -260f + (680f + 260f) * (1f - (1f - walk) * (1f - walk) * (1f - walk) * 0.35f) * 1f);
            cxD = -260f + (680f + 260f) * ease(walk * 0.6f + walk * walk * 0.4f);
            float hD = 520f + (700f - 520f) * zoom;                      // body height
            float feetD = 880f + (950f - 880f) * zoom;
            cxD += (590f - 680f) * zoom;                                  // slides left while zooming
            float bob = (float) Math.abs(Math.sin(phase)) * 7f * amount * (t < 3.0f ? 1f : 0f);
            float side = hD / 0.9f;                                       // the box the model is fitted in
            float cx = rx(cxD, 1), cy = (feetD - hD / 2f - bob) * f;
            drawSpawn(g, f, zoom, t);
            // soft shadow on the ground under the feet
            for (int i = 0; i < 6; i++) {
                float w = hD * (0.55f - i * 0.07f) * f, h = hD * 0.055f * (1f - i * 0.1f) * f;
                roundBox(g, cx - w / 2f, feetD * f - h / 2f + 4f * f, w, h, h / 2f, 0x14000000);
            }
            int x0 = Math.round((cx - side * f / 2f) / (float) gs), x1 = Math.round((cx + side * f / 2f) / (float) gs);
            int ya = Math.round((cy - side * f / 2f) / (float) gs), yb = Math.round((cy + side * f / 2f) / (float) gs);
            float turn = 78f + (24f - 78f) * ease((t - walkEnd) / 0.4f);   // profile while walking, then turns toward the servers
            g.submitSkinRenderState(mdl, tex, 0.97f * (yb - ya) / 2.125f, -28f, turn, -0.0625f, x0, ya, x1, yb);
            // heading and hint next to the server list
            if (sceneP > 0f) {
                int a = (int) (255 * sceneP);
                int white = (a << 24) | 0xFFFFFF, grey = (a << 24) | 0xC8C8CD;
                float lx = rx(1003f, 1) + (1f - sceneP) * 90f * f;
                dyn(g, "Saved servers", lx, 400f * f, 26f, true, white, 400f * f);
                dyn(g, items.stream().anyMatch(i -> i.kind == 1) ? "Click a server to join it" : "No saved servers yet - add some under Multiplayer",
                        lx, 438f * f, 13f, false, grey, 500f * f);
            }
        } catch (Throwable th) {
            if (!logged) { logged = true; LOG.warn("walking scene failed", th); }
        }
    }

    /** a medium close-up of your skin in side profile (head and shoulders), above the account box */
    private void profile(GuiGraphics g) {
        net.minecraft.resources.Identifier tex = skinTexture();
        if (tex == null) return;
        float f = f(), k = 14f * f;                       // 14 design pixels per skin pixel
        float cx = rx(1688f, 2) , top = 345f * f;
        float head = cx - 4f * k;                          // head is 8 wide, the body 4 wide, both centred
        float body = cx - 2f * k;
        // body first (3.5 rows are visible), then the arm in front of it, then the head
        part(g, tex, 16, 20, 4, 4, body, top + 8f * k, k);          // torso (right side)
        part(g, tex, 16, 36, 4, 4, body - 0.0f, top + 8f * k, k);   // jacket layer
        part(g, tex, 40, 20, 4, 4, body, top + 8f * k, k);          // arm (right side)
        part(g, tex, 40, 36, 4, 4, body, top + 8f * k, k);          // sleeve layer
        part(g, tex, 0, 8, 8, 8, head, top, k);                     // head (right side)
        part(g, tex, 32, 8, 8, 8, head, top, k);                    // hat layer
    }

    /** the server's icon as a texture (made once from the png in your server list); null if it has none */
    private net.minecraft.resources.Identifier serverIcon(Item it) {
        if (it.iconTried) return it.iconTex;
        it.iconTried = true;
        try {
            if (it.iconBytes == null || it.iconBytes.length == 0) return null;
            com.mojang.blaze3d.platform.NativeImage img = com.mojang.blaze3d.platform.NativeImage.read(new java.io.ByteArrayInputStream(it.iconBytes));
            net.minecraft.resources.Identifier id = Identifier.fromNamespaceAndPath(ChromeClientMod.MOD_ID, "server_icon/" + Math.abs(it.name.hashCode()) + "_" + Math.abs(java.util.Arrays.hashCode(it.iconBytes)));
            minecraft.getTextureManager().register(id, new net.minecraft.client.renderer.texture.DynamicTexture(() -> "chromeclient server icon", img));
            it.iconTex = id;
        } catch (Throwable ignored) {
            // no icon: the coloured initial is shown
        }
        return it.iconTex;
    }

    /** draws your skin's head (real pixels); false if the skin is not available */
    private boolean head(GuiGraphics g, float x, float y, float size) {
        try {
            net.minecraft.world.entity.player.PlayerSkin skin = skin();
            if (skin == null) return false;
            g.pose().pushMatrix();
            g.pose().scale((float) (1.0 / gs), (float) (1.0 / gs));
            g.pose().translate(x, y);
            g.pose().scale(size / 8f, size / 8f);
            net.minecraft.client.gui.components.PlayerFaceRenderer.draw(g, skin, 0, 0, 8);
            g.pose().popMatrix();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** a filled rounded square in real pixels (same helper the other screens use, but in real pixel coordinates) */
    private void roundBox(GuiGraphics g, float x, float y, float w, float h, float r, int color) {
        g.pose().pushMatrix();
        g.pose().scale((float) (1.0 / gs), (float) (1.0 / gs));
        int X = Math.round(x), Y = Math.round(y), W = Math.round(w), H = Math.round(h), R = Math.max(0, Math.min(Math.round(r), Math.min(W, H) / 2));
        for (int dy = 0; dy < H; dy++) {
            int d = dy < R ? R - dy : (dy >= H - R ? dy - (H - R) + 1 : 0);
            int in = d == 0 ? 0 : (int) Math.ceil(R - Math.sqrt(Math.max(0.0, (double) R * R - (d - 0.5) * (d - 0.5))));
            g.fill(X + in, Y + dy, X + W - in, Y + dy + 1, color);
        }
        g.pose().popMatrix();
    }

    /** hold the left mouse button on the model and move left / right to turn it */
    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dragX, double dragY) {
        if (!loggedDrag) { loggedDrag = true; LOG.info("mouse drag reached the menu: button={} at {},{}", event.button(), event.x(), event.y()); }
        float f = f();
        double mx = 1874.0 - (minecraft.getWindow().getWidth() - event.x() * gs) / f, my = event.y() * gs / f;   // back to design pixels (right-anchored)
        if (!sceneMode && event.button() == 0 && mx >= 1561 && mx <= 1561 + 254 && my >= 300 && my <= 530) {
            yaw += (float) dragX * 2.5f;
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;   // like the normal main menu
    }
}
