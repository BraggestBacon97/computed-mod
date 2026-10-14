package dev.propulsionteam.computed.integration.geckolib;

import net.neoforged.fml.ModList;

/**
 * Presence check for GeckoLib. All GeckoLib symbols stay out of classes that load without it, so
 * this check is the only safe entry point.
 */
public final class GeckoLibSupport {
    private static Boolean present;

    private GeckoLibSupport() {}

    public static boolean isLoaded() {
        Boolean cached = present;
        if (cached != null) {
            return cached;
        }
        boolean loaded = ModList.get().isLoaded("geckolib");
        present = loaded;
        return loaded;
    }
}
