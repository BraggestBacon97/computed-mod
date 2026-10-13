package dev.propulsionteam.computed.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Server-side operations for graph and node packages scoped to one computer ID. Packages live
 * under {@code <world>/computed/packages/<id>/{graphs,nodes}}: every computer that uses the same
 * ID shares the same folders, while other IDs cannot see them. All editor import/export traffic
 * goes through this service on the server so package folders work on dedicated servers. Pure nio —
 * unit tests never load Minecraft classes.
 */
public final class PackageTransferService {
    /** Hard cap per package in both directions (below the 1 MiB clientbound packet limit). */
    public static final int MAX_PACKAGE_BYTES = 1_000_000;
    /** Payload budget per C2S write packet (the serverbound limit is 32 KiB). */
    public static final int WRITE_CHUNK_BYTES = 24_000;
    /** Upper bound for one directory listing sent to a client. */
    public static final int MAX_LISTING_ENTRIES = 512;

    /** How a write resolves an already existing target file. */
    public enum WriteMode { FAIL_IF_EXISTS, OVERWRITE, NEXT_AVAILABLE }

    /** Where a write lands: the computer's package folders or the held program disk. */
    public enum WriteTarget { FOLDER, DISK }

    /** Result status of a write request; ordinals travel over the network. */
    public enum WriteStatus { OK, EXISTS, ERROR }

    /** One immediate child of a package folder as seen over the network. */
    public record Entry(String name, boolean directory) {}

    public record WriteResult(WriteStatus status, String fileName, String error) {
        public static WriteResult ok(String fileName) {
            return new WriteResult(WriteStatus.OK, fileName, "");
        }

        public static WriteResult exists() {
            return new WriteResult(WriteStatus.EXISTS, "", "");
        }

        public static WriteResult error(String message) {
            return new WriteResult(
                    WriteStatus.ERROR, "",
                    message == null || message.isBlank() ? "unknown error" : message);
        }
    }

    private final ComputedPackageStorage storage;

    /**
     * @param packageDirectory one computer's package directory (see
     *     {@link #computerDirectory(Path, int)}) that directly contains the kind folders
     */
    public PackageTransferService(Path packageDirectory) {
        this.storage = new ComputedPackageStorage(packageDirectory);
    }

    /** {@code <world>/computed/packages/<computerId>} — the package directory of one computer ID. */
    public static Path computerDirectory(Path worldDirectory, int computerId) {
        return worldDirectory.resolve("computed").resolve("packages").resolve(Integer.toString(computerId));
    }

    public static int id(ComputedPackageStorage.Kind kind) {
        return kind.ordinal();
    }

    public static ComputedPackageStorage.Kind kind(int id) {
        ComputedPackageStorage.Kind[] values = ComputedPackageStorage.Kind.values();
        if (id < 0 || id >= values.length) {
            throw new IllegalArgumentException("Unknown package kind " + id);
        }
        return values[id];
    }

    public static int id(WriteMode mode) {
        return mode.ordinal();
    }

    public static WriteMode mode(int id) {
        WriteMode[] values = WriteMode.values();
        if (id < 0 || id >= values.length) {
            throw new IllegalArgumentException("Unknown write mode " + id);
        }
        return values[id];
    }

    public static int id(WriteTarget target) {
        return target.ordinal();
    }

    public static WriteTarget target(int id) {
        WriteTarget[] values = WriteTarget.values();
        if (id < 0 || id >= values.length) {
            throw new IllegalArgumentException("Unknown write target " + id);
        }
        return values[id];
    }

    /** Immediate children of a package folder, capped at {@link #MAX_LISTING_ENTRIES}. */
    public List<Entry> list(ComputedPackageStorage.Kind kind, String folder) throws IOException {
        List<ComputedPackageStorage.Entry> entries = storage.listEntries(kind, folder);
        List<Entry> result = new ArrayList<>(Math.min(entries.size(), MAX_LISTING_ENTRIES));
        for (ComputedPackageStorage.Entry entry : entries) {
            if (result.size() >= MAX_LISTING_ENTRIES) break;
            result.add(new Entry(entry.name(), entry.directory()));
        }
        return result;
    }

    /** Reads a package file; throws when it is missing or exceeds {@link #MAX_PACKAGE_BYTES}. */
    public byte[] read(ComputedPackageStorage.Kind kind, String path) throws IOException {
        Path file = storage.source(kind, path);
        if (!Files.isRegularFile(file)) {
            throw new IOException("No such file: " + path);
        }
        long size = Files.size(file);
        if (size > MAX_PACKAGE_BYTES) {
            throw new IOException("File is too large (max " + MAX_PACKAGE_BYTES + " bytes)");
        }
        return Files.readAllBytes(file);
    }

    /** Writes a package honouring the chosen overwrite policy. */
    public WriteResult write(ComputedPackageStorage.Kind kind, String path, byte[] content, WriteMode mode) {
        byte[] bytes = content == null ? new byte[0] : content;
        if (bytes.length > MAX_PACKAGE_BYTES) {
            return WriteResult.error("Package is too large (max " + MAX_PACKAGE_BYTES + " bytes)");
        }
        try {
            if (mode == WriteMode.FAIL_IF_EXISTS && Files.exists(storage.source(kind, path))) {
                return WriteResult.exists();
            }
            Path target = mode == WriteMode.NEXT_AVAILABLE
                    ? storage.nextAvailable(kind, path)
                    : storage.target(kind, path);
            Files.write(target, bytes);
            return WriteResult.ok(target.getFileName().toString());
        } catch (IOException | IllegalArgumentException exception) {
            return WriteResult.error(exception.getMessage());
        }
    }
}
