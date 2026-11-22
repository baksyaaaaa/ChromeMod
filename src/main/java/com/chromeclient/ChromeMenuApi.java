package com.chromeclient;

import java.util.List;
import java.util.Map;

/** What the Chrome Client menu pages inside the PolyPlus / OneConfig menu use (reached by reflection from that mod). */
public final class ChromeMenuApi {
    private ChromeMenuApi() {}

    public static List<Map<String, Object>> modules() {
        return ClickGui.describe(ChromeClientMod.hudInstance);
    }

    public static void save() {
        Config.save();
        Extras.save();
        Cosmetics.save();
        HudPack.save();
        Visuals.save();
    }
}
