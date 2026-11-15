package com.chromeboost;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Other performance mods already do some of this work. When one of them is installed the matching module here switches
 * itself off, so nothing is done twice and nothing fights.
 */
public final class Compat {
    private static final String[] ENTITY_CULLING = {"entityculling", "moreculling", "enhancedblockentities"};
    private static final String[] PARTICLES = {"particlecore", "particular", "effectual"};

    private Compat() {}

    private static boolean any(String... ids) {
        FabricLoader l = FabricLoader.getInstance();
        for (String id : ids) if (l.isModLoaded(id)) return true;
        return false;
    }

    /** name of the mod that already culls entities, or null */
    public static String entityCullingBy() {
        FabricLoader l = FabricLoader.getInstance();
        for (String id : ENTITY_CULLING) if (l.isModLoaded(id) && !id.equals("enhancedblockentities")) return id;
        return null;
    }

    public static String particlesBy() {
        FabricLoader l = FabricLoader.getInstance();
        for (String id : PARTICLES) if (l.isModLoaded(id)) return id;
        return null;
    }

    public static boolean entityCullActive() {
        return Settings.get().entityCull && entityCullingBy() == null;
    }

    public static boolean particleCullActive() {
        return Settings.get().particleCull && particlesBy() == null;
    }

    /** the big engine mods are never in the way (this mod does not touch chunk meshing or lighting), but the menu says so */
    public static boolean engineMod() {
        return any("sodium", "embeddium", "lithium", "ferritecore", "modernfix", "c2me", "immediatelyfast");
    }
}
