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
    private static float yaw = 30f;          // how far the model is turned (kept when the menu is opened again)
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
        float f = f();

        if (!model3d(g)) profile(g);
        // logo and name in the middle
        sprAt(g, MenuSprites.LOGO, 937f - dw(MenuSprites.LOGO) / 2f, 386f - dh(MenuSprites.LOGO) / 2f, 1);   // centred on where the old logo square was
        sprAt(g, MenuSprites.T_TITLE, 937f - dw(MenuSprites.T_TITLE) / 2f, 466f - dh(MenuSprites.T_TITLE) / 2f, 1);

        for (Item it : items) {
            float x = rx(it.x, it.anchor), y = it.y * f;
            boolean hover = it.hit != null && it.hit.isHoveredOrFocused();
            spr(g, hover ? it.bgH : it.bg, x, y);
            switch (it.kind) {
                case 0 -> {    // icon + label as a group in the middle
                    float gw = 16f + 9f + dw(it.label), gx = (it.w - gw) / 2f;
                    spr(g, it.icon, x + gx * f, y + (it.h - 16f) / 2f * f);
                    spr(g, it.label, x + (gx + 25f) * f, y + (it.h - 22f) / 2f * f);
                }
                case 2 -> spr(g, it.icon, x + (it.w - 16f) / 2f * f, y + (it.h - 16f) / 2f * f);
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
        spr(g, MenuSprites.LOGO_S, (45f - dw(MenuSprites.LOGO_S) / 2f) * f, (1021f - dh(MenuSprites.LOGO_S) / 2f) * f);
        spr(g, MenuSprites.T_FOOT, 60f * f, 1012f * f);
        String ver = FabricLoader.getInstance().getModContainer("chromeclient")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
        dyn(g, "Fabric 1.21.11  " + ver, (60f + dw(MenuSprites.T_FOOT) + 12f) * f, 1021f * f, 12f, false, GREY, 300f * f);
        spr(g, MenuSprites.T_COPY, rx(1874f - 34f - dw(MenuSprites.T_COPY), 2), 1014f * f);
    }

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
            net.minecraft.world.entity.player.PlayerSkin skin = skin();
            if (skin == null) return false;
            net.minecraft.resources.Identifier tex = skin.body().texturePath();
            if (model3d == null) {
                model3d = new net.minecraft.client.model.player.PlayerModel(
                        minecraft.getEntityModels().bakeLayer(net.minecraft.client.model.geom.ModelLayers.PLAYER), false);
            }
            float f = f();
            float hr = 490f * f;                                   // height of the whole body area, real pixels
            float cx = rx(1688f, 2), y0 = 330f * f;
            int x0 = Math.round((cx - hr / 2f) / (float) gs), x1 = Math.round((cx + hr / 2f) / (float) gs);
            int ya = Math.round(y0 / (float) gs), yb = Math.round((y0 + hr) / (float) gs);
            float scale = 0.97f * (yb - ya) / 2.125f;
            // only head and shoulders are shown: cut everything below the box above the account button
            g.enableScissor(Math.round(rx(1561f, 2) / (float) gs), Math.round(300f * f / (float) gs),
                    Math.round(rx(1561f + 254f, 2) / (float) gs), Math.round(512f * f / (float) gs));
            g.submitSkinRenderState(model3d, tex, scale, -5f, yaw, -0.0625f, x0, ya, x1, yb);
            if (!logged3d) { logged3d = true; LOG.info("3D model submitted: texture={} box=({},{})-({},{}) scale={} guiScale={}", tex, x0, ya, x1, yb, scale, gs); }
            g.disableScissor();
            return true;
        } catch (Throwable t) {
            if (!logged) { logged = true; LOG.warn("3D model on the main menu failed, drawing the flat profile instead", t); }
            return false;
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
        if (event.button() == 0 && mx >= 1561 && mx <= 1561 + 254 && my >= 300 && my <= 515) {
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
