package dev.propulsionteam.computed;

import java.util.UUID;
import net.minecraft.core.BlockPos;

/** Set from the physical-client entrypoint so common networking never references client screens. */
public final class WidgetTextInputBridge {
    @FunctionalInterface
    public interface Opener {
        void open(BlockPos originPos, UUID widgetId, String initialValue);
    }

    private static Opener opener;

    private WidgetTextInputBridge() {}

    public static void install(Opener clientOpener) {
        opener = clientOpener;
    }

    public static void open(BlockPos originPos, UUID widgetId, String initialValue) {
        if (opener != null) {
            opener.open(originPos, widgetId, initialValue == null ? "" : initialValue);
        }
    }
}
