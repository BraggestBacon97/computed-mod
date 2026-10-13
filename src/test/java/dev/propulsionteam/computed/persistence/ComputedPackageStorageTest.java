package dev.propulsionteam.computed.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ComputedPackageStorageTest {
    @TempDir Path temporaryDirectory;

    @Test
    void listsComputedFilesAndGeneratesSafeSuffixes() throws Exception {
        ComputedPackageStorage storage = new ComputedPackageStorage(temporaryDirectory);
        Path graphs = storage.directory(ComputedPackageStorage.Kind.GRAPHS);
        Files.writeString(graphs.resolve("z.computed"), "z");
        Files.writeString(graphs.resolve("A.computed"), "a");
        Files.writeString(graphs.resolve("ignore.txt"), "x");

        assertEquals("A.computed", storage.list(ComputedPackageStorage.Kind.GRAPHS).getFirst().getFileName().toString());
        assertEquals("z.computed", storage.nextAvailable(ComputedPackageStorage.Kind.GRAPHS, "z").getFileName().toString().replace(" (1)", ""));
        assertEquals("z (1).computed", storage.nextAvailable(ComputedPackageStorage.Kind.GRAPHS, "z").getFileName().toString());
        assertEquals("bad_name", ComputedPackageStorage.sanitizeName("bad/name"));
        assertThrows(IllegalArgumentException.class, () -> ComputedPackageStorage.sanitizeName(".."));
    }

    @Test
    void usesPlainLuaFilesForNodeStorage() throws Exception {
        ComputedPackageStorage storage = new ComputedPackageStorage(temporaryDirectory);
        Path nodes = storage.directory(ComputedPackageStorage.Kind.NODES);
        Files.writeString(nodes.resolve("Node.lua"), "return node");
        Files.writeString(nodes.resolve("legacy.computed"), "legacy");

        assertEquals(
                "Node.lua",
                storage.list(ComputedPackageStorage.Kind.NODES).getFirst().getFileName().toString());
        assertEquals(
                "Node (1).lua",
                storage.nextAvailable(ComputedPackageStorage.Kind.NODES, "Node").getFileName().toString());
        assertEquals(
                "Fresh.lua",
                storage.target(ComputedPackageStorage.Kind.NODES, "Fresh").getFileName().toString());
    }

    @Test
    void findsPackagesInsideSubfolders() throws Exception {
        ComputedPackageStorage storage = new ComputedPackageStorage(temporaryDirectory);
        Path graphs = storage.directory(ComputedPackageStorage.Kind.GRAPHS);
        Files.createDirectories(graphs.resolve("blimp"));
        Files.writeString(graphs.resolve("blimp/pilot.computed"), "p");
        Files.createDirectories(graphs.resolve("plane-graphs"));
        Files.writeString(graphs.resolve("plane-graphs/engine.computed"), "e");
        Files.writeString(graphs.resolve("root.computed"), "r");
        Files.createDirectories(graphs.resolve("tmp"));

        List<Path> all = storage.list(ComputedPackageStorage.Kind.GRAPHS);
        assertEquals(3, all.size());
        assertTrue(all.stream().noneMatch(path -> path.getFileName().toString().equals("tmp")));

        List<ComputedPackageStorage.Entry> rootEntries =
                storage.listEntries(ComputedPackageStorage.Kind.GRAPHS, "");
        assertEquals(List.of("blimp", "plane-graphs", "tmp"),
                rootEntries.stream().filter(ComputedPackageStorage.Entry::directory)
                        .map(ComputedPackageStorage.Entry::name).toList());
        assertEquals(List.of("root.computed"),
                rootEntries.stream().filter(entry -> !entry.directory())
                        .map(ComputedPackageStorage.Entry::name).toList());

        List<ComputedPackageStorage.Entry> blimpEntries =
                storage.listEntries(ComputedPackageStorage.Kind.GRAPHS, "blimp");
        assertEquals(1, blimpEntries.size());
        assertEquals("pilot.computed", blimpEntries.getFirst().name());
        assertTrue(blimpEntries.getFirst().matches(ComputedPackageStorage.Kind.GRAPHS));
    }

    @Test
    void resolvesTargetsInsideSubfolders() throws Exception {
        ComputedPackageStorage storage = new ComputedPackageStorage(temporaryDirectory);

        Path nested = storage.target(ComputedPackageStorage.Kind.GRAPHS, "planes/racers/sprint");
        assertEquals("sprint.computed", nested.getFileName().toString());
        assertTrue(Files.isDirectory(nested.getParent()));
        Files.writeString(nested, "x");

        Path suffixed = storage.nextAvailable(ComputedPackageStorage.Kind.GRAPHS, "planes/racers/sprint");
        assertEquals("sprint (1).computed", suffixed.getFileName().toString());

        Path created = storage.createFolder(ComputedPackageStorage.Kind.NODES, "machines/engines");
        assertTrue(Files.isDirectory(created));
        assertEquals("engines", created.getFileName().toString());
        assertTrue(storage.listEntries(ComputedPackageStorage.Kind.NODES, "machines")
                .stream().anyMatch(entry -> entry.directory() && entry.name().equals("engines")));
    }

    @Test
    void rejectsPathsEscapingTheStorageRoot() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> ComputedPackageStorage.sanitizeRelativePath("../evil", ".computed"));
        assertThrows(IllegalArgumentException.class,
                () -> ComputedPackageStorage.sanitizeRelativeFolder("a/../../b"));
        assertThrows(IllegalArgumentException.class,
                () -> ComputedPackageStorage.sanitizeRelativePath("..", ".computed"));
        assertEquals("", ComputedPackageStorage.sanitizeRelativeFolder("/"));
        assertEquals("planes/racers", ComputedPackageStorage.sanitizeRelativeFolder("/planes/racers/"));

        ComputedPackageStorage storage = new ComputedPackageStorage(temporaryDirectory);
        assertThrows(IllegalArgumentException.class,
                () -> storage.createFolder(ComputedPackageStorage.Kind.GRAPHS, ".."));
        assertThrows(IllegalArgumentException.class,
                () -> storage.target(ComputedPackageStorage.Kind.GRAPHS, "a/../b"));
    }
}
