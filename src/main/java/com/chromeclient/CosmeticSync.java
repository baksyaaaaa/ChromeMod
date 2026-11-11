package com.chromeclient;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shows your cosmetics to other players who use the mod (and theirs to you). Your picks are sent to a tiny web
 * service keyed by your player UUID; the mod asks the same service about the
 * players it can see. Nothing is sent unless a service link is set in the Cosmetics tab.
 */
public final class CosmeticSync {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("ChromeCosmetics");
    /**
     * The built-in service link everybody's mod uses, so nobody has to type anything.
     * The cosmetics server on Render (hardcoded, not changeable in game).
     */
    public static final String DEFAULT_URL = "https://cosmetics-md7h.onrender.com";
    /** the link in use: built in and fixed */
    public static final String url = DEFAULT_URL;   // fixed on purpose: not saved, not editable
    public static boolean share = true;   // send my own picks

    public record Remote(int hat, int cape, int wings, int effect, int where, float amount, boolean effectOn) {}

    private static final Map<UUID, Remote> remote = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> asked = new ConcurrentHashMap<>();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static String lastSent = "";
    private static long lastSentAt = 0;
    private static ClientLevel lastLevel = null;
    private static int tick = 0;
    public static String status = "";

    private CosmeticSync() {}

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(CosmeticSync::tick);
    }

    public static boolean on() {
        return url != null && url.startsWith("http");
    }

    /** another player's cosmetics, or null when we know of none */
    public static Remote get(UUID id) {
        Remote r = remote.get(id);
        return r == null || (r.hat() == 0 && r.cape() == 0 && r.wings() == 0 && r.effect() == 0) ? null : r;
    }

    private static String base() {
        String u = url.trim();
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }

    private static void tick(Minecraft mc) {
        ClientLevel lv = mc.level;
        if (lv == null || mc.player == null) { lastLevel = null; return; }
        if (!on()) return;
        tick++;
        if (lv != lastLevel) {   // joined a world: tell the service again and forget what we knew
            lastLevel = lv;
            lastSent = "";
            asked.clear();
        }
        try {
            if (share) publish(mc);
            lookup(mc, lv);
            if (!mc.isPaused()) {   // particles on the other players
                for (Player p : lv.players()) {
                    if (p == mc.player) continue;
                    Remote r = remote.get(p.getUUID());
                    if (r == null || !r.effectOn() || r.effect() == 0) continue;
                    if (p.distanceToSqr(mc.player) > 40 * 40) continue;
                    Cosmetics.emit(p, r.effect(), r.where(), r.amount(), lv);
                }
            }
        } catch (Throwable t) {
            status = "Sync problem: " + t.getMessage();
        }
    }

    /** sends my picks when they changed (or every few minutes so the service keeps them) */
    private static void publish(Minecraft mc) {
        JsonObject o = new JsonObject();
        o.addProperty("u", mc.player.getUUID().toString());
        o.addProperty("h", Cosmetics.chosen[0]);
        o.addProperty("c", Cosmetics.chosen[1]);
        o.addProperty("w", Cosmetics.chosen[2]);
        o.addProperty("e", Cosmetics.chosen[3]);
        o.addProperty("ew", Cosmetics.effectWhere);
        o.addProperty("ea", Cosmetics.effectAmount);
        o.addProperty("eo", Cosmetics.effectOn);
        String body = o.toString();
        long now = System.currentTimeMillis();
        if (body.equals(lastSent) && now - lastSentAt < 5 * 60_000L) return;
        lastSent = body;
        lastSentAt = now;
        HTTP.sendAsync(HttpRequest.newBuilder(URI.create(base() + "/set")).timeout(Duration.ofSeconds(8))
                        .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString()).thenAccept(r -> status = r.statusCode() == 200 ? "Connected" : "Service said " + r.statusCode())
                .exceptionally(e -> { status = "Can't reach the service"; return null; });
    }

    /** asks about the players around that we haven't asked about lately */
    private static volatile boolean busy = false;   // one question at a time: as fast as the service answers, never piling up

    private static void lookup(Minecraft mc, ClientLevel lv) {
        if (busy) return;
        long now = System.currentTimeMillis();
        List<String> ids = new ArrayList<>();
        for (Player p : lv.players()) {
            if (p == mc.player) continue;
            Long t = asked.get(p.getUUID());
            if (t != null && now - t < 50L) continue;
            asked.put(p.getUUID(), now);
            ids.add(p.getUUID().toString());
            if (ids.size() >= 40) break;
        }
        if (ids.isEmpty()) return;
        busy = true;
        String q = URLEncoder.encode(String.join(",", ids), StandardCharsets.UTF_8);
        HTTP.sendAsync(HttpRequest.newBuilder(URI.create(base() + "/get?u=" + q)).timeout(Duration.ofSeconds(8)).GET().build(),
                HttpResponse.BodyHandlers.ofString()).thenAccept(r -> {
            if (r.statusCode() != 200) { status = "Service said " + r.statusCode(); return; }
            status = "Connected";
            JsonObject all = JsonParser.parseString(r.body()).getAsJsonObject();
            for (String id : ids) {
                UUID u = UUID.fromString(id);
                if (!all.has(id) || !all.get(id).isJsonObject()) { remote.put(u, new Remote(0, 0, 0, 0, 0, 0.5f, true)); continue; }
                JsonObject o = all.getAsJsonObject(id);
                remote.put(u, new Remote(
                        clamp(o, "h", Cosmetics.ITEMS[0].length - 1), clamp(o, "c", Cosmetics.ITEMS[1].length - 1),
                        clamp(o, "w", Cosmetics.ITEMS[2].length - 1), clamp(o, "e", Cosmetics.ITEMS[3].length - 1),
                        clamp(o, "ew", Cosmetics.WHERE.length - 1),
                        o.has("ea") ? Math.max(0.1f, Math.min(1f, o.get("ea").getAsFloat())) : 0.5f,
                        !o.has("eo") || o.get("eo").getAsBoolean()));
            }
        }).exceptionally(e -> { status = "Can't reach the service"; LOG.debug("lookup failed", e); return null; }).whenComplete((a, b) -> busy = false);
    }

    private static int clamp(JsonObject o, String k, int max) {
        return o.has(k) ? Math.max(0, Math.min(max, o.get(k).getAsInt())) : 0;
    }

    // ---- saving (inside the cosmetics file) ----
    public static void toJson(JsonObject o) {
        o.addProperty("shareMine", share);
    }

    public static void fromJson(JsonObject o) {
        if (o.has("shareMine")) share = o.get("shareMine").getAsBoolean();
    }
}
