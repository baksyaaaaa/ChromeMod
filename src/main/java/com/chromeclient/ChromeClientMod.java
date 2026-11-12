package com.chromeclient;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Chrome Client: press Right Shift in game for the menu (HUD info, keystrokes, armor status, zoom, auto sprint).
 * Press [ to move the HUD around.
 * Keys can be changed in Options > Controls. Nothing opens while chat or another screen is open.
 */
public class ChromeClientMod implements ClientModInitializer {
    public static final String MOD_ID = "chromeclient";
    private static final int ZOOM_FOV = 30;   // the lowest the game allows

    private KeyMapping menuKey, zoomKey, editKey, freelookKey, musicKey, emoteKey;
    private boolean zooming = false;
    private int savedFov = 70;

    @Override
    public void onInitializeClient() {
        Config.load();
        Extras.load();
        Lyrics.init();   // lyric pop-ups for the YT Music mod
        Cosmetics.load();
        Cosmetics.init(); CosmeticSync.init();
        Visuals.load();
        Visuals.init();       // hand position, swing trail, block outline
        Fx.init(); Aura.init();            // our own glowing / sparkling particles
        HudPack.load();
        HudPack.init();       // potion / keybind / target / performance panels, crosshair, jump ripples
        new Buddy().init();   // /buddy <uuid>: a little buddy follows you
        Hud hud = new Hud();

        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key." + MOD_ID + ".menu", InputConstants.Type.KEYSYM, InputConstants.KEY_RSHIFT, category));
        zoomKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key." + MOD_ID + ".zoom", InputConstants.Type.KEYSYM, InputConstants.KEY_C, category));

        freelookKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key." + MOD_ID + ".freelook", InputConstants.Type.KEYSYM, InputConstants.KEY_LALT, category));

        musicKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key." + MOD_ID + ".music", InputConstants.Type.KEYSYM, InputConstants.KEY_J, category));

        emoteKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key." + MOD_ID + ".emotes", InputConstants.Type.KEYSYM, InputConstants.KEY_B, category));

        editKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key." + MOD_ID + ".edit", InputConstants.Type.KEYSYM, InputConstants.KEY_LBRACKET, category));

        // the pay timer reads server messages ("Steve paid you $500") while it runs
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) PayTimer.get().onMessage(message.getString());
        });

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "hud"), hud::render);
        shiftVanillaElements(hud);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (menuKey.consumeClick()) {
                if (client.player != null && client.screen == null) client.setScreen(new ClickGui(hud));
            }
            while (musicKey.consumeClick()) {
                if (client.player != null && client.screen == null && YtBridge.installed()) client.setScreen(new MusicScreen(null));
            }
            while (editKey.consumeClick()) {
                if (client.player != null && client.screen == null) client.setScreen(new EditHudScreen(hud));
            }
            while (emoteKey.consumeClick()) {
                org.slf4j.LoggerFactory.getLogger("ChromeEmotes").info("B pressed: emotes={} screen={}", Extras.emotes, client.screen);
                if (Extras.emotes && client.player != null && client.screen == null) client.setScreen(new EmoteWheelScreen());
            }
            PlayerEmotes.tick(client);
            Freelook.update(freelookKey.isDown() && client.player != null && client.screen == null);
            handleZoom(client);
            handleSprint(client);
        });

        // hand the FOV back before the game saves its options
        ClientLifecycleEvents.CLIENT_STOPPING.register(this::stopZoom);
    }

    /** Lets the vanilla HUD pieces be moved and resized: each is drawn shifted and scaled around its default spot. */
    private void shiftVanillaElements(Hud hud) {
        for (int i = 0; i < Hud.IDS.length; i++) {
            if (!Hud.isVanilla(i)) continue;
            final String key = Hud.IDS[i];
            // the registry's name for the player list isn't certain across versions, so try both
            String[] names = key.equals("tab_list") ? new String[]{"tab_list", "player_list"} : new String[]{key};
            for (String name : names) {
                try {
                    HudElementRegistry.replaceElement(Identifier.fromNamespaceAndPath("minecraft", name), original ->
                            (graphics, delta) -> {
                                int[] o = Config.get().off(key);
                                float sc = Config.get().scale(key);
                                int[] base = hud.base(key, Minecraft.getInstance());
                                graphics.pose().pushMatrix();
                                graphics.pose().translate((float) (base[0] + o[0]), (float) (base[1] + o[1]));
                                graphics.pose().scale(sc, sc);
                                graphics.pose().translate((float) -base[0], (float) -base[1]);
                                original.render(graphics, delta);
                                graphics.pose().popMatrix();
                            });
                } catch (Throwable ignored) {
                    // this piece isn't there in this version: it just can't be moved, nothing else breaks
                }
            }
        }
    }

    private void handleZoom(Minecraft client) {
        boolean want = Config.get().zoom && zoomKey.isDown() && client.player != null && client.screen == null;
        if (want && !zooming) {
            savedFov = client.options.fov().get();
            client.options.fov().set(ZOOM_FOV);
            zooming = true;
        } else if (!want && zooming) {
            stopZoom(client);
        }
    }

    private void stopZoom(Minecraft client) {
        if (zooming) {
            client.options.fov().set(savedFov);
            zooming = false;
        }
    }

    private void handleSprint(Minecraft client) {
        if (!Config.get().autoSprint || client.player == null || client.screen != null) return;
        if (client.options.keyUp.isDown() && !client.player.isShiftKeyDown()) {
            client.options.keySprint.setDown(true);
        }
    }
}
