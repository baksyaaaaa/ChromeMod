package com.chromeboost;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/** Chrome Boost: a small set of optimizations that each do something measurable. Settings: F10. */
public final class Boost implements ClientModInitializer {
    public static final String MOD_ID = "chromeboost";
    private KeyMapping menuKey;

    @Override
    public void onInitializeClient() {
        Settings.load();
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key." + MOD_ID + ".menu", InputConstants.Type.KEYSYM, InputConstants.KEY_F10, category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (menuKey.consumeClick()) {
                if (client.screen == null) client.setScreen(new BoostScreen(null));
            }
            AdaptiveDistance.tick(client);
            MemoryCare.tick(client);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            AdaptiveDistance.restore(client);
            MemoryCare.onLeave();
        });
    }
}
