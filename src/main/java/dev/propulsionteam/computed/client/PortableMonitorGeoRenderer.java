package dev.propulsionteam.computed.client;

import dev.propulsionteam.computed.Computed;
import dev.propulsionteam.computed.content.items.GeoPortableMonitorItem;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/**
 * Item renderer for {@link GeoPortableMonitorItem}. Only ever loaded when GeckoLib is present (the
 * item hands it out from {@code createGeoRenderer}); model, texture and animation live at the
 * conventional {@code geo/item}, {@code textures/item} and {@code animations/item} paths.
 */
public final class PortableMonitorGeoRenderer extends GeoItemRenderer<GeoPortableMonitorItem> {
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(
            Computed.MODID, "geo/item/portable_monitor.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            Computed.MODID, "textures/item/portable_monitor_geo.png");
    private static final ResourceLocation ANIMATIONS = ResourceLocation.fromNamespaceAndPath(
            Computed.MODID, "animations/item/portable_monitor.animation.json");

    private static PortableMonitorGeoRenderer instance;

    /** Lazily created shared renderer; never call before the client is up. */
    public static BlockEntityWithoutLevelRenderer instance() {
        PortableMonitorGeoRenderer renderer = instance;
        if (renderer == null) {
            instance = renderer = new PortableMonitorGeoRenderer();
        }
        return renderer;
    }

    private PortableMonitorGeoRenderer() {
        super(new Model());
    }

    @Override
    public ResourceLocation getTextureLocation(GeoPortableMonitorItem animatable) {
        return TEXTURE;
    }

    private static final class Model extends GeoModel<GeoPortableMonitorItem> {
        @Override
        public ResourceLocation getModelResource(GeoPortableMonitorItem animatable) {
            return MODEL;
        }

        @Override
        public ResourceLocation getTextureResource(GeoPortableMonitorItem animatable) {
            return TEXTURE;
        }

        @Override
        public ResourceLocation getAnimationResource(GeoPortableMonitorItem animatable) {
            return ANIMATIONS;
        }
    }
}
