package com.chromeclient;

import com.mojang.realmsclient.RealmsMainScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
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
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Chrome Client's main menu: the blurred panorama, the name in the middle with the main buttons under it, your saved
 * servers as a quick-play list on the left and your account on the right. The buttons are normal (invisible) game
 * buttons that we draw over, so clicking and the keyboard work exactly like in the normal menu.
 */
public final class ChromeTitleScreen extends Screen {
    private final List<Button> hit = new ArrayList<>();       // one invisible real button per drawn button
    private final List<String> labels = new ArrayList<>();
    private final List<Boolean> primary = new ArrayList<>();
    private final List<int[]> rects = new ArrayList<>();
    private final List<ServerData> servers = new ArrayList<>();
    private float[] hov = new float[0];
    private int quickBtnStart = 0;
    private long last = System.nanoTime();

    public ChromeTitleScreen() {
        super(Component.literal("Chrome Client"));
    }

    private void add(String label, boolean prim, int x, int y, int w, int h, Runnable r) {
        Button b = Button.builder(Component.literal(label), btn -> r.run()).bounds(x, y, w, h).build();
        b.setAlpha(0f);   // invisible: we draw it ourselves, the game still handles the clicks
        addRenderableWidget(b);
        hit.add(b);
        labels.add(label);
        primary.add(prim);
        rects.add(new int[]{x, y, w, h});
    }

    @Override
    protected void init() {
        hit.clear(); labels.clear(); primary.clear(); rects.clear(); servers.clear();
        int cx = width / 2, bw = 190, y0 = height / 2 - 4;
        add("Singleplayer", false, cx - bw / 2, y0, bw, 20, () -> minecraft.setScreen(new SelectWorldScreen(this)));
        add("Multiplayer", false, cx - bw / 2, y0 + 25, bw, 20, () -> minecraft.setScreen(new JoinMultiplayerScreen(this)));
        add("Realms", false, cx - bw / 2, y0 + 50, bw, 20, () -> minecraft.setScreen(new RealmsMainScreen(this)));
        add("Settings", false, cx - bw / 2, y0 + 75, bw / 2 - 2, 20, () -> minecraft.setScreen(new OptionsScreen(this, minecraft.options)));
        add("Quit", false, cx + 2, y0 + 75, bw / 2 - 2, 20, () -> minecraft.stop());

        // your saved servers on the left (the same list as in Multiplayer)
        quickBtnStart = hit.size();
        try {
            ServerList sl = new ServerList(minecraft);
            sl.load();
            int n = Math.min(4, sl.size());
            for (int i = 0; i < n; i++) {
                ServerData d = sl.get(i);
                servers.add(d);
                add(d.name, false, 14, height / 2 - 60 + 44 + i * 34, 150, 30, () ->
                        ConnectScreen.startConnecting(this, minecraft, ServerAddress.parseString(d.ip), d, false, null));
            }
        } catch (Throwable ignored) {
            // no server list: the left side just stays empty
        }
        hov = new float[hit.size()];
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);   // panorama + blur
        Ui.fade = 1f;
        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - last) / 1e9f);
        last = now;
        float ease = 1f - (float) Math.exp(-dt * 16f);
        g.fill(0, 0, width, height, 0x44000000);

        int cx = width / 2, cy = height / 2;

        // the name in the middle
        g.pose().pushMatrix();
        float s = 2.6f;
        String title = "CHROME CLIENT";
        Component tc = Component.literal(title).withStyle(net.minecraft.ChatFormatting.BOLD);
        g.pose().translate(cx - font.width(tc) * s / 2f, cy - 46);
        g.pose().scale(s, s);
        g.drawString(font, tc, 0, 0, 0xFFFFFFFF, true);
        g.pose().popMatrix();
        // a small chrome ring above it
        Ui.rring(g, cx - 12, cy - 86, 24, 24, 12, 0xFFFFFFFF);
        Ui.rring(g, cx - 11, cy - 85, 22, 22, 11, 0x66FFFFFF);
        Ui.rrect(g, cx - 5, cy - 79, 10, 10, 5, Ui.ACCENT);

        // quick-play header and the account on the right
        Ui.glass(g, 14, cy - 60, 150, 36);
        Ui.textCenter(g, font, "Quickplay", 14, cy - 60 + 14, 150, 0xFFFFFFFF);
        if (servers.isEmpty()) {
            Ui.textCenter(g, font, "Add servers in Multiplayer", 14, cy - 60 + 44, 150, 0xFFB8B8B8);
        }
        String user = minecraft.getUser().getName();
        Ui.glass(g, width - 14 - 130, cy - 60, 130, 36);
        Ui.textCenter(g, font, user, width - 14 - 130, cy - 60 + 14, 130, 0xFFFFFFFF);

        // all the buttons
        for (int i = 0; i < hit.size(); i++) {
            int[] r = rects.get(i);
            boolean h = hit.get(i).isHoveredOrFocused();
            hov[i] += ((h ? 1f : 0f) - hov[i]) * ease;
            if (i < quickBtnStart) {
                Ui.button(g, font, labels.get(i), r[0], r[1], r[2], r[3], hov[i], primary.get(i));
            } else {
                Ui.rrect(g, r[0], r[1], r[2], r[3], 5, Ui.lerp(Ui.SURFACE, Ui.HOVER, hov[i]));
                Ui.textFit(g, font, labels.get(i), r[0] + 8, r[1] + 6, r[2] - 16, 0xFFFFFFFF);
                ServerData d = servers.get(i - quickBtnStart);
                Ui.textFit(g, font, d.ip, r[0] + 8, r[1] + 17, r[2] - 16, 0xFFB8B8B8);
            }
        }

        // bottom corners
        String ver = FabricLoader.getInstance().getModContainer("chromeclient")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
        g.drawString(font, "CHROME CLIENT  " + ver + "  Fabric 1.21.11", 8, height - 12, 0xFFD0D0D0, true);
        String c = "Copyright Mojang AB. Do not distribute!";
        g.drawString(font, c, width - font.width(c) - 8, height - 12, 0xFFB0B0B0, true);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;   // like the normal main menu
    }
}
