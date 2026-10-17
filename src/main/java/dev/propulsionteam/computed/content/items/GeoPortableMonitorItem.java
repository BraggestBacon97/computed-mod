package dev.propulsionteam.computed.content.items;

import java.util.function.Consumer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;
import dev.propulsionteam.computed.client.PortableMonitorGeoRenderer;

/**
 * GeckoLib variant of the portable monitor: same link logic plus the keyframe-animated item model.
 * Registered in place of {@link PortableMonitorItem} when GeckoLib is loaded (see
 * {@code GeckoLibSupport}). Opening and closing the screen is instant — the model has no
 * open/close animation; the controller exists only so GeckoLib has one, and always stops.
 */
public class GeoPortableMonitorItem extends PortableMonitorItem implements GeoItem {
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    public GeoPortableMonitorItem(Properties properties) {
        super(properties);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, state -> PlayState.STOP));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        // Separate anonymous class: the client-only renderer resolves only when this runs.
        consumer.accept(new GeoRenderProvider() {
            @Override
            public BlockEntityWithoutLevelRenderer getGeoItemRenderer() {
                return PortableMonitorGeoRenderer.instance();
            }
        });
    }
}
