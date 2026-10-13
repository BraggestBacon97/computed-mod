package dev.propulsionteam.computed.lua.fs;

import dev.propulsionteam.computed.content.blocks.ComputerBlockEntity;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Minecraft-side root resolution for {@code computed:fs}. Kept separate so unit tests never load
 * block-entity or server classes; only referenced when a real computer host ticks.
 */
final class ComputerFsRoots {
    private ComputerFsRoots() {}

    static void attach(ComputerFsService service, UUID computerId, Object host) {
        if (!(host instanceof ComputerBlockEntity computer)) return;
        if (!(computer.getLevel() instanceof ServerLevel serverLevel)) return;
        MinecraftServer server = serverLevel.getServer();
        if (server == null) return;
        int numberId = computer.getOrCreateComputerId();
        if (numberId < 0) return;
        if (service.attachedNumericId(computerId) == numberId) return;
        Path worldRoot = server.getWorldPath(LevelResource.ROOT);
        Path root = worldRoot.resolve("computed").resolve("computer").resolve(Integer.toString(numberId));
        migrateLegacyRoot(worldRoot.resolve("computed").resolve("computers")
                .resolve(computerId.toString()), root);
        service.attachComputer(computerId, numberId, root);
        writeIdentityFile(root, computerId, numberId, serverLevel, computer);
    }

    /** Moves a pre-numeric {@code computed/computers/<uuid>} tree to {@code computed/computer/<id>}. */
    private static void migrateLegacyRoot(Path legacyRoot, Path root) {
        if (!Files.isDirectory(legacyRoot) || Files.exists(root)) return;
        try {
            if (root.getParent() != null) {
                Files.createDirectories(root.getParent());
            }
            Files.move(legacyRoot, root);
        } catch (IOException ignored) {
            // Migration is best-effort; attach then recreates an empty numeric root.
        }
    }

    private static void writeIdentityFile(
            Path root, UUID computerId, int numberId, ServerLevel level, ComputerBlockEntity computer) {
        String content = "id=" + numberId + "\n"
                + "uuid=" + computerId + "\n"
                + "dimension=" + level.dimension().location() + "\n"
                + "position=" + computer.getBlockPos().toShortString() + "\n";
        try {
            Files.writeString(root.resolve("computer.txt"), content, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // Identity file is a convenience; the filesystem still works without it.
        }
    }
}
