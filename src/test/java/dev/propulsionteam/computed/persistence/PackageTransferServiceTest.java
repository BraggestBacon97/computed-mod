package dev.propulsionteam.computed.persistence;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.propulsionteam.computed.persistence.ComputedPackageStorage.Kind;
import dev.propulsionteam.computed.persistence.PackageTransferService.WriteMode;
import dev.propulsionteam.computed.persistence.PackageTransferService.WriteStatus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PackageTransferServiceTest {
    @TempDir Path temporaryDirectory;

    private PackageTransferService service() {
        return new PackageTransferService(temporaryDirectory);
    }

    @Test
    void listsNothingOnAFreshWorld() throws IOException {
        assertTrue(service().list(Kind.NODES, "").isEmpty());
    }

    @Test
    void writesPackagesIntoTheWorldTree() throws IOException {
        byte[] content = "print('hi')".getBytes(StandardCharsets.UTF_8);

        PackageTransferService.WriteResult result =
                service().write(Kind.NODES, "helper", content, WriteMode.FAIL_IF_EXISTS);

        assertEquals(WriteStatus.OK, result.status());
        assertEquals("helper.lua", result.fileName());
        assertArrayEquals(content, service().read(Kind.NODES, "helper"));
        assertTrue(Files.isRegularFile(temporaryDirectory.resolve("nodes/helper.lua")));
    }

    @Test
    void reportsExistingFilesWithoutTouchingThem() throws IOException {
        PackageTransferService service = service();
        service.write(Kind.GRAPHS, "mission", new byte[] {1, 2, 3}, WriteMode.FAIL_IF_EXISTS);

        PackageTransferService.WriteResult second = service.write(
                Kind.GRAPHS, "mission", new byte[] {9}, WriteMode.FAIL_IF_EXISTS);

        assertEquals(WriteStatus.EXISTS, second.status());
        assertArrayEquals(new byte[] {1, 2, 3}, service.read(Kind.GRAPHS, "mission"));
    }

    @Test
    void overwritesOnlyWhenAllowed() {
        PackageTransferService service = service();
        service.write(Kind.GRAPHS, "mission", new byte[] {1}, WriteMode.FAIL_IF_EXISTS);

        PackageTransferService.WriteResult result = service.write(
                Kind.GRAPHS, "mission", new byte[] {4, 5}, WriteMode.OVERWRITE);

        assertEquals(WriteStatus.OK, result.status());
        assertArrayEquals(new byte[] {4, 5}, readUnchecked(service, Kind.GRAPHS, "mission"));
    }

    @Test
    void savesWithTheNextAvailableName() {
        PackageTransferService service = service();
        service.write(Kind.NODES, "helper", new byte[] {1}, WriteMode.FAIL_IF_EXISTS);

        PackageTransferService.WriteResult result = service.write(
                Kind.NODES, "helper", new byte[] {2}, WriteMode.NEXT_AVAILABLE);

        assertEquals(WriteStatus.OK, result.status());
        assertEquals("helper (1).lua", result.fileName());
        assertArrayEquals(new byte[] {1}, readUnchecked(service, Kind.NODES, "helper"));
        assertArrayEquals(new byte[] {2}, readUnchecked(service, Kind.NODES, "helper (1)"));
    }

    @Test
    void rejectsOversizedPackages() {
        byte[] huge = new byte[PackageTransferService.MAX_PACKAGE_BYTES + 1];

        PackageTransferService.WriteResult result =
                service().write(Kind.GRAPHS, "huge", huge, WriteMode.OVERWRITE);

        assertEquals(WriteStatus.ERROR, result.status());
        assertTrue(result.error().contains("too large"));
    }

    @Test
    void readingAMissingPackageFails() {
        assertThrows(IOException.class, () -> service().read(Kind.GRAPHS, "missing"));
    }

    @Test
    void listsFoldersBeforeFiles() throws IOException {
        Files.createDirectories(temporaryDirectory.resolve("nodes/packs"));
        Files.writeString(temporaryDirectory.resolve("nodes/base.lua"), "-- base");

        List<PackageTransferService.Entry> entries = service().list(Kind.NODES, "");

        assertEquals(2, entries.size());
        assertEquals("packs", entries.get(0).name());
        assertTrue(entries.get(0).directory());
        assertEquals("base.lua", entries.get(1).name());
        assertFalse(entries.get(1).directory());
    }

    @Test
    void scopesPackageDirectoryToComputerId() {
        Path world = temporaryDirectory.resolve("saves/world");

        assertEquals(world.resolve("computed/packages/3"), PackageTransferService.computerDirectory(world, 3));
        assertEquals(world.resolve("computed/packages/17"), PackageTransferService.computerDirectory(world, 17));
    }

    @Test
    void roundTripsKindAndModeIds() {
        for (Kind kind : Kind.values()) {
            assertEquals(kind, PackageTransferService.kind(PackageTransferService.id(kind)));
        }
        for (WriteMode mode : WriteMode.values()) {
            assertEquals(mode, PackageTransferService.mode(PackageTransferService.id(mode)));
        }
        assertThrows(IllegalArgumentException.class, () -> PackageTransferService.kind(-1));
        assertThrows(IllegalArgumentException.class, () -> PackageTransferService.kind(99));
        assertThrows(IllegalArgumentException.class, () -> PackageTransferService.mode(7));
    }

    private static byte[] readUnchecked(PackageTransferService service, Kind kind, String path) {
        try {
            return service.read(kind, path);
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }
}
