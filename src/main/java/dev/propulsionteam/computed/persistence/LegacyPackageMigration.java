package dev.propulsionteam.computed.persistence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * One-time migration of legacy game-directory packages ({@code <gameDir>/computed/{graphs,nodes}},
 * written by client-side import/export before packages moved into the world save) into one
 * computer's package folders ({@code <world>/computed/packages/<id>/}). Existing world files are
 * never overwritten. A marker file records which source was imported so that a marker from a
 * different (for example stale) source triggers a fresh, still idempotent copy, and an empty or
 * missing source leaves no marker so a later open retries. Pure nio — unit tests never load
 * Minecraft classes.
 */
public final class LegacyPackageMigration {
    private static final String MARKER = ".legacy-import-done";
    private static final String[] KIND_FOLDERS = {"graphs", "nodes"};

    private LegacyPackageMigration() {}

    /**
     * Copies missing files from {@code <serverDirectory>/computed/{graphs,nodes}} into the given
     * computer's package directory.
     *
     * @return {@code -1} when there is nothing to migrate (empty source) or this computer already
     *     migrated from this source, otherwise the number of copied files
     */
    public static int migrateIfNeeded(Path serverDirectory, Path worldDirectory, int computerId)
            throws IOException {
        Path sourceRoot = serverDirectory.resolve("computed");
        Path targetRoot = PackageTransferService.computerDirectory(worldDirectory, computerId);
        Path marker = targetRoot.resolve(MARKER);
        if (markerNames(marker, sourceRoot)) {
            return -1;
        }
        if (!hasPackages(sourceRoot)) {
            return -1;
        }
        int copied = 0;
        for (String folder : KIND_FOLDERS) {
            copied += copyMissing(sourceRoot.resolve(folder), targetRoot.resolve(folder));
        }
        Files.createDirectories(targetRoot);
        Files.writeString(marker, "migrated from " + sourceRoot + "\n", StandardCharsets.UTF_8);
        return copied;
    }

    /** True when the marker exists and records this exact source (absolute path comparison). */
    private static boolean markerNames(Path marker, Path sourceRoot) {
        if (!Files.isRegularFile(marker)) {
            return false;
        }
        try {
            return Files.readString(marker, StandardCharsets.UTF_8).contains(sourceRoot.toString());
        } catch (IOException exception) {
            return false;
        }
    }

    /** True when the legacy source holds at least one graph or node package. */
    private static boolean hasPackages(Path sourceRoot) throws IOException {
        for (String folder : KIND_FOLDERS) {
            Path source = sourceRoot.resolve(folder);
            if (!Files.isDirectory(source)) {
                continue;
            }
            try (var paths = Files.walk(source)) {
                if (paths.filter(Files::isRegularFile).findFirst().isPresent()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int copyMissing(Path source, Path target) throws IOException {
        if (!Files.isDirectory(source)) {
            return 0;
        }
        int copied = 0;
        try (var paths = Files.walk(source)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                Path destination = target.resolve(source.relativize(path)).normalize();
                if (!destination.startsWith(target) || Files.exists(destination)) {
                    continue;
                }
                Files.createDirectories(destination.getParent());
                Files.copy(path, destination);
                copied++;
            }
        }
        return copied;
    }
}
