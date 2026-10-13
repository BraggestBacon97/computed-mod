package dev.propulsionteam.computed.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LegacyPackageMigrationTest {
    @TempDir Path temporaryDirectory;

    private Path serverDirectory;
    private Path worldDirectory;

    private void prepare() throws IOException {
        serverDirectory = Files.createDirectories(temporaryDirectory.resolve("server"));
        worldDirectory = Files.createDirectories(temporaryDirectory.resolve("saves/world"));
    }

    private Path packageRoot(int computerId) {
        return worldDirectory.resolve("computed/packages/" + computerId);
    }

    @Test
    void copiesLegacyPackagesIntoTheComputersFoldersOnce() throws IOException {
        prepare();
        Path legacy = Files.createDirectories(serverDirectory.resolve("computed/nodes"));
        Files.writeString(legacy.resolve("helper.lua"), "print('hi')");

        assertEquals(1, LegacyPackageMigration.migrateIfNeeded(serverDirectory, worldDirectory, 3));

        Path migrated = packageRoot(3).resolve("nodes/helper.lua");
        assertTrue(Files.isRegularFile(migrated));
        assertEquals("print('hi')", Files.readString(migrated));
        assertEquals(-1, LegacyPackageMigration.migrateIfNeeded(serverDirectory, worldDirectory, 3));
    }

    @Test
    void migratesEveryComputerIdIndependently() throws IOException {
        prepare();
        Path legacy = Files.createDirectories(serverDirectory.resolve("computed/graphs"));
        Files.writeString(legacy.resolve("mission.lua"), "-- mission");

        assertEquals(1, LegacyPackageMigration.migrateIfNeeded(serverDirectory, worldDirectory, 3));
        assertEquals(1, LegacyPackageMigration.migrateIfNeeded(serverDirectory, worldDirectory, 4));

        assertEquals("-- mission", Files.readString(packageRoot(3).resolve("graphs/mission.lua")));
        assertEquals("-- mission", Files.readString(packageRoot(4).resolve("graphs/mission.lua")));
        assertTrue(Files.isRegularFile(packageRoot(3).resolve(".legacy-import-done")));
        assertTrue(Files.isRegularFile(packageRoot(4).resolve(".legacy-import-done")));
    }

    @Test
    void neverOverwritesExistingWorldFiles() throws IOException {
        prepare();
        Files.createDirectories(serverDirectory.resolve("computed/graphs"));
        Files.writeString(serverDirectory.resolve("computed/graphs/mission.lua"), "legacy");
        Path existing = Files.createDirectories(packageRoot(3).resolve("graphs"));
        Files.writeString(existing.resolve("mission.lua"), "current");

        assertEquals(0, LegacyPackageMigration.migrateIfNeeded(serverDirectory, worldDirectory, 3));

        assertEquals("current", Files.readString(packageRoot(3).resolve("graphs/mission.lua")));
    }

    @Test
    void doesNotMarkAnEmptyLegacySourceAsDone() throws IOException {
        prepare();

        assertEquals(-1, LegacyPackageMigration.migrateIfNeeded(serverDirectory, worldDirectory, 3));
        assertFalse(Files.exists(packageRoot(3).resolve(".legacy-import-done")));
        assertEquals(-1, LegacyPackageMigration.migrateIfNeeded(serverDirectory, worldDirectory, 3));
    }

    @Test
    void remigratesWhenTheMarkerNamesADifferentSource() throws IOException {
        prepare();
        Path legacy = Files.createDirectories(serverDirectory.resolve("computed/nodes"));
        Files.writeString(legacy.resolve("helper.lua"), "-- helper");
        Path root = packageRoot(3);
        Files.createDirectories(root);
        Files.writeString(root.resolve(".legacy-import-done"), "migrated from /stale/place/computed\n");

        assertEquals(1, LegacyPackageMigration.migrateIfNeeded(serverDirectory, worldDirectory, 3));

        assertEquals("-- helper", Files.readString(root.resolve("nodes/helper.lua")));
        assertTrue(Files.readString(root.resolve(".legacy-import-done")).contains(
                serverDirectory.resolve("computed").toString()));
        assertEquals(-1, LegacyPackageMigration.migrateIfNeeded(serverDirectory, worldDirectory, 3));
    }

    @Test
    void ignoresFoldersOtherThanGraphsAndNodes() throws IOException {
        prepare();
        Path other = Files.createDirectories(serverDirectory.resolve("computed/scripts"));
        Files.writeString(other.resolve("secret.txt"), "nope");

        assertEquals(-1, LegacyPackageMigration.migrateIfNeeded(serverDirectory, worldDirectory, 3));

        assertFalse(Files.exists(packageRoot(3).resolve("scripts/secret.txt")));
    }

    @Test
    void copiesNestedFolders() throws IOException {
        prepare();
        Path nested = Files.createDirectories(serverDirectory.resolve("computed/nodes/packs"));
        Files.writeString(nested.resolve("deep.lua"), "-- deep");

        assertEquals(1, LegacyPackageMigration.migrateIfNeeded(serverDirectory, worldDirectory, 3));

        assertEquals("-- deep", Files.readString(packageRoot(3).resolve("nodes/packs/deep.lua")));
    }

    @Test
    void writesMarkerAsUtf8() throws IOException {
        prepare();
        Files.createDirectories(serverDirectory.resolve("computed/nodes"));
        Files.writeString(serverDirectory.resolve("computed/nodes/x.lua"), "-- x");
        LegacyPackageMigration.migrateIfNeeded(serverDirectory, worldDirectory, 7);

        String marker =
                Files.readString(packageRoot(7).resolve(".legacy-import-done"), StandardCharsets.UTF_8);
        assertTrue(marker.contains("computed"));
    }
}
