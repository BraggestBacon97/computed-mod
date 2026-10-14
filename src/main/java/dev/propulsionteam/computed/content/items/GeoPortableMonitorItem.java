package dev.propulsionteam.computed.content.items;

import java.util.function.Consumer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;
import dev.propulsionteam.computed.client.PortableMonitorGeoRenderer;

/**
 * GeckoLib variant of the portable monitor: same link logic plus a keyframe-animated item model.
 * Registered in place of {@link PortableMonitorItem} when GeckoLib is loaded (see
 * {@code GeckoLibSupport}); a non-sneak right-click triggers the {@code unfold} animation on the
 * {@code use} controller.
 */
public class GeoPortableMonitorItem extends PortableMonitorItem implements GeoItem {
    private static final RawAnimation UNFOLD =
            RawAnimation.begin().thenPlay("animation.portable_monitor.unfold");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    public GeoPortableMonitorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        InteractionResultHolder<ItemStack> result = super.use(level, player, hand);
        if (level instanceof ServerLevel serverLevel && !player.isSecondaryUseActive()) {
            ItemStack stack = player.getItemInHand(hand);
            triggerAnim(player, GeoItem.getOrAssignId(stack, serverLevel), "use", "unfold");
        }
        return result;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "use", 2, state -> PlayState.STOP)
                .triggerableAnim("unfold", UNFOLD));
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
