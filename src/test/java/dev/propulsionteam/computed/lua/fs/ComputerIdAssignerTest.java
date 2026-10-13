package dev.propulsionteam.computed.lua.fs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ComputerIdAssignerTest {
    @TempDir Path temporaryDirectory;

    @Test
    void allocatesSequentialIdsStartingAtZero() {
        assertEquals(0, ComputerIdAssigner.allocate(temporaryDirectory));
        assertEquals(1, ComputerIdAssigner.allocate(temporaryDirectory));
        assertEquals(2, ComputerIdAssigner.allocate(temporaryDirectory));
    }

    @Test
    void writesComputerCraftStyleIdsFile() throws IOException {
        ComputerIdAssigner.allocate(temporaryDirectory);

        String content = Files.readString(temporaryDirectory.resolve("ids.json"), StandardCharsets.UTF_8);
        assertEquals("{\n  \"computer\": 0\n}", content);
        assertFalse(Files.exists(temporaryDirectory.resolve("ids.json.new")));
    }

    @Test
    void continuesFromExistingIdsFile() throws IOException {
        Files.writeString(temporaryDirectory.resolve("ids.json"), "{\"computer\": 6}");

        assertEquals(7, ComputerIdAssigner.allocate(temporaryDirectory));
        assertEquals(8, ComputerIdAssigner.allocate(temporaryDirectory));
    }

    @Test
    void neverReusesAnExistingComputerFolder() throws IOException {
        Files.createDirectories(temporaryDirectory.resolve("computer").resolve("9"));

        assertEquals(10, ComputerIdAssigner.allocate(temporaryDirectory));
    }

    @Test
    void recoversFromCorruptIdsFileUsingFolderScan() throws IOException {
        Files.writeString(temporaryDirectory.resolve("ids.json"), "not json at all");
        Files.createDirectories(temporaryDirectory.resolve("computer").resolve("3"));

        assertEquals(4, ComputerIdAssigner.allocate(temporaryDirectory));
    }

    @Test
    void ignoresNonNumericComputerFolders() throws IOException {
        Files.createDirectories(temporaryDirectory.resolve("computer").resolve("backups"));

        assertEquals(0, ComputerIdAssigner.allocate(temporaryDirectory));
        assertTrue(Files.isDirectory(temporaryDirectory.resolve("computer")));
    }

    @Test
    void keepsHigherFileValueWhenFoldersAreAhead() throws IOException {
        Files.writeString(temporaryDirectory.resolve("ids.json"), "{\n  \"computer\": 12\n}");
        Files.createDirectories(temporaryDirectory.resolve("computer").resolve("5"));

        assertEquals(13, ComputerIdAssigner.allocate(temporaryDirectory));
    }

    @Test
    void reserveAdvancesTheCounterPastManualIds() throws IOException {
        assertTrue(ComputerIdAssigner.reserve(temporaryDirectory, 5));

        assertEquals(
                "{\n  \"computer\": 5\n}",
                Files.readString(temporaryDirectory.resolve("ids.json"), StandardCharsets.UTF_8));
        assertEquals(6, ComputerIdAssigner.allocate(temporaryDirectory));
    }

    @Test
    void reserveKeepsHigherCountersAndTreatsLowerOnesAsCovered() throws IOException {
        Files.writeString(temporaryDirectory.resolve("ids.json"), "{\"computer\": 9}");

        assertTrue(ComputerIdAssigner.reserve(temporaryDirectory, 4));
        assertTrue(ComputerIdAssigner.reserve(temporaryDirectory, 9));
        assertEquals(10, ComputerIdAssigner.allocate(temporaryDirectory));
    }

    @Test
    void reserveRejectsNegativeIds() {
        assertFalse(ComputerIdAssigner.reserve(temporaryDirectory, -1));
    }
}
