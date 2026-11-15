package com.chromeboost;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/** Chrome Boost: a small set of optimizations that each do something measurable. Runs with its defaults; options live in config/chrome-boost.json. */
public final class Boost implements ClientModInitializer {
    public static final String MOD_ID = "chromeboost";

    @Override
    public void onInitializeClient() {
        Settings.load();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            AdaptiveDistance.tick(client);
            MemoryCare.tick(client);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            AdaptiveDistance.restore(client);
            MemoryCare.onLeave();
        });
    }
}
