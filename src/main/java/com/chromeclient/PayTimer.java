package com.chromeclient;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A timer that watches the game chat for payments ("Steve paid you $500") while it runs
 * and remembers the biggest one. Start / stop it in the Right Shift menu, the result shows on the HUD.
 * If your server words payments differently, put your own pattern in config/chromeclient.json as "payRegex",
 * using named groups: (?<amount>...) and optionally (?<player>...).
 */
public final class PayTimer {
    private static final PayTimer INSTANCE = new PayTimer();

    public static PayTimer get() {
        return INSTANCE;
    }

    boolean running = false;
    boolean finished = false;
    long endMs = 0;
    double best = 0;
    String bestFrom = "";
    int count = 0;
    double total = 0;

    private record Pat(Pattern p, int amount, int who) {}

    private static final String AMT = "([0-9][0-9,]*(?:\\.[0-9]+)?\\s?[kmbt]?)\\b";
    private static final String WHO = "([A-Za-z0-9_]{3,16})";
    private static final Pat[] DEFAULTS = {
            // Steve paid you $500 / Steve has sent you 1,000 / Steve gave you $2k
            new Pat(Pattern.compile("(?i)(?:^|\\W)" + WHO + "\\s+(?:has\\s+)?(?:paid|sent|gave|gifted|transferred)\\s+you\\s+\\$?\\s*" + AMT), 2, 1),
            // You received $500 (from Steve) / You got $500
            new Pat(Pattern.compile("(?i)you\\s+(?:have\\s+)?(?:received|got)\\s+\\$?\\s*" + AMT + "(?:\\s+from\\s+" + WHO + ")?"), 1, 2),
            // You were paid $500 (by Steve)
            new Pat(Pattern.compile("(?i)you\\s+(?:were|have\\s+been|got)\\s+paid\\s+\\$?\\s*" + AMT + "(?:\\s+by\\s+" + WHO + ")?"), 1, 2),
            // $500 received from Steve
            new Pat(Pattern.compile("(?i)\\$\\s*" + AMT + "\\s+(?:was\\s+)?(?:received|sent|paid)\\s+(?:to\\s+you\\s+)?from\\s+" + WHO), 1, 2),
    };

    private Pattern customCache;
    private String customSource = "";

    public boolean visible() {
        return running || finished;
    }

    public long remainingMs() {
        return Math.max(0, endMs - System.currentTimeMillis());
    }

    public void start(int seconds) {
        running = true;
        finished = false;
        endMs = System.currentTimeMillis() + seconds * 1000L;
        best = 0;
        bestFrom = "";
        count = 0;
        total = 0;
    }

    /** ends it early and shows the result */
    public void stop() {
        if (running) finish();
    }

    /** hides the result */
    public void clear() {
        running = false;
        finished = false;
    }

    /** called every frame from the HUD */
    public void update(Minecraft mc) {
        if (running && System.currentTimeMillis() >= endMs) finish();
    }

    private void finish() {
        running = false;
        finished = true;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                String msg = count == 0
                        ? "[Chrome Client] Timer done. No payments came in."
                        : "[Chrome Client] Timer done. Biggest payment: " + money(best)
                        + (bestFrom.isEmpty() ? "" : " from " + bestFrom)
                        + " (" + count + " payments, " + money(total) + " total)";
                mc.player.displayClientMessage(Component.literal(msg), false);
            }
        } catch (Throwable ignored) {
            // the chat line is just a bonus
        }
    }

    /** every chat / server message goes through here while the timer runs */
    public void onMessage(String text) {
        if (!running || text == null || text.isEmpty()) return;
        Pattern custom = customPattern();
        if (custom != null) {
            Matcher m = custom.matcher(text);
            if (m.find()) {
                try {
                    String who;
                    try {
                        who = m.group("player");
                    } catch (IllegalArgumentException e) {
                        who = null;
                    }
                    record(parse(m.group("amount")), who);
                } catch (Exception ignored) {
                    // wrong group names in the custom pattern: ignore the line
                }
            }
            return;
        }
        for (Pat pat : DEFAULTS) {
            Matcher m = pat.p().matcher(text);
            if (m.find()) {
                // "gave you 5 diamonds" is an item, not money: without a $ the number must be followed by a money word or nothing
                boolean dollar = text.substring(m.start(), m.start(pat.amount())).contains("$");
                String rest = text.substring(m.end(pat.amount())).trim();
                if (!dollar && !rest.isEmpty() && Character.isLetter(rest.charAt(0))
                        && !rest.matches("(?is)^(coins?|dollars?|money|cash|bucks|credits?|gold|by|from|for)\\b.*")) continue;
                double v = parse(m.group(pat.amount()));
                String who = m.groupCount() >= pat.who() ? m.group(pat.who()) : null;
                record(v, who);
                return;
            }
        }
    }

    private Pattern customPattern() {
        String src = Config.get().payRegex;
        if (src == null || src.isBlank()) return null;
        if (!src.equals(customSource)) {
            customSource = src;
            try {
                customCache = Pattern.compile(src, Pattern.CASE_INSENSITIVE);
            } catch (PatternSyntaxException e) {
                customCache = null;
            }
        }
        return customCache;
    }

    private void record(double v, String who) {
        if (Double.isNaN(v) || v <= 0) return;
        count++;
        total += v;
        if (v > best) {
            best = v;
            bestFrom = who == null ? "" : who;
        }
    }

    /** "1,234.5" -> 1234.5, "2k" -> 2000, "1.5m" -> 1500000 */
    static double parse(String s) {
        if (s == null) return Double.NaN;
        s = s.replace(",", "").replace(" ", "").toLowerCase(Locale.ROOT);
        double mult = 1;
        if (!s.isEmpty()) {
            char last = s.charAt(s.length() - 1);
            switch (last) {
                case 'k' -> mult = 1e3;
                case 'm' -> mult = 1e6;
                case 'b' -> mult = 1e9;
                case 't' -> mult = 1e12;
                default -> { }
            }
            if (mult != 1) s = s.substring(0, s.length() - 1);
        }
        try {
            return Double.parseDouble(s) * mult;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    static String money(double v) {
        return String.format(Locale.US, v == Math.floor(v) ? "$%,.0f" : "$%,.2f", v);
    }

    static String time(long ms) {
        long s = (ms + 999) / 1000;
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, sec) : String.format("%02d:%02d", m, sec);
    }
}
