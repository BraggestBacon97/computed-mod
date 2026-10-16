package dev.propulsionteam.computed.content.items;

import java.util.function.Consumer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;
import dev.propulsionteam.computed.client.PortableMonitorGeoRenderer;

/**
 * GeckoLib variant of the portable monitor: same link logic plus a keyframe-animated item model.
 * Registered in place of {@link PortableMonitorItem} when GeckoLib is loaded (see
 * {@code GeckoLibSupport}). A non-sneak right-click starts the client-side raise (open
 * animation, screen opens after {@code PortableMonitorRaise.RAISE_TICKS}); closing the screen
 * plays the close animation and lowers the item again. The animation is driven purely
 * client-side through {@link PortableMonitorRaiseBridge} — no server sync, no latency.
 */
public class GeoPortableMonitorItem extends PortableMonitorItem implements GeoItem {
    private static final RawAnimation OPEN =
            RawAnimation.begin().thenPlay("animation.portable_monitor.open");
    private static final RawAnimation CLOSE =
            RawAnimation.begin().thenPlay("animation.portable_monitor.close");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    public GeoPortableMonitorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive()) {
            return super.use(level, player, hand);
        }
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            if (PortableMonitorItem.readLinks(stack).isEmpty()) {
                player.displayClientMessage(
                        Component.translatable("item.computed.portable_monitor.hint"), true);
            } else {
                PortableMonitorRaiseBridge.startRaise(stack);
            }
        }
        // CONSUME instead of sidedSuccess: SUCCESS makes the client play the vanilla swing, which
        // shoves the held item (and the arm) around right while the raise animation runs.
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        boolean[] raisedOnce = {false};
        // Transition 0: the arm in PortableMonitorRenderHandler has no blend, so a blend here
        // would make the item lag ~4 ticks behind the arm and the screen opening.
        controllers.add(new AnimationController<>(this, "main", 0, state -> {
            // The raise state is global; only animate the first-person hand render, not the
            // inventory/hotbar icon, dropped item or third-person models.
            ItemDisplayContext context = state.getData(DataTickets.ITEM_RENDER_PERSPECTIVE);
            if (context == null || !context.firstPerson()) {
                return PlayState.STOP;
            }
            if (PortableMonitorRaiseBridge.isRaised()) {
                raisedOnce[0] = true;
                return state.setAndContinue(OPEN);
            }
            if (raisedOnce[0]) {
                return state.setAndContinue(CLOSE);
            }
            return PlayState.STOP;
        }));
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
