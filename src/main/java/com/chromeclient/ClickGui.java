package com.chromeclient;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * The Right Shift menu: a frosted-glass window with tabs on top, a module list on the left and that module's settings on the right.
 * The Cosmetics tab is a wardrobe with a try-on preview. Everything applies instantly and is saved when you close it.
 */
public class ClickGui extends Screen {
    private static final int W = 430, H = 252, PAD = 8, HEAD = 28, LIST_W = 112, ROW = 22, GAP = 4;
    private static final String[] TABS = {"HUD", "Render", "Misc", "Music", "Cosmetics"};
    private static final int COS = 4;

    private enum K { TOGGLE, SLIDER, CHOICE, SWATCH, ACTION, TEXT, PREVIEW }

    private static final class Opt {
        K k;
        String label;
        Supplier<String> labelS;
        BooleanSupplier getB;
        Consumer<Boolean> setB;
        DoubleSupplier getD;
        Consumer<Double> setD;
        double min, max;
        String fmt = "%.1f";
        String[] choices;
        IntSupplier getI;
        IntConsumer setI;
        int[] colors;
        Runnable act;
        BooleanSupplier vis = () -> true;

        Opt show(BooleanSupplier v) { vis = v; return this; }
        int height() { return k == K.PREVIEW ? 46 : ROW; }
    }

    private static final class Mod {
        String name, desc;
        BooleanSupplier on;
        Consumer<Boolean> set;
        final List<Opt> opts = new ArrayList<>();

        Mod(String name, String desc, BooleanSupplier on, Consumer<Boolean> set) {
            this.name = name; this.desc = desc; this.on = on; this.set = set;
        }
        Mod add(Opt o) { opts.add(o); return this; }
    }

    /** a HUD panel module: on/off plus where it sits and how big */
    private static Mod panelMod(String name, String desc, HudPack.Panel p) {
        Mod m = new Mod(name, desc, () -> p.on, v -> p.on = v);
        m.add(choice("Corner", HudPack.CORNERS, () -> p.corner, i -> p.corner = i));
        m.add(slider("Across", () -> p.ox, v -> p.ox = v.intValue(), 0, 400, "%d"));
        m.add(slider("Down", () -> p.oy, v -> p.oy = v.intValue(), 0, 300, "%d"));
        m.add(slider("Size", () -> p.scale, v -> p.scale = v.floatValue(), 0.5, 2.5, "x%.1f"));
        return m;
    }

    private static Opt toggle(String l, BooleanSupplier g, Consumer<Boolean> s) {
        Opt o = new Opt(); o.k = K.TOGGLE; o.label = l; o.getB = g; o.setB = s; return o;
    }
    private static Opt slider(String l, DoubleSupplier g, Consumer<Double> s, double min, double max, String fmt) {
        Opt o = new Opt(); o.k = K.SLIDER; o.label = l; o.getD = g; o.setD = s; o.min = min; o.max = max; o.fmt = fmt; return o;
    }
    private static Opt choice(String l, String[] ch, IntSupplier g, IntConsumer s) {
        Opt o = new Opt(); o.k = K.CHOICE; o.label = l; o.choices = ch; o.getI = g; o.setI = s; return o;
    }
    private static Opt swatch(String l, int[] cols, IntSupplier g, IntConsumer s) {
        Opt o = new Opt(); o.k = K.SWATCH; o.label = l; o.colors = cols; o.getI = g; o.setI = s; return o;
    }
    private static Opt action(Supplier<String> l, Runnable r) {
        Opt o = new Opt(); o.k = K.ACTION; o.labelS = l; o.act = r; return o;
    }
    private static Opt text(Supplier<String> l) {
        Opt o = new Opt(); o.k = K.TEXT; o.labelS = l; return o;
    }

    private final Hud hud;
    private final List<List<Mod>> tabs = new ArrayList<>();
    private final int[] sel = new int[TABS.length];
    private int tab = 0, cosCat = 0, cosScroll = 0;
    private boolean wasDownNow = false, pendingClick = false;

    /** the game's own click event: a backup in case polling the mouse button misses the press */
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) pendingClick = true;
        return true;
    }
    private final List<Opt> effOpts = new ArrayList<>();
    private final List<Opt> syncOpts = new ArrayList<>();
    private int scroll = 0;
    private double pendingScroll = 0;
    private Opt dragging = null;
    private boolean wasDown = true;
    private float openT = 0f, underX = -1f;
    private final float[] tabHov = new float[TABS.length];
    private final float[] listHov = new float[16];
    private final float[] cellHov = new float[64];
    private long lastNanos = System.nanoTime();
    private Runnable later = null;

    public ClickGui(Hud hud) {
        super(Component.literal("Chrome Client"));
        this.hud = hud;
        Config c = Config.get();

        // ---- HUD ----
        List<Mod> hudMods = new ArrayList<>();
        hudMods.add(new Mod("FPS", "Frames per second on the HUD.", () -> c.fps, v -> c.fps = v));
        hudMods.add(new Mod("CPS", "Left and right clicks per second.", () -> c.cps, v -> c.cps = v));
        hudMods.add(new Mod("Ping", "Your latency to the server.", () -> c.ping, v -> c.ping = v));
        hudMods.add(new Mod("Coordinates", "XYZ position.", () -> c.coords, v -> c.coords = v));
        hudMods.add(new Mod("Direction", "Which way you are facing.", () -> c.direction, v -> c.direction = v));
        hudMods.add(new Mod("Clock", "Your local time.", () -> c.clock, v -> c.clock = v));
        hudMods.add(new Mod("Keystrokes", "WASD, space and mouse buttons.", () -> c.keystrokes, v -> c.keystrokes = v));
        hudMods.add(new Mod("Armor status", "Armor pieces and durability.", () -> c.armor, v -> c.armor = v));
        hudMods.add(new Mod("Pay timer", "Counts down and reads payment messages.", null, null)
                .add(text(() -> {
                    PayTimer pt = PayTimer.get();
                    return "Status: " + (pt.running ? PayTimer.time(pt.remainingMs()) : (pt.finished ? "Done" : "Ready"));
                }))
                .add(action(() -> PayTimer.get().running ? "Stop timer" : "Start timer", () -> {
                    PayTimer pt = PayTimer.get();
                    if (pt.running) pt.stop(); else pt.start(c.payTimerSeconds);
                }))
                .add(slider("Minutes", () -> c.payTimerSeconds / 60, v -> c.payTimerSeconds = Math.max(5, v.intValue() * 60 + c.payTimerSeconds % 60), 0, 60, "%d"))
                .add(slider("Seconds", () -> c.payTimerSeconds % 60, v -> c.payTimerSeconds = Math.max(5, (c.payTimerSeconds / 60) * 60 + v.intValue()), 0, 59, "%d")));
        hudMods.add(panelMod("Potion list", "Active effects with their time left.", HudPack.potions));
        hudMods.add(panelMod("Keybind list", "Movement and attack keys, lit while held.", HudPack.binds));
        hudMods.add(panelMod("Target HUD", "Health and distance of what you are fighting.", HudPack.target));
        hudMods.add(panelMod("Performance", "FPS graph, memory and CPU.", HudPack.perf));
        hudMods.add(panelMod("Watermark", "Small Chrome Client tag.", HudPack.mark));
        hudMods.add(new Mod("Move HUD", "Drag every HUD piece where you want it.", null, null)
                .add(action(() -> "Open the editor", () -> later = () -> { Config.save(); Minecraft.getInstance().setScreen(new EditHudScreen(this.hud)); })));
        tabs.add(hudMods);

        // ---- Render ----
        List<Mod> render = new ArrayList<>();
        Mod hand = new Mod("Hand position", "Move, shrink or tilt the item in your hand.", () -> Visuals.handOn, v -> Visuals.handOn = v);
        hand.add(slider("Sideways", () -> Visuals.handX, v -> Visuals.handX = v.floatValue(), -60, 60, "%d"));
        hand.add(slider("Up / down", () -> Visuals.handY, v -> Visuals.handY = v.floatValue(), -60, 60, "%d"));
        hand.add(slider("Forward / back", () -> Visuals.handZ, v -> Visuals.handZ = v.floatValue(), -60, 60, "%d"));
        hand.add(slider("Size", () -> Visuals.handScale, v -> Visuals.handScale = v.floatValue(), 0.4, 1.6, "x%.2f"));
        hand.add(slider("Tilt", () -> Visuals.handTilt, v -> Visuals.handTilt = v.floatValue(), -45, 45, "%d deg"));
        render.add(hand);
        Mod lowFire = new Mod("Low fire", "Draws the fire on your screen lower so you can see.", () -> Visuals.fireOn, v -> Visuals.fireOn = v);
        lowFire.add(slider("Fire height %", () -> Visuals.fireHeight, v -> Visuals.fireHeight = v.floatValue(), 0, 100, "%d"));
        render.add(lowFire);
        Mod ch = new Mod("Crosshair", "Your own crosshair instead of the game's.", () -> HudPack.crossOn, v -> HudPack.crossOn = v);
        ch.add(choice("Shape", HudPack.SHAPES, () -> HudPack.crossShape, i -> HudPack.crossShape = i));
        ch.add(slider("Size", () -> HudPack.crossSize, v -> HudPack.crossSize = v.intValue(), 1, 16, "%d"));
        ch.add(slider("Gap", () -> HudPack.crossGap, v -> HudPack.crossGap = v.intValue(), 0, 10, "%d"));
        ch.add(slider("Thickness", () -> HudPack.crossThick, v -> HudPack.crossThick = v.intValue(), 1, 4, "%d"));
        ch.add(swatch("Color", HudPack.CROSS_COLORS, () -> HudPack.crossColor, i -> HudPack.crossColor = i));
        ch.add(toggle("Outline", () -> HudPack.crossOutline, v -> HudPack.crossOutline = v));
        ch.add(toggle("Opens when moving", () -> HudPack.crossDynamic, v -> HudPack.crossDynamic = v));
        render.add(ch);
        render.add(new Mod("Freelook", "Hold Left Alt to look around without turning.", () -> Extras.freelook, v -> Extras.freelook = v));
        render.add(new Mod("Zoom", "Hold C to zoom in.", () -> c.zoom, v -> c.zoom = v));
        tabs.add(render);

        // ---- Misc ----
        List<Mod> misc = new ArrayList<>();
        misc.add(new Mod("Menu theme", "Colours of this menu and the panels.", null, null)
                .add(choice("Theme", Ui.THEMES, () -> Extras.uiTheme, i -> { Extras.uiTheme = i; Ui.setTheme(i); })));
        misc.add(new Mod("Auto sprint", "Sprints for you while you walk forward.", () -> c.autoSprint, v -> c.autoSprint = v));
        misc.add(new Mod("Emotes", "Press B for an emote wheel. Look at yourself in third person (F5).", () -> Extras.emotes, v -> Extras.emotes = v));
        misc.add(new Mod("Buddy", "A little copy of a skin that follows you.", Buddy::enabled, Buddy::setEnabled)
                .add(slider("Size", Buddy::size, v -> Buddy.setSize(v.floatValue()), 0.2, 1.5, "%.2f"))
                .add(text(() -> "Name: " + Buddy.name() + "   Skin: " + Buddy.skinText()))
                .add(action(() -> "Skin, name and presets", () -> later = () -> { Config.save(); Minecraft.getInstance().setScreen(new BuddyScreen(this)); })));
        tabs.add(misc);

        // ---- Music ----
        List<Mod> music = new ArrayList<>();
        music.add(new Mod("World lyrics", "Big glowing words around you, in time with the song.", () -> Extras.lyrics, v -> Extras.lyrics = v)
                .add(slider("Distance", () -> Extras.lyricsDistance, v -> Extras.lyricsDistance = v.floatValue(), 3, 20, "%.1f"))
                .add(slider("Size", () -> Extras.lyricsSize, v -> Extras.lyricsSize = v.floatValue(), 0.5, 3, "%.1f"))
                .add(slider("Turn away", () -> Extras.lyricsTilt, v -> Extras.lyricsTilt = v.floatValue(), 0, 1, "%.1f"))
                .add(choice("Colors", Lyrics.PALETTE_NAMES, () -> Extras.lyricsPalette, i -> Extras.lyricsPalette = i))
                .add(slider("Scatter", () -> Extras.lyricsScatter, v -> Extras.lyricsScatter = v.floatValue(), 0, 1, "%.1f"))
                .add(slider("Sync offset", () -> Extras.lyricsSync, v -> Extras.lyricsSync = (float) (Math.round(v * 10) / 10.0), -3, 3, "%+.1f s"))
                .add(toggle("Glow", () -> Extras.lyricsGlow, v -> Extras.lyricsGlow = v))
                .add(slider("Glow strength", () -> Extras.lyricsGlowStrength, v -> Extras.lyricsGlowStrength = v.floatValue(), 0, 1, "%.1f"))
                .add(toggle("Shadow", () -> Extras.lyricsShadow, v -> Extras.lyricsShadow = v))
                .add(toggle("Now singing pill", () -> Extras.lyricsPill, v -> Extras.lyricsPill = v)));
        music.add(new Mod("YT Music", "Player drop-down. Needs the YT Music mod.", null, null)
                .add(text(() -> YtBridge.installed() ? "YT Music mod found. Key: J" : "YT Music mod not installed"))
                .add(action(() -> "Open player", () -> { if (YtBridge.installed()) later = () -> { Config.save(); Extras.save(); Minecraft.getInstance().setScreen(new MusicScreen(this)); }; }))
                .add(toggle("Hide its top-right bar", () -> Extras.hideYtBar, v -> Extras.hideYtBar = v)));
        tabs.add(music);
        tabs.add(new ArrayList<>());   // cosmetics has its own layout
        syncOpts.add(toggle("Show mine to others", () -> CosmeticSync.share, v -> CosmeticSync.share = v));
        syncOpts.add(text(() -> !CosmeticSync.on() ? "Sharing is not available yet" : (CosmeticSync.status.isEmpty() ? "Sharing is on" : "Status: " + CosmeticSync.status)));
        effOpts.add(toggle("Particles", () -> Cosmetics.effectOn, v -> Cosmetics.effectOn = v));
        effOpts.add(choice("Where", Cosmetics.WHERE, () -> Cosmetics.effectWhere, i -> Cosmetics.effectWhere = i));
        effOpts.add(slider("Amount", () -> Cosmetics.effectAmount, v -> Cosmetics.effectAmount = v.floatValue(), 0.1, 1, "%.1f"));
    }

    // ---------------------------------------------------------------- layout
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        Minecraft mc = Minecraft.getInstance();
        float s = Math.min(1f, Math.min((height - 8f) / H, (width - 8f) / W));
        int vw = Math.round(width / s), vh = Math.round(height / s);
        int mx = Math.round(mouseX / s), my = Math.round(mouseY / s);
        boolean down = EditHudScreen.leftDown(mc);
        boolean click = (down && !wasDown) || pendingClick;   // also counts a click the game itself reported
        pendingClick = false;
        wasDownNow = down;
        wasDown = down;
        if (!down) dragging = null;

        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        float ease = 1f - (float) Math.exp(-dt * 18f);
        openT += (1f - openT) * (1f - (float) Math.exp(-dt * 12f));
        Ui.fade = openT;

        int x = (vw - W) / 2, y = (vh - H) / 2 + Math.round((1f - openT) * 6);
        g.pose().pushMatrix();
        g.pose().scale(s, s);

        Ui.glass(g, x, y, W, H);

        // ---- header: logo, tabs, close ----
        Ui.rrect(g, x + PAD + 2, y + 10, 9, 9, 4.5f, Ui.ACCENT);
        Ui.rrect(g, x + PAD + 4.5f, y + 12.5f, 4, 4, 2, 0xFF1A1430);
        g.drawString(font, Component.literal("Chrome Client").withStyle(ChatFormatting.BOLD), x + PAD + 16, y + 10, Ui.mul(Ui.TEXT), false);
        int tx = x + PAD + 16 + font.width("Chrome Client") + 18;
        float targetUnder = 0, underW = 0;
        for (int i = 0; i < TABS.length; i++) {
            int tw = font.width(TABS[i]) + 14;
            boolean hov = Ui.in(mx, my, tx, y + 4, tw, 20);
            tabHov[i] += ((hov ? 1f : 0f) - tabHov[i]) * ease;
            int col = Ui.lerp(i == tab ? Ui.TEXT : Ui.MUTED, Ui.TEXT, tabHov[i]);
            Ui.text(g, font, TABS[i], tx + 7, y + 10, col);
            if (i == tab) { targetUnder = tx + 7; underW = font.width(TABS[i]); }
            if (click && hov && i != tab) { tab = i; scroll = 0; dragging = null; }
            tx += tw;
        }
        if (underX < 0) underX = targetUnder;
        underX += (targetUnder - underX) * ease;
        Ui.rect(g, underX, y + 22, Math.max(6f, underW), 2, Ui.ACCENT);
        String ver = "v" + net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(ChromeClientMod.MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
        Ui.text(g, font, ver, x + W - 30 - font.width(ver), y + 10, Ui.MUTED);
        boolean hClose = Ui.in(mx, my, x + W - 24, y + 5, 18, 18);
        Ui.rect(g, x + W - 24, y + 5, 18, 18, hClose ? Ui.HOVER : Ui.SURFACE);
        Ui.text(g, font, "x", x + W - 24 + (18 - font.width("x")) / 2, y + 10, Ui.TEXT);
        if (click && hClose) onClose();
        g.fill(x + PAD, y + HEAD + 2, x + W - PAD, y + HEAD + 3, Ui.mul(0x22FFFFFF));

        int bodyTop = y + HEAD + 8, bodyBot = y + H - PAD;
        if (tab == COS) drawWardrobe(g, mx, my, click, ease, x, bodyTop, bodyBot);
        else drawModules(g, mx, my, click, down, ease, x, bodyTop, bodyBot);

        g.pose().popMatrix();
        Ui.fade = 1f;
        pendingScroll = 0;

        if (later != null) {
            Runnable r = later;
            later = null;
            r.run();
        }
    }

    private void drawModules(GuiGraphics g, int mx, int my, boolean click, boolean down, float ease, int x, int top, int bot) {
        List<Mod> mods = tabs.get(tab);
        // ---- module list ----
        int lx = x + PAD, ly = top;
        for (int i = 0; i < mods.size(); i++) {
            Mod m = mods.get(i);
            int ry = ly + i * (ROW + 2);
            if (ry + ROW > bot) break;
            boolean hov = Ui.in(mx, my, lx, ry, LIST_W, ROW);
            listHov[i] += ((hov ? 1f : 0f) - listHov[i]) * ease;
            boolean selected = sel[tab] == i;
            Ui.rect(g, lx, ry, LIST_W, ROW, selected ? Ui.accA(0x44) : Ui.lerp(Ui.SURFACE, Ui.HOVER, listHov[i]));
            if (selected) Ui.rring(g, lx, ry, LIST_W, ROW, 3, Ui.accA(0xAA));
            Ui.textFit(g, font, m.name, lx + 8, ry + (ROW - 8) / 2, LIST_W - 8 - 20, selected ? Ui.TEXT : Ui.lerp(Ui.MUTED, Ui.TEXT, listHov[i]));
            if (m.on != null) {
                boolean on = m.on.getAsBoolean();
                Ui.rrect(g, lx + LIST_W - 14, ry + ROW / 2f - 3, 6, 6, 3, on ? Ui.ACCENT : 0xFF4A505A);
            }
            if (click && hov) { sel[tab] = i; scroll = 0; dragging = null; }
        }

        // ---- settings pane ----
        int px = lx + LIST_W + PAD, pw = x + W - PAD - px;
        Mod m = mods.get(Math.min(sel[tab], mods.size() - 1));
        Ui.rrect(g, px, top, pw, bot - top, 4, 0x14FFFFFF);
        g.drawString(font, Component.literal(Ui.fit(font, m.name, pw - 20 - 44)).withStyle(ChatFormatting.BOLD), px + 10, top + 8, Ui.mul(Ui.TEXT), false);
        Ui.textFit(g, font, m.desc, px + 10, top + 21, pw - 20, Ui.MUTED);
        if (m.on != null) {
            boolean on = m.on.getAsBoolean();
            drawSwitch(g, px + pw - 10 - 30, top + 8, on);
            if (click && Ui.in(mx, my, px + pw - 44, top + 4, 36, 20)) m.set.accept(!on);
        }
        int cy0 = top + 38, viewH = bot - cy0 - 4;
        // visible options and their heights
        List<Opt> vis = new ArrayList<>();
        int total = 0;
        for (Opt o : m.opts) if (o.vis.getAsBoolean()) { vis.add(o); total += o.height() + GAP; }
        int maxScroll = Math.max(0, total - viewH);
        if (pendingScroll != 0) scroll = Mth.clamp(scroll - (int) Math.signum(pendingScroll) * 26, 0, maxScroll);
        scroll = Mth.clamp(scroll, 0, maxScroll);
        if (m.opts.isEmpty()) {
            Ui.textFit(g, font, m.on != null ? "Use the switch at the top right." : "", px + 10, cy0 + 4, pw - 20, Ui.MUTED);
        }
        int cy = cy0 - scroll;
        int rx = px + 8, rw = pw - 16;
        for (Opt o : vis) {
            int h = o.height();
            if (cy >= cy0 - 1 && cy + h <= cy0 + viewH + 1) drawOpt(g, o, rx, cy, rw, h, mx, my, click, down, ease);
            cy += h + GAP;
        }
        if (maxScroll > 0) {   // little scroll bar
            int trackH = viewH;
            int th = Math.max(14, trackH * viewH / total);
            int ty = cy0 + (trackH - th) * scroll / maxScroll;
            Ui.rect(g, px + pw - 4, cy0, 2, trackH, 0x22FFFFFF);
            Ui.rect(g, px + pw - 4, ty, 2, th, 0x88FFFFFF);
        }
    }

    private void drawSwitch(GuiGraphics g, int x, int y, boolean on) {
        Ui.rrect(g, x, y, 30, 14, 7, on ? Ui.ACCENT : 0xFF4A505A);
        Ui.rrect(g, x + (on ? 17 : 2), y + 2, 10, 10, 5, 0xFFFFFFFF);
    }

    private void drawOpt(GuiGraphics g, Opt o, int x, int y, int w, int h, int mx, int my, boolean click, boolean down, float ease) {
        boolean hov = Ui.in(mx, my, x, y, w, h);
        if (o.k != K.PREVIEW && o.k != K.TEXT) Ui.rect(g, x, y, w, h, hov ? 0x2AFFFFFF : Ui.SURFACE);
        int ty = y + (h - 8) / 2;
        switch (o.k) {
            case TOGGLE -> {
                Ui.textFit(g, font, o.label, x + 8, ty, w - 8 - 30 - 8 - 6, Ui.TEXT);
                boolean on = o.getB.getAsBoolean();
                drawSwitch(g, x + w - 8 - 30, y + (h - 14) / 2, on);
                if (click && hov) o.setB.accept(!on);
            }
            case SLIDER -> {
                Ui.textFit(g, font, o.label, x + 8, ty, 80, Ui.TEXT);
                int tx = x + 92, tw = w - 92 - 50, ly = y + h / 2 - 2;
                boolean over = Ui.in(mx, my, tx - 4, y, tw + 8, h);
                if (click && over) dragging = o;
                if (dragging == o && down) {
                    double f = Mth.clamp((mx - tx) / (double) tw, 0.0, 1.0);
                    o.setD.accept(o.min + f * (o.max - o.min));
                }
                double v = o.getD.getAsDouble();
                float f = (float) Mth.clamp((v - o.min) / (o.max - o.min), 0.0, 1.0);
                Ui.rect(g, tx, ly, tw, 4, Ui.OFF);
                Ui.rect(g, tx, ly, Math.max(4f, tw * f), 4, Ui.ACCENT);
                Ui.rrect(g, tx + tw * f - 3, y + h / 2f - 6, 6, 12, 3, Ui.TEXT);
                String shown = o.fmt.contains("%d") ? String.format(o.fmt, Math.round(o.fmt.contains("%%") ? v * 100 : v)) : String.format(o.fmt, v);
                shown = Ui.fit(font, shown, 46);
                Ui.text(g, font, shown, x + w - 8 - font.width(shown), ty, Ui.MUTED);
            }
            case CHOICE -> {
                int n = o.choices.length, cg = 3, cw = (w - 6 - (n - 1) * cg) / n;
                for (int i = 0; i < n; i++) {
                    int cx = x + 3 + i * (cw + cg);
                    boolean ch = Ui.in(mx, my, cx, y + 2, cw, h - 4);
                    boolean sel = o.getI.getAsInt() == i;
                    Ui.rrect(g, cx, y + 2, cw, h - 4, 3, sel ? Ui.ACCENT : (ch ? Ui.HOVER : 0x18FFFFFF));
                    Ui.textCenter(g, font, o.choices[i], cx, ty, cw, sel ? Ui.INK : Ui.TEXT);
                    if (click && ch) o.setI.accept(i);
                }
            }
            case SWATCH -> {
                int n = o.colors.length;
                int sx = x + w - 8 - n * 14 - (n - 1) * 4;
                Ui.textFit(g, font, o.label, x + 8, ty, sx - (x + 8) - 6, Ui.TEXT);
                for (int i = 0; i < n; i++) {
                    int bx = sx + i * 18, by = y + (h - 14) / 2;
                    if (i == o.getI.getAsInt()) Ui.rrect(g, bx - 2, by - 2, 18, 18, 4, Ui.TEXT);
                    Ui.rrect(g, bx, by, 14, 14, 3, o.colors[i]);
                    if (click && Ui.in(mx, my, bx - 2, by - 2, 18, 18)) o.setI.accept(i);
                }
            }
            case ACTION -> {
                String l = o.labelS.get();
                Ui.rrect(g, x, y, w, h, 3, hov ? Ui.lerp(Ui.ACCENT, 0xFFB7A3FF, 0.3f) : Ui.ACCENT);
                Ui.textCenter(g, font, l, x, ty, w, Ui.INK);
                if (click && hov) o.act.run();
            }
            case TEXT -> Ui.textFit(g, font, o.labelS.get(), x + 4, ty, w - 8, Ui.MUTED);
        }
    }

    // ---------------------------------------------------------------- wardrobe
    private void drawWardrobe(GuiGraphics g, int mx, int my, boolean click, float ease, int x, int top, int bot) {
        int lx = x + PAD;
        for (int i = 0; i < Cosmetics.CATS.length; i++) {
            int ry = top + i * (ROW + 2);
            boolean hov = Ui.in(mx, my, lx, ry, LIST_W, ROW);
            listHov[i] += ((hov ? 1f : 0f) - listHov[i]) * ease;
            boolean selected = cosCat == i;
            Ui.rect(g, lx, ry, LIST_W, ROW, selected ? Ui.accA(0x44) : Ui.lerp(Ui.SURFACE, Ui.HOVER, listHov[i]));
            if (selected) Ui.rring(g, lx, ry, LIST_W, ROW, 3, Ui.accA(0xAA));
            Ui.textFit(g, font, Cosmetics.CATS[i], lx + 8, ry + (ROW - 8) / 2, LIST_W - 8 - 20, selected ? Ui.TEXT : Ui.lerp(Ui.MUTED, Ui.TEXT, listHov[i]));
            boolean has = Cosmetics.chosen[i] != 0 && (i < 3 || Cosmetics.effectOn);
            if (has) Ui.rrect(g, lx + LIST_W - 14, ry + ROW / 2f - 3, 6, 6, 3, Ui.ACCENT);
            if (click && hov && cosCat != i) { cosCat = i; cosScroll = 0; dragging = null; }
        }
        int ry = top + Cosmetics.CATS.length * (ROW + 2) + 6;
        boolean hOff = Ui.in(mx, my, lx, ry, LIST_W, ROW);
        Ui.rect(g, lx, ry, LIST_W, ROW, hOff ? Ui.HOVER : Ui.SURFACE);
        String off = "Take everything off";
        Ui.textCenter(g, font, off, lx, ry + (ROW - 8) / 2, LIST_W, Ui.TEXT);
        if (click && hOff) { Cosmetics.chosen[0] = 0; Cosmetics.chosen[1] = 0; Cosmetics.chosen[2] = 0; }

        int px = lx + LIST_W + PAD, pw = x + W - PAD - px;
        // try-on card
        int cardW = 112;
        Ui.rrect(g, px, top, cardW, bot - top, 4, 0x14FFFFFF);
        Ui.text(g, font, "Try-on", px + 8, top + 7, Ui.MUTED);
        float bob = (float) Math.sin(System.currentTimeMillis() / 700.0) * 1.5f;
        float u = Math.min(4.2f, (bot - top - 60) / 36f);
        float ax = px + (cardW - 16 * u) / 2f, ay = top + 28 + bob + 3 * u;
        Cosmetics.drawAvatar(g, ax, ay, u, Cosmetics.chosen[0], Cosmetics.chosen[1], Cosmetics.chosen[2]);
        String eq = Cosmetics.chosen[cosCat] == 0 ? "Nothing equipped" : Cosmetics.ITEMS[cosCat][Cosmetics.chosen[cosCat]];
        Ui.textCenter(g, font, eq, px, bot - 14, cardW, Ui.TEXT);

        // a few sparkles around the try-on figure when an effect is on
        if (Cosmetics.effectOn && Cosmetics.chosen[3] > 0) {
            int ec = Cosmetics.effectColor(Cosmetics.chosen[3]);
            long tt = System.currentTimeMillis();
            for (int k = 0; k < 9; k++) {
                double ph = ((tt * 0.0004 * (0.6 + (k % 4) * 0.25)) + k * 0.37) % 1.0;
                float sx = px + 12 + ((k * 53) % 100) / 100f * (cardW - 24);
                float sy = top + 20 + (float) ((1.0 - ph) * (bot - top - 50));
                Ui.rect(g, sx, sy, 2, 2, Ui.mul(ec));
            }
        }

        // item grid (the Effects page gets its switch / placement / amount rows on top)
        int gx = px + cardW + PAD, gw = x + W - PAD - gx;
        Ui.rrect(g, gx, top, gw, bot - top, 4, 0x14FFFFFF);
        Ui.textFit(g, font, Cosmetics.CATS[cosCat], gx + 8, top + 7, gw - 16, Ui.TEXT);
        int gridTop = top + 22;
        if (cosCat == 3 || cosCat == 2) {
            int oy = top + 20;
            for (Opt o : (cosCat == 3 ? effOpts : syncOpts)) {
                drawOpt(g, o, gx + 8, oy, gw - 16, ROW, mx, my, click, wasDownNow, ease);
                oy += ROW + 3;
            }
            gridTop = oy + 2;
        }
        String[] items = Cosmetics.ITEMS[cosCat];
        int cols = cosCat == 1 ? 4 : 3, cg = 5, cw = (gw - 16 - (cols - 1) * cg) / cols, chh = cosCat == 1 ? 80 : 52;
        int rows = (items.length + cols - 1) / cols;
        int visRows = Math.max(1, (bot - gridTop - 2 + cg) / (chh + cg));
        int maxScroll = Math.max(0, rows - visRows);
        if (pendingScroll != 0) cosScroll = Mth.clamp(cosScroll - (int) Math.signum(pendingScroll), 0, maxScroll);
        cosScroll = Mth.clamp(cosScroll, 0, maxScroll);
        for (int i = 0; i < items.length; i++) {
            int row = i / cols - cosScroll;
            if (row < 0 || row >= visRows) continue;
            int cx = gx + 8 + (i % cols) * (cw + cg), cy = gridTop + row * (chh + cg);
            boolean hov = Ui.in(mx, my, cx, cy, cw, chh);
            cellHov[i] += ((hov ? 1f : 0f) - cellHov[i]) * ease;
            boolean on = Cosmetics.chosen[cosCat] == i;
            Ui.rrect(g, cx, cy, cw, chh, 4, on ? Ui.accA(0x55) : Ui.lerp(Ui.SURFACE, Ui.HOVER, cellHov[i]));
            if (on) Ui.rring(g, cx, cy, cw, chh, 4, Ui.ACCENT);
            Cosmetics.icon(g, cosCat, i, cx + 2, cy + 2, cw - 4, chh - 15);
            Ui.textCenter(g, font, items[i], cx, cy + chh - 11, cw, on ? Ui.TEXT : Ui.MUTED);
            if (click && hov) Cosmetics.chosen[cosCat] = i;
        }
        if (maxScroll > 0) {
            int trackH = bot - gridTop - 2;
            int th = Math.max(14, trackH * visRows / rows);
            int ty = gridTop + (trackH - th) * cosScroll / maxScroll;
            Ui.rect(g, gx + gw - 5, gridTop, 2, trackH, 0x22FFFFFF);
            Ui.rect(g, gx + gw - 5, ty, 2, th, 0x88FFFFFF);
        }
    }

    // ---------------------------------------------------------------- misc screen stuff
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        pendingScroll += scrollY;
        return true;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, ((int) (0x38 * openT) << 24));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Config.save();
        Extras.save();
        Cosmetics.save();
        HudPack.save();
        Visuals.save();
        super.onClose();
    }
}
