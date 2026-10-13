package dev.propulsionteam.computed;

import java.util.List;
import net.minecraft.core.BlockPos;

/**
 * Client-side hooks for the portable monitor (installed by {@code ComputedClient}): display frame
 * delivery and opening the windowed screen. No-ops on dedicated servers.
 */
public final class PortableDisplayBridge {
    private static FrameConsumer frameConsumer = (pos, bytes, name) -> {};
    private static OpenConsumer screenOpener = (sources, active) -> {};

    /** One display frame pushed by the server: position, encoded widgets, computer name. */
    @FunctionalInterface
    public interface FrameConsumer {
        void accept(BlockPos pos, byte[] bytes, String computerName);
    }

    /** Opening request: ordered channel positions plus the initially selected channel. */
    @FunctionalInterface
    public interface OpenConsumer {
        void open(List<BlockPos> sources, int activeIndex);
    }

    private PortableDisplayBridge() {}

    public static void install(FrameConsumer frames, OpenConsumer opener) {
        frameConsumer = frames == null ? (pos, bytes, name) -> {} : frames;
        screenOpener = opener == null ? (sources, active) -> {} : opener;
    }

    public static void deliver(BlockPos computerPos, byte[] drawListBytes, String computerName) {
        frameConsumer.accept(computerPos, drawListBytes, computerName);
    }

    /** Opens the windowed monitor screen for the given channels (client only). */
    public static void openScreen(List<BlockPos> sources, int activeIndex) {
        screenOpener.open(sources, activeIndex);
    }
}
