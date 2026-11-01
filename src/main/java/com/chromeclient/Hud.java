package com.chromeclient;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Draws the on-screen info: text lines, keystrokes, armor status. Each can be dragged in the HUD editor. */
public final class Hud {
    /** movable elements: our own three, then three from the vanilla game (moved by an offset in ChromeClientMod). */
    public static final String[] IDS = {"info", "keys", "armor", "timer", "scoreboard", "boss_bar", "status_effects",
            "hotbar", "health_bar", "food_bar", "armor_bar", "air_bar", "info_bar", "experience_level",
            "held_item_tooltip", "overlay_message", "title_and_subtitle", "tab_list", "subtitles"};
    public static final String[] LABELS = {"Info", "Keystrokes", "Armor status", "Pay timer", "Leaderboard", "Boss bar", "Effects",
            "Hotbar", "Health", "Food", "Armor points", "Air", "XP bar", "XP",
            "Item name", "Action bar", "Titles", "Player list", "Subtitles"};
    /** the ids of the vanilla ones (everything from index 4 on) */
    public static boolean isVanilla(int index) { return index >= 4; }

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final EquipmentSlot[] ARMOR = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private final ArrayDeque<Long> leftClicks = new ArrayDeque<>();
    private final ArrayDeque<Long> rightClicks = new ArrayDeque<>();
    private boolean wasLeft, wasRight;
    private List<String> lastLines = new ArrayList<>();
    private List<String> lastTimerLines = new ArrayList<>();

    private static int count(ArrayDeque<Long> q, long now) {
        while (!q.isEmpty() && now - q.peekFirst() > 1000) q.pollFirst();
        return q.size();
    }

    /** {x, y, width, height} of an element where it currently sits (default spot + your drag, at your size). */
    public int[] bounds(String id, Minecraft mc) {
        int[] b = base(id, mc);
        int[] o = Config.get().off(id);
        float s = Config.get().scale(id);
        return new int[]{b[0] + o[0], b[1] + o[1], Math.round(b[2] * s), Math.round(b[3] * s)};
    }

    /** {x, y, width, height} of an element's default spot at normal size (no drag, no resize). */
    public int[] base(String id, Minecraft mc) {
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int x, y, w, h;
        switch (id) {
            case "info" -> {
                int mw = 70;
                for (String s : lastLines) mw = Math.max(mw, mc.font.width(s) + 4);
                x = 6; y = 6; w = mw; h = Math.max(1, lastLines.size()) * 13;
            }
            case "keys" -> { x = 6; y = sh / 2 - 36; w = 70; h = 86; }
            case "armor" -> { x = sw - 56; y = sh / 2 - 40; w = 56; h = 80; }
            case "timer" -> {
                int mw = 100;
                for (String s : lastTimerLines) mw = Math.max(mw, mc.font.width(s) + 4);
                x = 6; y = sh / 2 + 56; w = mw; h = Math.max(2, lastTimerLines.size()) * 13;
            }
            case "scoreboard" -> {   // measured from the real leaderboard when there is one, else a guess
                int[] m = measureScoreboard(mc, sw, sh);
                if (m != null) { x = m[0]; y = m[1]; w = m[2]; h = m[3]; }
                else { x = sw - 63; y = sh / 2 - 40; w = 60; h = 56; }
            }
            case "boss_bar" -> { x = sw / 2 - 91; y = 12; w = 182; h = 20; }
            case "status_effects" -> {   // 25 px per effect, starting at the right edge
                int n = mc.player == null ? 1 : Math.max(1, mc.player.getActiveEffects().size());
                w = 25 * n; h = 26; x = sw - w; y = 1;
            }
            case "hotbar" -> { x = sw / 2 - 91; y = sh - 22; w = 182; h = 22; }
            case "health_bar" -> { x = sw / 2 - 91; y = sh - 39; w = 81; h = 10; }
            case "food_bar" -> { x = sw / 2 + 10; y = sh - 39; w = 81; h = 10; }
            case "armor_bar" -> { x = sw / 2 - 91; y = sh - 49; w = 81; h = 10; }
            case "air_bar" -> { x = sw / 2 + 10; y = sh - 49; w = 81; h = 10; }
            case "info_bar" -> { x = sw / 2 - 91; y = sh - 29; w = 182; h = 5; }
            case "experience_level" -> { x = sw / 2 - 10; y = sh - 36; w = 20; h = 9; }
            case "held_item_tooltip" -> { x = sw / 2 - 60; y = sh - 60; w = 120; h = 10; }
            case "overlay_message" -> { x = sw / 2 - 100; y = sh - 70; w = 200; h = 12; }
            case "title_and_subtitle" -> { x = sw / 2 - 100; y = sh / 2 - 60; w = 200; h = 50; }
            case "tab_list" -> {   // sized like the real player list when we can work it out
                int[] m = measureTabList(mc, sw);
                if (m != null) { x = m[0]; y = m[1]; w = m[2]; h = m[3]; }
                else { x = sw / 2 - 150; y = 10; w = 300; h = 100; }
            }
            default -> { x = sw - 130; y = sh - 70; w = 120; h = 40; }   // subtitles
        }
        return new int[]{x, y, w, h};
    }

    /**
     * The player list is centred at the top; its columns and widths follow the vanilla layout
     * (up to 20 rows per column, widest name + head + ping per column). Returns null if it can't be worked out.
     */
    private int[] measureTabList(Minecraft mc, int sw) {
        return null;   // not measured (yet): the box uses a centred default
    }

    /**
     * Works out where the game draws the sidebar leaderboard and how big it is, using the same maths as the game:
     * as wide as the title or the longest "name + score" line (max 15 lines), 9 px per line plus the title bar,
     * flush to the right edge and centred a little below the middle. Returns null if there is none (or anything is unreadable).
     */
    private int[] measureScoreboard(Minecraft mc, int sw, int sh) {
        try {
            if (mc.level == null) return null;
            Scoreboard board = mc.level.getScoreboard();
            Objective obj = board.getDisplayObjective(DisplaySlot.SIDEBAR);
            if (obj == null) return null;
            Font font = mc.font;
            NumberFormat nf = obj.numberFormat();
            List<PlayerScoreEntry> list = new ArrayList<>();
            for (PlayerScoreEntry e : board.listPlayerScores(obj)) {
                if (!e.isHidden()) list.add(e);
            }
            list.sort(Comparator.comparingInt(PlayerScoreEntry::value).reversed().thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER));
            if (list.size() > 15) list = list.subList(0, 15);

            int maxWidth = font.width(obj.getDisplayName());
            int colon = font.width(": ");
            for (PlayerScoreEntry e : list) {
                Component name = PlayerTeam.formatNameForTeam(board.getPlayersTeam(e.owner()), e.ownerName());
                NumberFormat eff = e.numberFormatOverride() != null ? e.numberFormatOverride() : nf;
                int scoreW = eff != null ? font.width(e.formatValue(eff)) : font.width(String.valueOf(e.value()));
                maxWidth = Math.max(maxWidth, font.width(name) + (scoreW > 0 ? colon + scoreW : 0));
            }
            int bodyH = list.size() * 9;
            int bottom = sh / 2 + bodyH / 3;
            // background: from (left - 2) to 1 px short of the right edge, from above the title bar down to the last line
            return new int[]{sw - maxWidth - 5, bottom - bodyH - 10, maxWidth + 4, bodyH + 10};
        } catch (Throwable t) {
            return null;
        }
    }

    public boolean enabled(String id) {
        Config c = Config.get();
        return switch (id) {
            case "info" -> c.fps || c.cps || c.ping || c.coords || c.direction || c.clock;
            case "keys" -> c.keystrokes;
            case "armor" -> c.armor;
            case "timer" -> PayTimer.get().visible();
            default -> true;
        };
    }

    public void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        Config cfg = Config.get();
        Font font = mc.font;
        long now = System.currentTimeMillis();

        // clicks per second: count each press of the mouse buttons (checked every frame)
        boolean l = mc.options.keyAttack.isDown(), r = mc.options.keyUse.isDown();
        if (l && !wasLeft) leftClicks.addLast(now);
        if (r && !wasRight) rightClicks.addLast(now);
        wasLeft = l;
        wasRight = r;
        int cpsL = count(leftClicks, now), cpsR = count(rightClicks, now);

        int color = cfg.textColor();

        // ---- text lines ----
        List<String> lines = new ArrayList<>();
        if (cfg.fps) lines.add("FPS: " + mc.getFps());
        if (cfg.cps) lines.add("CPS: " + cpsL + " | " + cpsR);
        if (cfg.ping) {
            PlayerInfo info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(mc.player.getUUID());
            lines.add("Ping: " + (info == null ? "-" : info.getLatency()) + " ms");
        }
        if (cfg.coords) {
            lines.add(String.format("XYZ: %d %d %d", (int) Math.floor(mc.player.getX()),
                    (int) Math.floor(mc.player.getY()), (int) Math.floor(mc.player.getZ())));
        }
        if (cfg.direction) lines.add("Facing: " + mc.player.getDirection().getName());
        if (cfg.clock) lines.add("Time: " + LocalTime.now().format(CLOCK));
        lastLines = lines;
        int[] b = bounds("info", mc);
        g.pose().pushMatrix();
        g.pose().translate((float) b[0], (float) b[1]);
        g.pose().scale(cfg.scale("info"), cfg.scale("info"));
        int y = 0;
        for (String s : lines) {
            int w = font.width(s);
            g.fill(-2, y - 2, 2 + w, y + 10, 0x66000000);
            g.drawString(font, s, 0, y, color, true);
            y += 13;
        }
        g.pose().popMatrix();

        // ---- pay timer ----
        PayTimer pt = PayTimer.get();
        pt.update(mc);
        List<String> tl = new ArrayList<>();
        if (pt.visible()) {
            tl.add(pt.running ? "Timer: " + PayTimer.time(pt.remainingMs()) : "Timer: done");
            tl.add(pt.count == 0 ? "Best paid: none yet"
                    : "Best paid: " + PayTimer.money(pt.best) + (pt.bestFrom.isEmpty() ? "" : " (" + pt.bestFrom + ")"));
            if (pt.count > 0) tl.add("Total: " + PayTimer.money(pt.total) + "  (" + pt.count + ")");
        }
        lastTimerLines = tl;
        if (!tl.isEmpty()) {
            int[] tb = bounds("timer", mc);
            g.pose().pushMatrix();
            g.pose().translate((float) tb[0], (float) tb[1]);
            g.pose().scale(cfg.scale("timer"), cfg.scale("timer"));
            int ty = 0;
            for (String s : tl) {
                g.fill(-2, ty - 2, 2 + font.width(s), ty + 10, 0x66000000);
                g.drawString(font, s, 0, ty, color, true);
                ty += 13;
            }
            g.pose().popMatrix();
        }

        // ---- keystrokes ----
        if (cfg.keystrokes) {
            int[] kb = bounds("keys", mc);
            g.pose().pushMatrix();
            g.pose().translate((float) kb[0], (float) kb[1]);
            g.pose().scale(cfg.scale("keys"), cfg.scale("keys"));
            int size = 22, gap = 2, x0 = 0, y0 = 0;
            key(g, font, "W", x0 + size + gap, y0, size, mc.options.keyUp.isDown(), color);
            key(g, font, "A", x0, y0 + size + gap, size, mc.options.keyLeft.isDown(), color);
            key(g, font, "S", x0 + size + gap, y0 + size + gap, size, mc.options.keyDown.isDown(), color);
            key(g, font, "D", x0 + 2 * (size + gap), y0 + size + gap, size, mc.options.keyRight.isDown(), color);
            int half = (3 * size + 2 * gap - gap) / 2;
            int ly = y0 + 2 * (size + gap);
            wide(g, font, "LMB", x0, ly, half, size, l, color);
            wide(g, font, "RMB", x0 + half + gap, ly, half, size, r, color);
            wide(g, font, "SPACE", x0, ly + size + gap, 3 * size + 2 * gap, 14, mc.options.keyJump.isDown(), color);
            g.pose().popMatrix();
        }

        // ---- armor status ----
        if (cfg.armor) {
            int[] ab = bounds("armor", mc);
            g.pose().pushMatrix();
            g.pose().translate((float) ab[0], (float) ab[1]);
            g.pose().scale(cfg.scale("armor"), cfg.scale("armor"));
            int ay = 0, ax = 0;
            for (EquipmentSlot slot : ARMOR) {
                ItemStack st = mc.player.getItemBySlot(slot);
                if (st.isEmpty()) continue;
                g.renderItem(st, ax, ay);
                if (st.isDamageableItem()) {
                    int left = st.getMaxDamage() - st.getDamageValue();
                    g.drawString(font, String.valueOf(left), ax + 20, ay + 4, color, true);
                }
                ay += 20;
            }
            g.pose().popMatrix();
        }
    }

    private void key(GuiGraphics g, Font font, String label, int x, int y, int size, boolean down, int color) {
        wide(g, font, label, x, y, size, size, down, color);
    }

    private void wide(GuiGraphics g, Font font, String label, int x, int y, int w, int h, boolean down, int color) {
        g.fill(x, y, x + w, y + h, down ? 0xAAFFFFFF : 0x66000000);
        int tc = down ? 0xFF101010 : color;
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, tc, false);
    }
}
