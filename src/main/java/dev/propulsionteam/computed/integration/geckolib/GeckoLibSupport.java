package dev.propulsionteam.computed.integration.geckolib;

import dev.propulsionteam.computed.content.items.PortableMonitorItem;
import net.minecraft.world.item.Item;
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

    /**
     * Creates the animated portable monitor via reflection. Deliberately no static reference to
     * {@code GeoPortableMonitorItem}: the JVM verifier resolves implement-interfaces like
     * {@code GeoItem} when verifying class references, which would crash mod construction in
     * packs without GeckoLib even behind an {@code isLoaded()} branch.
     */
    public static PortableMonitorItem createPortableMonitor(Item.Properties properties) {
        try {
            return Class.forName("dev.propulsionteam.computed.content.items.GeoPortableMonitorItem")
                    .asSubclass(PortableMonitorItem.class)
                    .getConstructor(Item.Properties.class)
                    .newInstance(properties);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "GeckoLib is loaded but GeoPortableMonitorItem could not be created", e);
        }
    }
}
