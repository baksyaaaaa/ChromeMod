package com.chromeclient;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
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
    private int timerW = 124, timerH = 50;

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
                int mw = 60;
                for (String s : lastLines) mw = Math.max(mw, mc.font.width(s) + 12);
                x = 6; y = 6; w = mw; h = Math.max(1, lastLines.size()) * 11 + 8;
            }
            case "keys" -> { x = 6; y = sh / 2 - 36; w = 70; h = 86; }
            case "armor" -> { x = sw - 56; y = sh / 2 - 40; w = 56; h = 80; }
            case "timer" -> {
                x = 6; y = sh / 2 + 56; w = timerW; h = timerH;
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
        if (cfg.fps && !Extras.topPill) lines.add("FPS: " + mc.getFps());
        if (cfg.cps) lines.add("CPS: " + cpsL + " | " + cpsR);
        if (cfg.ping && !Extras.topPill) {
            PlayerInfo info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(mc.player.getUUID());
            lines.add("Ping: " + (info == null ? "-" : info.getLatency()) + " ms");
        }
        if (cfg.coords) {
            lines.add(String.format("XYZ: %d %d %d", (int) Math.floor(mc.player.getX()),
                    (int) Math.floor(mc.player.getY()), (int) Math.floor(mc.player.getZ())));
        }
        if (cfg.direction) lines.add("Facing: " + mc.player.getDirection().getName());
        if (cfg.clock && !Extras.topPill) lines.add("Time: " + LocalTime.now().format(CLOCK));
        lastLines = lines;
        int[] b = bounds("info", mc);
        g.pose().pushMatrix();
        g.pose().translate((float) b[0], (float) b[1]);
        g.pose().scale(cfg.scale("info"), cfg.scale("info"));
        if (!lines.isEmpty()) {
            int cw = 60;
            for (String s : lines) cw = Math.max(cw, font.width(s) + 12);
            card(g, 0, 0, cw, lines.size() * 11 + 8);
            int y = 5;
            for (String s : lines) {
                int cut = s.indexOf(": ");
                if (cut > 0) {   // quiet label, bright value
                    String lab = s.substring(0, cut + 1);
                    Ui.text(g, font, lab, 6, y, Ui.MUTED);
                    Ui.text(g, font, s.substring(cut + 2), 6 + font.width(lab + " "), y, color);
                } else {
                    Ui.text(g, font, s, 6, y, color);
                }
                y += 11;
            }
        }
        g.pose().popMatrix();

        // ---- pay timer ----
        PayTimer pt = PayTimer.get();
        pt.update(mc);
        if (pt.visible()) {
            String time = pt.running ? PayTimer.time(pt.remainingMs()) : "Done";
            String best = pt.count == 0 ? "none yet" : PayTimer.money(pt.best) + (pt.bestFrom.isEmpty() ? "" : "  " + pt.bestFrom);
            String total = PayTimer.money(pt.total) + "  (" + pt.count + ")";
            int cw = Math.max(124, Math.max(font.width("Best " + best), font.width("Total " + total)) + 14);
            int ch = pt.count > 0 ? 61 : 50;
            timerW = cw; timerH = ch;
            int[] tb = bounds("timer", mc);
            g.pose().pushMatrix();
            g.pose().translate((float) tb[0], (float) tb[1]);
            g.pose().scale(cfg.scale("timer"), cfg.scale("timer"));
            card(g, 0, 0, cw, ch);
            Ui.rrect(g, 6, 6, 6, 6, 3, pt.running ? Ui.ACCENT : Ui.MUTED);
            Ui.text(g, font, "PAY TIMER", 16, 5, Ui.MUTED);
            g.pose().pushMatrix();                      // the time, one and a half times bigger
            g.pose().translate(6f, 17f);
            g.pose().scale(1.5f, 1.5f);
            Ui.text(g, font, time, 0, 0, color);
            g.pose().popMatrix();
            float k = pt.running && pt.totalMs > 0 ? 1f - Mth.clamp(pt.remainingMs() / (float) pt.totalMs, 0f, 1f) : 1f;
            Ui.rrect(g, 6, 32, cw - 12, 3, 1.5f, 0x33FFFFFF);
            if (k > 0.01f) Ui.rrect(g, 6, 32, Math.max(3f, (cw - 12) * k), 3, 1.5f, Ui.ACCENT);
            Ui.text(g, font, "Best", 6, 39, Ui.MUTED);
            Ui.text(g, font, best, 6 + font.width("Best "), 39, color);
            if (pt.count > 0) {
                Ui.text(g, font, "Total", 6, 50, Ui.MUTED);
                Ui.text(g, font, total, 6 + font.width("Total "), 50, color);
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
                card(g, ax - 2, ay - 1, 54, 18);
                g.renderItem(st, ax, ay);
                if (st.isDamageableItem()) {
                    int left = st.getMaxDamage() - st.getDamageValue();
                    float frac = left / (float) Math.max(1, st.getMaxDamage());
                    Ui.text(g, font, String.valueOf(left), ax + 20, ay + 2, color);
                    Ui.rrect(g, ax + 20, ay + 12, 28, 2, 1, 0x33FFFFFF);
                    Ui.rrect(g, ax + 20, ay + 12, Math.max(2f, 28 * frac), 2, 1, frac > 0.5f ? 0xFF58D68D : frac > 0.2f ? 0xFFF4D03F : 0xFFE74C3C);
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
        if (down) {
            Ui.rrect(g, x, y, w, h, 4, Ui.ACCENT);
        } else {
            card(g, x, y, w, h);
        }
        Ui.text(g, font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, down ? Ui.INK : color);
    }

    /** a HUD card in the menu's look: dark glass, thin accent edge, round corners */
    private static void card(GuiGraphics g, int x, int y, int w, int h) {
        Ui.rrect(g, x, y, w, h, 5, (0xB0 << 24) | (Ui.BG & 0xFFFFFF));
        Ui.rring(g, x, y, w, h, 5, Ui.accA(0x55));
    }
}
