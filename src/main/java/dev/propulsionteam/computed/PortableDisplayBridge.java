package dev.propulsionteam.computed;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;

/**
 * Client-side hooks for the portable monitor (installed by {@code ComputedClient}): display frame
 * delivery and opening the windowed screen. No-ops on dedicated servers.
 */
public final class PortableDisplayBridge {
    private static BiConsumer<BlockPos, byte[]> frameConsumer = (pos, bytes) -> {};
    private static Consumer<BlockPos> screenOpener = pos -> {};

    private PortableDisplayBridge() {}

    public static void install(BiConsumer<BlockPos, byte[]> frames, Consumer<BlockPos> opener) {
        frameConsumer = frames == null ? (pos, bytes) -> {} : frames;
        screenOpener = opener == null ? pos -> {} : opener;
    }

    public static void deliver(BlockPos computerPos, byte[] drawListBytes) {
        frameConsumer.accept(computerPos, drawListBytes);
    }

    /** Opens the windowed monitor screen for the given computer (client only). */
    public static void openScreen(BlockPos computerPos) {
        screenOpener.accept(computerPos);
    }
}
