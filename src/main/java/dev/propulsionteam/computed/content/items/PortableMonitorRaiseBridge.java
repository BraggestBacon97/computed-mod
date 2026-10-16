package dev.propulsionteam.computed.content.items;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.world.item.ItemStack;

/**
 * Client-driven "raise" state for the portable monitor, decoupled from GeckoLib. The geo item
 * starts a raise (open animation + delayed screen open) through {@link #startRaise}; the
 * animation controller and the hand-pose handler poll {@link #isRaised}. Installed from
 * {@code ComputedClient}, so without installation every call stays inert.
 */
public final class PortableMonitorRaiseBridge {
    private static BooleanSupplier raised = () -> false;
    private static Consumer<ItemStack> starter = stack -> {};

    private PortableMonitorRaiseBridge() {}

    public static void install(BooleanSupplier raisedNow, Consumer<ItemStack> startRaise) {
        raised = raisedNow;
        starter = startRaise;
    }

    /** True while the monitor is being raised or the portable screen is open. */
    public static boolean isRaised() {
        return raised.getAsBoolean();
    }

    /** Begins the client-side raise for a linked monitor stack. */
    public static void startRaise(ItemStack stack) {
        starter.accept(stack);
    }
}
