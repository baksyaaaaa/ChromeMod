package com.chromeclient;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Buddy (part of Chrome Client): a little buddy wearing any player's skin follows you around. Set it up in the
 * Right Shift menu or with commands:
 *   /buddy <uuid>     whose skin it wears (turns it on)      /buddy name <name>   what it's called
 *   /buddy            switch it on or off                     /buddy size <n>      how big (0.2 - 1.5, default 0.5)
 */
public class Buddy {
    /** the entity id it always uses (so the head-size tweak can tell it apart from real players) */
    public static final int BUDDY_ID = Integer.MAX_VALUE - 7;
    /** how much bigger its head is than the rest of it */
    public static final float HEAD_SCALE = 1.7f;
    /** the emote it is doing right now (BuddyEmotes.*, 0 = none) and the tick it started: read by the model to pose the buddy */
    public static volatile int emote = 0;
    public static volatile int emoteStart = 0;
    /** true while it is walking (read by the model for the waddle) and how strongly the waddle is blended in right now */
    public static volatile boolean walking = false;
    public static float walkBlend = 0f;

    private static UUID skinUuid;
    private static boolean enabled = true;
    private static float size = 0.5f;
    private static String name = "Buddy";

    private static BuddyPlayer buddy;

    /** saved buddies: a name and a skin UUID, switchable from the menu */
    private record Preset(String name, String uuid) {}
    private static final List<Preset> presets = new ArrayList<>();
    private static int presetIdx = -1;   // the preset currently in use (-1 = none / edited since)

    public static int presetCount() { return presets.size(); }
    public static int presetIndex() { return presetIdx; }
    public static String presetName(int i) { return presets.get(i).name(); }

    /** saves the current skin + name as a preset called by the buddy's name (same name = overwritten) */
    public static boolean savePreset() {
        if (skinUuid == null) return false;
        Preset p = new Preset(name, skinUuid.toString());
        for (int i = 0; i < presets.size(); i++) {
            if (presets.get(i).name().equalsIgnoreCase(name)) {
                presets.set(i, p);
                presetIdx = i;
                save();
                return true;
            }
        }
        presets.add(p);
        presetIdx = presets.size() - 1;
        save();
        return true;
    }

    /** switches to preset i (wraps around) */
    public static void usePreset(int i) {
        if (presets.isEmpty()) return;
        i = ((i % presets.size()) + presets.size()) % presets.size();
        Preset p = presets.get(i);
        UUID id = parseUuid(p.uuid());
        if (id == null) return;
        skinUuid = id;
        name = p.name();
        enabled = true;
        presetIdx = i;
        save();
    }

    public static void deletePreset(int i) {
        if (i < 0 || i >= presets.size()) return;
        presets.remove(i);
        presetIdx = -1;
        save();
    }

    // ---- used by the menu ----
    public static boolean enabled() { return enabled; }
    public static float size() { return size; }
    public static boolean hasSkin() { return skinUuid != null; }
    public static String skinFull() { return skinUuid == null ? "" : skinUuid.toString(); }
    public static String skinText() { return skinUuid == null ? "not set" : skinUuid.toString().substring(0, 8) + "..."; }
    public static String name() { return name; }

    public static void setEnabled(boolean on) {
        enabled = on;
        save();
    }

    /** a UUID with or without dashes; false if it isn't one */
    public static boolean setSkin(String raw) {
        UUID id = parseUuid(raw == null ? "" : raw);
        if (id == null) return false;
        skinUuid = id;
        enabled = true;
        presetIdx = -1;
        save();
        return true;
    }

    /** letters, digits and _ only, up to 16 characters (what a player name can be); false if nothing is left */
    public static boolean setName(String raw) {
        String s = cleanName(raw);
        if (s.isEmpty()) return false;
        name = s;
        presetIdx = -1;
        save();
        return true;
    }

    public static void setSize(float v) {
        size = Mth.clamp(v, 0.2f, 1.5f);
        save();
        if (buddy != null) buddy.setSize(size);
    }

    private static String cleanName(String raw) {
        StringBuilder sb = new StringBuilder();
        for (char c : (raw == null ? "" : raw.trim()).toCharArray()) {
            if (c == ' ') c = '_';
            if (c > 32 && c < 127) sb.append(c);
            if (sb.length() >= 16) break;
        }
        return sb.toString();
    }

    /** called once from ChromeClientMod */
    public void init() {
        load();
        ClientTickEvents.START_CLIENT_TICK.register(this::tick);

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandManager.literal("buddy")
                        .executes(ctx -> {
                            setEnabled(!enabled);
                            ctx.getSource().sendFeedback(Component.literal(enabled
                                    ? (skinUuid == null ? "Buddy on. Pick a skin with /buddy <uuid>" : "Buddy is back")
                                    : "Buddy sent home"));
                            return 1;
                        })
                        .then(ClientCommandManager.literal("off").executes(ctx -> {
                            setEnabled(false);
                            ctx.getSource().sendFeedback(Component.literal("Buddy sent home"));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("name")
                                .then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            boolean ok = setName(StringArgumentType.getString(ctx, "name"));
                                            ctx.getSource().sendFeedback(Component.literal(ok ? "Buddy is now called " + name : "Pick a name with letters or numbers"));
                                            return ok ? 1 : 0;
                                        })))
                        .then(ClientCommandManager.literal("size")
                                .then(ClientCommandManager.argument("size", FloatArgumentType.floatArg(0.2f, 1.5f))
                                        .executes(ctx -> {
                                            setSize(FloatArgumentType.getFloat(ctx, "size"));
                                            ctx.getSource().sendFeedback(Component.literal("Buddy size: " + size));
                                            return 1;
                                        })))
                        .then(ClientCommandManager.argument("uuid", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    boolean ok = setSkin(StringArgumentType.getString(ctx, "uuid"));
                                    ctx.getSource().sendFeedback(Component.literal(ok ? "Buddy is now wearing that skin"
                                            : "That doesn't look like a UUID (like 069a79f4-44e9-4726-a5be-fca90e38aaf5)"));
                                    return ok ? 1 : 0;
                                }))));
    }

    /** only keeps the buddy in the world; its moving is done by the buddy itself (BuddyPlayer.aiStep) */
    private void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        ClientLevel level = mc.level;
        if (p == null || level == null) {   // in the menu / loading: forget the old one
            buddy = null;
            return;
        }
        if (!enabled || skinUuid == null) {
            removeBuddy();
            return;
        }
        // (re)create it when it's missing, in another world, or the skin / name was changed
        if (buddy == null || buddy.isRemoved() || buddy.level() != level
                || !skinUuid.equals(buddy.skinUuid) || !name.equals(buddy.buddyName)) {
            removeBuddy();
            buddy = new BuddyPlayer(level, skinUuid, name, size);
            Vec3 look = p.getLookAngle();
            Vec3 pos = p.position();
            buddy.setPos(pos.x - look.x * 2.0, pos.y, pos.z - look.z * 2.0);
            level.addEntity(buddy);
        }
    }

    private static void removeBuddy() {
        if (buddy != null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && buddy.level() == mc.level) {
                mc.level.removeEntity(buddy.getId(), Entity.RemovalReason.DISCARDED);
            }
            buddy = null;
        }
    }

    /** accepts a UUID with or without dashes */
    private static UUID parseUuid(String raw) {
        String s = raw.trim().replace("-", "");
        if (!s.matches("[0-9a-fA-F]{32}")) return null;
        return UUID.fromString(s.replaceFirst("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5"));
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("buddy.json");
    }

    private static void load() {
        try {
            Path f = file();
            if (!Files.isRegularFile(f)) {   // settings from before the rename
                f = FabricLoader.getInstance().getConfigDir().resolve("baby-buddy.json");
                if (!Files.isRegularFile(f)) return;
            }
            JsonObject o = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            if (o.has("uuid")) skinUuid = parseUuid(o.get("uuid").getAsString());
            if (o.has("enabled")) enabled = o.get("enabled").getAsBoolean();
            if (o.has("size")) size = Mth.clamp(o.get("size").getAsFloat(), 0.2f, 1.5f);
            if (o.has("name")) {
                String n = cleanName(o.get("name").getAsString());
                if (!n.isEmpty()) name = n.equals("Baby") ? "Buddy" : n;
            }
            if (o.has("presets")) {
                for (var e : o.getAsJsonArray("presets")) {
                    JsonObject po = e.getAsJsonObject();
                    String pn = cleanName(po.get("name").getAsString());
                    String pu = po.get("uuid").getAsString();
                    if (!pn.isEmpty() && parseUuid(pu) != null) presets.add(new Preset(pn, pu));
                }
            }
        } catch (Exception ignored) {
            // a broken config just means defaults
        }
    }

    private static void save() {
        try {
            JsonObject o = new JsonObject();
            if (skinUuid != null) o.addProperty("uuid", skinUuid.toString());
            o.addProperty("enabled", enabled);
            o.addProperty("size", size);
            o.addProperty("name", name);
            JsonArray arr = new JsonArray();
            for (Preset p : presets) {
                JsonObject po = new JsonObject();
                po.addProperty("name", p.name());
                po.addProperty("uuid", p.uuid());
                arr.add(po);
            }
            o.add("presets", arr);
            Files.writeString(file(), o.toString());
        } catch (Exception ignored) {
            // can't save: it still works this session
        }
    }
}
