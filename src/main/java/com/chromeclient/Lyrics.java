package com.chromeclient;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * World lyrics: while the YT Music mod plays a song, the lyrics fly up around you as big glowing words hanging in the
 * world in front of you, and a small "now singing" pill at the top follows the line as it is sung. The time-synced
 * lyrics come from lrclib.net. Everything is tuned in Right Shift > Lyrics.
 */
public final class Lyrics {
    private record Line(double time, String text) {}

    private static final class Popup {
        String text;
        double wx, wy, wz;   // where it hangs in the world
        float scale;         // text size at the reference distance
        long born;           // may be in the future: words of one line arrive one after another
        long life;
        int width;
        float yawOff, roll, lean;  // how the words are turned in the world (they don't always face you)
        float faceYaw;       // yaw (deg) from you to it when it appeared
        float phase;         // offsets the flowing gradient
        TextEntity ent;      // the 3D text in the world
        int id;
    }

    private static final Pattern STAMP = Pattern.compile("\\[(\\d+):(\\d+(?:\\.\\d+)?)]");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();

    private static volatile List<Line> lines = List.of();
    private static String key = "";
    private static int shown = -1;               // index of the last line already sent into the world
    private static int lastPos = -1;
    private static long lastPosNanos = 0;
    private static final List<Popup> popups = new ArrayList<>();

    // what the pill shows
    private static String pillTitle = "";
    private static String lineText = "";
    private static double lineStart = 0, lineEnd = 0;
    private static double songNow = 0;
    private static long songNowNanos = 0;
    private static boolean songPlaying = false;

    // what the music panel shows
    public static String lineText() { return lineText; }
    public static double lineStart() { return lineStart; }
    public static double lineEnd() { return lineEnd; }
    public static double songTimeNow() { return songNow + (songPlaying ? (System.nanoTime() - songNowNanos) / 1e9 : 0.0); }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(Lyrics::tick);
        try {
            HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(ChromeClientMod.MOD_ID, "lyrics_pill"), Lyrics::renderPill);
        } catch (Throwable ignored) {
            // no lyrics display in this version: nothing else breaks
        }
    }

    // ---- following the song ----

    private static double songTime(YtBridge.Track t) {
        long now = System.nanoTime();
        if (t.position() != lastPos) {
            lastPos = t.position();
            lastPosNanos = now;
        }
        double extra = t.playing() ? Math.min(1.0, (now - lastPosNanos) / 1e9) : 0.0;
        return t.position() + extra;
    }

    private static void tick(Minecraft mc) {
        if (Extras.hideYtBar) YtBridge.hideOverlay();
        try { updatePopups(mc); } catch (Throwable ignored) { /* no 3D words this tick */ }
        if (mc.player == null || (!Extras.lyrics && !Extras.lyricsPill)) return;
        YtBridge.Track t = YtBridge.current();
        if (t == null || t.title() == null || t.title().isBlank() || t.duration() <= 0) {
            songPlaying = false;
            return;
        }
        pillTitle = t.artist() == null || t.artist().isBlank() ? t.title() : t.title() + " - " + t.artist();

        String k = t.title() + "|" + t.artist() + "|" + t.duration();
        if (!k.equals(key)) {   // a new song
            key = k;
            lines = List.of();
            shown = -1;
            lineText = "";
            clearPopups();
            fetch(k, t.title(), t.artist(), t.duration());
        }
        double now = songTime(t) + Extras.lyricsSync;
        songNow = now;
        songNowNanos = System.nanoTime();
        songPlaying = t.playing();

        List<Line> ls = lines;
        if (ls.isEmpty()) return;
        // the last line that has started
        int idx = -1;
        for (int i = 0; i < ls.size(); i++) {
            if (ls.get(i).time() <= now) idx = i;
            else break;
        }
        if (idx >= 0) {
            lineText = ls.get(idx).text();
            lineStart = ls.get(idx).time();
            lineEnd = idx + 1 < ls.size() ? ls.get(idx + 1).time() : lineStart + 4.0;
        }
        if (!t.playing() || !Extras.lyrics) return;
        if (idx < shown) shown = idx - 1;   // jumped back in the song
        if (idx >= 0 && idx > shown) {
            Line l = ls.get(idx);
            boolean fresh = now - l.time() < 3.0;   // after a skip forward don't show a line that was sung long ago
            shown = idx;
            if (fresh && !l.text().isBlank()) spawnLine(mc, l.text(), Math.max(1.5, lineEnd - l.time()));
        }
    }

    // ---- words flying up in the world ----

    /** splits a line into short chunks that arrive one after another, each in its own spot */
    private static void spawnLine(Minecraft mc, String text, double seconds) {
        String[] words = text.trim().split("\\s+");
        List<String> chunks = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int n = 0;
        for (String w : words) {
            if (n > 0 && (n >= 3 || cur.length() + w.length() > 16)) {
                chunks.add(cur.toString());
                cur.setLength(0);
                n = 0;
            }
            if (n > 0) cur.append(' ');
            cur.append(w);
            n++;
        }
        if (cur.length() > 0) chunks.add(cur.toString());
        if (chunks.size() > 4) {   // keep it readable: at most 4 pieces
            List<String> merged = new ArrayList<>(chunks.subList(0, 3));
            merged.add(String.join(" ", chunks.subList(3, chunks.size())));
            chunks = merged;
        }
        long base = System.currentTimeMillis();
        long step = (long) Mth.clamp(seconds * 1000.0 / (chunks.size() + 1), 150, 500);
        for (int i = 0; i < chunks.size(); i++) {
            spawn(mc, chunks.get(i), base + i * step, (long) Mth.clamp(seconds * 1000 + 700 - i * step, 1500, 6000));
        }
    }

    private static void spawn(Minecraft mc, String text, long born, long life) {
        var p0 = mc.player;
        if (p0 == null) return;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Vec3 eye = p0.getEyePosition();
        float yaw = p0.getYRot() + (Freelook.active ? Freelook.yaw : 0f);
        float pitch = p0.getXRot() + (Freelook.active ? Freelook.pitch : 0f);

        Popup p = new Popup();
        p.text = text;
        p.width = mc.font.width(text);
        p.scale = (2.4f + rnd.nextFloat() * 1.4f) * Extras.lyricsSize;
        p.born = born;
        p.life = life;
        // somewhere in front of you, near the middle of the screen: "Scatter" is how wide they spread, "Distance" how far away
        double spread = 14.0 + 50.0 * Mth.clamp(Extras.lyricsScatter, 0f, 1f);
        var level = mc.level;
        boolean placed = false;
        for (int tries = 0; tries < 16 && !placed; tries++) {
            double dist = Extras.lyricsDistance * (0.65 + rnd.nextDouble() * 0.45);
            double dy = Math.toRadians(yaw + (rnd.nextDouble() - 0.5) * spread);
            double dp = Math.toRadians(pitch - 4 + (rnd.nextDouble() - 0.5) * spread * 0.5);
            double fx = -Math.sin(dy) * Math.cos(dp), fy = -Math.sin(dp), fz = Math.cos(dy) * Math.cos(dp);
            // never inside a block, and never behind one: stop short of the first block in the way
            Vec3 want = eye.add(fx * dist, fy * dist, fz * dist);
            if (level != null) {
                var hit = level.clip(new net.minecraft.world.level.ClipContext(eye, want,
                        net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, p0));
                if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                    double d2 = hit.getLocation().distanceTo(eye) - 1.2;
                    if (d2 < 2.5) continue;   // a wall right in front of you: try another direction
                    want = eye.add(fx * d2, fy * d2, fz * d2);
                }
                if (!level.getBlockState(net.minecraft.core.BlockPos.containing(want)).getCollisionShape(level, net.minecraft.core.BlockPos.containing(want)).isEmpty()) continue;
            }
            p.wx = want.x; p.wy = want.y; p.wz = want.z;
            boolean ok = true;
            for (Popup o : popups) {   // not right on top of another one
                double ddx = o.wx - p.wx, ddy = o.wy - p.wy, ddz = o.wz - p.wz;
                double min = Extras.lyricsDistance * 0.30;
                if (ddx * ddx + ddy * ddy + ddz * ddz < min * min) ok = false;
            }
            placed = ok || tries >= 12;
        }
        if (!placed && p.wx == 0 && p.wy == 0 && p.wz == 0) { p.wx = eye.x; p.wy = eye.y + 1; p.wz = eye.z; return; }
        double tdx = p.wx - eye.x, tdz = p.wz - eye.z;
        p.faceYaw = (float) Math.toDegrees(Math.atan2(-tdx, tdz));
        float tilt = Mth.clamp(Extras.lyricsTilt, 0f, 1f);
        float amt = (float) Math.sqrt(tilt);
        p.yawOff = (rnd.nextBoolean() ? 1f : -1f) * (0.5f + 0.5f * rnd.nextFloat()) * 70f * amt;   // clearly turned away from you
        p.lean = (rnd.nextFloat() * 2f - 1f) * 22f * amt;                                          // leaning back / forward
        p.roll = (rnd.nextFloat() * 2f - 1f) * 10f * amt;
        p.phase = rnd.nextFloat();
        popups.add(p);
        while (popups.size() > 10) popups.remove(0);
    }

    private static void clearPopups() {
        for (Popup p : popups) if (p.ent != null) p.ent.remove();
        popups.clear();
    }

    private static int nextId = 0;

    /** keeps the 3D words in step: they are real entities that keep one fixed direction and never turn towards you */
    private static void updatePopups(Minecraft mc) {
        var level = mc.level;
        if (level == null || mc.player == null || !Extras.lyrics) {
            if (!popups.isEmpty()) clearPopups();
            return;
        }
        long now = System.currentTimeMillis();
        for (java.util.Iterator<Popup> it = popups.iterator(); it.hasNext(); ) {
            Popup p = it.next();
            if (now - p.born > p.life || (p.ent != null && !p.ent.aliveIn(level))) {
                if (p.ent != null) p.ent.remove();
                it.remove();
            }
        }
        float ref = Math.max(2f, Extras.lyricsDistance);
        int[] pal = PALETTES[Mth.clamp(Extras.lyricsPalette, 0, PALETTES.length - 1)];
        for (Popup p : popups) {
            float age = now - p.born;
            if (age < 0) continue;   // not its turn yet
            // it drifts slowly upwards and sways a little while it hangs there
            double wy = p.wy + age / 1000.0 * 0.3 + Math.sin(age / 700.0 + p.wx) * 0.08;
            if (p.ent == null) {
                p.id = Integer.MAX_VALUE - 200 - (nextId++ % 40);
                p.ent = TextEntity.spawn(level, p.id, p.wx, wy, p.wz);
                if (p.ent == null) continue;
                p.ent.shadow(Extras.lyricsShadow);
                p.ent.bright(true);
            }
            float in = Mth.clamp(age / 260f, 0f, 1f);
            float out = Mth.clamp((p.life - age) / 500f, 0f, 1f);
            float pop = 1f + 0.5f * (1f - in) * (1f - in);                  // flies in a little bigger and settles
            double dxe = p.wx - mc.player.getX(), dye = wy - mc.player.getEyeY(), dze = p.wz - mc.player.getZ();
            float near = Mth.clamp((float) (Math.sqrt(dxe * dxe + dye * dye + dze * dze) - 0.6) / 1.5f, 0f, 1f);   // fades if you walk into it
            // a letter pixel is about p.scale * ref * 0.0026 blocks (the same size the old on-screen words had at the reference distance)
            p.ent.scale(p.scale * ref * 0.104f * pop);
            p.ent.opacity(Math.min(Math.min(in, out), near));
            // turned away from you by yawOff for good: its front points the opposite way the readers look
            float face = p.faceYaw + p.yawOff + (float) Math.sin(age / 900.0 + p.phase * 6.0) * 5f;
            p.ent.moveTo(p.wx, wy, p.wz, face + 180f, p.lean);
            // the colour flows along the words over time
            net.minecraft.network.chat.MutableComponent comp = Component.empty();
            int n = p.text.length();
            for (int i = 0; i < n; i++) {
                float t = (i + 0.5f) / Math.max(1, n);
                float wave = 0.5f + 0.5f * (float) Math.sin((t * 1.4f - age / 1100f - p.phase) * Math.PI * 2.0);
                int rgb = Ui.lerp(pal[0], pal[1], wave) & 0xFFFFFF;
                comp.append(Component.literal(String.valueOf(p.text.charAt(i)))
                        .withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(rgb).withBold(true)));
            }
            p.ent.text(comp);
        }
    }

    /** colour pairs for the flowing gradient: Ice, Violet, Sunset, Neon */
    public static final String[] PALETTE_NAMES = {"Ice", "Violet", "Sunset", "Neon"};
    private static final int[][] PALETTES = {
            {0xFFFFFFFF, 0xFF8FB4FF}, {0xFFFFFFFF, 0xFFB38CFF}, {0xFFFFD27A, 0xFFFF6FA8}, {0xFF5CF2FF, 0xFFC870FF}};

    // ---- the "now singing" pill at the top ----

    private static void renderPill(GuiGraphics g, DeltaTracker delta) {
        if (!Extras.lyricsPill || key.isEmpty() || !songPlaying) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen instanceof MusicScreen) return;   // the drop-down takes its place
        Font font = mc.font;
        int sw = mc.getWindow().getGuiScaledWidth();
        boolean has = !lineText.isBlank();
        String text = has ? lineText : pillTitle;
        if (text.length() > 54) text = text.substring(0, 52) + "...";
        int tw = font.width(text);
        int w = tw + 54, h = 18;
        int x = (sw - w) / 2, y = Extras.topPill ? 28 : 6;
        Ui.rect(g, x, y, w, h, 0xCC0E0B16);
        Ui.rect(g, x + 1, y + 1, w - 2, 1, 0x22FFFFFF);

        // the line fills in as it is sung
        double nowT = songNow + (System.nanoTime() - songNowNanos) / 1e9;
        double prog = has && lineEnd > lineStart ? Mth.clamp((nowT - lineStart) / (lineEnd - lineStart), 0.0, 1.0) : 1.0;
        int k = (int) Math.round(text.length() * prog);
        String done = text.substring(0, k), rest = text.substring(k);
        int tx = x + 10, ty = y + 5;
        g.drawString(font, done, tx, ty, 0xFFFFFFFF, false);
        g.drawString(font, rest, tx + font.width(done), ty, 0x66FFFFFF, false);

        // little animated bars on the right
        long ms = System.currentTimeMillis();
        for (int i = 0; i < 4; i++) {
            int bh = 3 + (int) Math.round(5 * (0.5 + 0.5 * Math.sin(ms / 140.0 + i * 1.7)));
            int bx = x + w - 22 + i * 4;
            Ui.rect(g, bx, y + h / 2f - bh / 2f, 2, bh, 0xFFB79BFF);
        }
    }

    // ---- lyrics from lrclib.net ----

    private static void fetch(String forKey, String title, String artist, int duration) {
        Thread th = new Thread(() -> {
            try {
                List<Line> found = load(title, artist, duration);
                if (forKey.equals(key)) lines = found;
            } catch (Throwable ignored) {
                // no lyrics for this one
            }
        }, "chrome-lyrics");
        th.setDaemon(true);
        th.start();
    }

    private static String get(String url) throws Exception {
        HttpRequest rq = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "ChromeClient (Minecraft mod)")
                .timeout(Duration.ofSeconds(8)).GET().build();
        HttpResponse<String> r = HTTP.send(rq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return r.statusCode() == 200 ? r.body() : null;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String clean(String t) {
        return t.replaceAll("\\s*[(\\[].*?[)\\]]", "").trim();   // drop "(feat. ...)", "[Official Video]"
    }

    private static List<Line> load(String title, String artist, int duration) throws Exception {
        String body = get("https://lrclib.net/api/get?track_name=" + enc(title) + "&artist_name=" + enc(artist) + "&duration=" + duration);
        List<Line> out = body == null ? List.of() : parse(JsonParser.parseString(body));
        if (out.isEmpty()) {   // looser search
            String s = get("https://lrclib.net/api/search?q=" + enc(clean(title) + " " + artist));
            if (s != null) {
                JsonElement e = JsonParser.parseString(s);
                if (e.isJsonArray()) {
                    JsonArray arr = e.getAsJsonArray();
                    for (JsonElement el : arr) {
                        List<Line> l = parse(el);
                        if (!l.isEmpty()) return l;
                    }
                }
            }
        }
        return out;
    }

    private static List<Line> parse(JsonElement e) {
        List<Line> out = new ArrayList<>();
        if (e == null || !e.isJsonObject()) return out;
        JsonObject o = e.getAsJsonObject();
        if (!o.has("syncedLyrics") || o.get("syncedLyrics").isJsonNull()) return out;
        for (String raw : o.get("syncedLyrics").getAsString().split("\n")) {
            Matcher m = STAMP.matcher(raw);
            List<Double> times = new ArrayList<>();
            int end = 0;
            while (m.find()) {
                times.add(Integer.parseInt(m.group(1)) * 60 + Double.parseDouble(m.group(2)));
                end = m.end();
            }
            String text = raw.substring(end).trim();
            for (double t : times) out.add(new Line(t, text));
        }
        out.sort((a, b) -> Double.compare(a.time(), b.time()));
        return out;
    }
}
