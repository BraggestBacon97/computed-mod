package dev.propulsionteam.computed.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Filesystem boundary for player-visible Computed imports and exports. */
public final class ComputedPackageStorage {
    public enum Kind { GRAPHS("graphs", ".computed"), NODES("nodes", ".lua");
        private final String folder;
        private final String extension;
        Kind(String folder, String extension) {
            this.folder = folder;
            this.extension = extension;
        }

        public String extension() { return extension; }
    }

    /** One immediate child of a storage folder: either a subfolder or a package file. */
    public record Entry(boolean directory, String name, Path file) {
        public boolean matches(Kind kind) {
            return !directory && name.toLowerCase(Locale.ROOT).endsWith(kind.extension);
        }
    }

    private static final Pattern ILLEGAL_NAME = Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]");
    private static final int MAX_SEGMENT_LENGTH = 96;
    private static final int MAX_PATH_LENGTH = 256;
    private final Path root;

    /**
     * @param packageDirectory directory that directly contains the {@code graphs} and {@code nodes}
     *     folders — one computer's package tree inside a world save
     *     ({@code <world>/computed/packages/<id>}), a game directory (unit tests), or a legacy
     *     install
     */
    public ComputedPackageStorage(Path packageDirectory) {
        root = packageDirectory.normalize();
    }

    public Path directory(Kind kind) throws IOException {
        Path directory = root.resolve(kind.folder).normalize();
        if (!directory.startsWith(root)) throw new IOException("Invalid Computed storage path");
        return Files.createDirectories(directory);
    }

    /** All package files below a kind, recursively. */
    public List<Path> list(Kind kind) throws IOException {
        List<Path> files = new ArrayList<>();
        collectFiles(directory(kind), files);
        files.removeIf(path -> !path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(kind.extension));
        files.sort(Comparator.comparing(Path::toString, String.CASE_INSENSITIVE_ORDER));
        return files;
    }

    /** Immediate children (subfolders first, then files) of a folder inside a kind. */
    public List<Entry> listEntries(Kind kind, String relativeFolder) throws IOException {
        Path folder = resolveFolder(kind, relativeFolder);
        if (!Files.isDirectory(folder)) return List.of();
        List<Entry> entries = new ArrayList<>();
        try (var paths = Files.list(folder)) {
            for (Path path : paths.toList()) {
                String name = path.getFileName().toString();
                if (Files.isDirectory(path)) {
                    entries.add(new Entry(true, name, path));
                } else if (path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(kind.extension)) {
                    entries.add(new Entry(false, name, path));
                }
            }
        }
        entries.sort(Comparator
                .comparing((Entry entry) -> !entry.directory())
                .thenComparing(entry -> entry.name(), String.CASE_INSENSITIVE_ORDER));
        return entries;
    }

    /** Resolves a package file target below a kind; the name may contain "/" separated subfolders. */
    public Path target(Kind kind, String requestedPath) throws IOException {
        Path file = source(kind, requestedPath);
        Files.createDirectories(file.getParent());
        return file;
    }

    /** Resolves a package file path below a kind without creating anything on disk. */
    public Path source(Kind kind, String requestedPath) throws IOException {
        String relative = sanitizeRelativePath(requestedPath, kind.extension);
        Path directory = directory(kind).resolve(parent(relative)).normalize();
        if (!directory.startsWith(root.resolve(kind.folder))) throw new IOException("Invalid Computed storage path");
        return directory.resolve(fileName(relative, kind.extension));
    }

    public Path nextAvailable(Kind kind, String requestedPath) throws IOException {
        Path initial = target(kind, requestedPath);
        if (!Files.exists(initial)) return initial;
        String full = initial.getFileName().toString();
        String base = full.substring(0, full.length() - kind.extension.length());
        Path folder = initial.getParent();
        for (int number = 1; number < 10_000; number++) {
            Path candidate = folder.resolve(base + " (" + number + ")" + kind.extension);
            if (!Files.exists(candidate)) return candidate;
        }
        throw new IOException("Could not find an available export name");
    }

    public Path createFolder(Kind kind, String relativeFolder) throws IOException {
        String relative = sanitizeRelativeFolder(relativeFolder);
        if (relative.isEmpty()) return directory(kind);
        Path folder = directory(kind).resolve(relative).normalize();
        if (!folder.startsWith(root.resolve(kind.folder))) throw new IOException("Invalid Computed storage path");
        return Files.createDirectories(folder);
    }

    public static String sanitizeName(String name) {
        String cleaned = ILLEGAL_NAME.matcher(name == null ? "" : name.strip()).replaceAll("_")
                .replaceAll("\\s+", " ").replaceAll("[. ]+$", "");
        if (cleaned.isBlank() || cleaned.equals(".") || cleaned.equals("..")) throw new IllegalArgumentException("A file name is required");
        return cleaned.length() > MAX_SEGMENT_LENGTH ? cleaned.substring(0, MAX_SEGMENT_LENGTH).strip() : cleaned;
    }

    /**
     * Normalizes a "/" separated relative path such as "planes/cockpit" (optionally ending in
     * {@code extension}) and rejects anything that could escape the kind directory.
     */
    public static String sanitizeRelativePath(String requested, String extension) {
        String trimmed = requested == null ? "" : requested.strip();
        if (extension != null && trimmed.toLowerCase(Locale.ROOT).endsWith(extension.toLowerCase(Locale.ROOT))) {
            trimmed = trimmed.substring(0, trimmed.length() - extension.length());
        }
        String folder = sanitizeRelativeFolder(parentOf(trimmed));
        String file = sanitizeSegment(fileNameOf(trimmed), "A file name is required");
        return folder.isEmpty() ? file : folder + "/" + file;
    }

    /** Normalizes a folder path such as "planes/racers" and returns "" for the kind root. */
    public static String sanitizeRelativeFolder(String requested) {
        String trimmed = requested == null ? "" : requested.strip().replace('\\', '/');
        while (trimmed.startsWith("/")) trimmed = trimmed.substring(1);
        while (trimmed.endsWith("/")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        if (trimmed.isEmpty()) return "";
        List<String> segments = new ArrayList<>();
        for (String rawSegment : trimmed.split("/")) {
            if (rawSegment.isEmpty() || rawSegment.equals(".")) continue;
            segments.add(sanitizeSegment(rawSegment, "A folder name is required"));
        }
        String joined = String.join("/", segments);
        if (joined.length() > MAX_PATH_LENGTH) throw new IllegalArgumentException("Path is too long");
        return joined;
    }

    private static String sanitizeSegment(String raw, String blankMessage) {
        String segment = ILLEGAL_NAME.matcher(raw == null ? "" : raw.strip()).replaceAll("_")
                .replaceAll("\\s+", " ").replaceAll("[. ]+$", "");
        if (segment.isBlank() || segment.equals(".") || segment.equals("..")) {
            throw new IllegalArgumentException(blankMessage);
        }
        return segment.length() > MAX_SEGMENT_LENGTH ? segment.substring(0, MAX_SEGMENT_LENGTH).strip() : segment;
    }

    private Path resolveFolder(Kind kind, String relativeFolder) throws IOException {
        String relative = sanitizeRelativeFolder(relativeFolder);
        Path folder = directory(kind).resolve(relative.isEmpty() ? "." : relative).normalize();
        if (!folder.startsWith(root.resolve(kind.folder))) throw new IOException("Invalid Computed storage path");
        return folder;
    }

    private static void collectFiles(Path folder, List<Path> files) throws IOException {
        if (!Files.isDirectory(folder)) return;
        try (var paths = Files.list(folder)) {
            for (Path path : paths.toList()) {
                if (Files.isDirectory(path)) collectFiles(path, files);
                else files.add(path);
            }
        }
    }

    private static String parent(String relative) {
        int separator = relative.lastIndexOf('/');
        return separator < 0 ? "" : relative.substring(0, separator);
    }

    private static String fileName(String relative, String extension) {
        int separator = relative.lastIndexOf('/');
        String name = separator < 0 ? relative : relative.substring(separator + 1);
        return name.toLowerCase(Locale.ROOT).endsWith(extension) ? name : name + extension;
    }

    private static String parentOf(String path) {
        int separator = path.lastIndexOf('/');
        return separator < 0 ? "" : path.substring(0, separator);
    }

    private static String fileNameOf(String path) {
        int separator = path.lastIndexOf('/');
        return separator < 0 ? path : path.substring(separator + 1);
    }
}
