package dev.propulsionteam.computed.lua.fs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * Per-computer isolated filesystem roots. Each computer owns a directory tree under
 * {@code <world>/computed/computer/<id>/}; paths never escape their root and quotas bound usage.
 */
public final class ComputerFsService {
    public static final long DEFAULT_QUOTA_BYTES = 1024L * 1024L;
    private static final int MAX_PATH_LENGTH = 240;
    private static final int MAX_SEGMENT_LENGTH = 64;
    private static final int MAX_LISTING = 512;
    private static final Pattern ILLEGAL_SEGMENT = Pattern.compile("[\\\\:*?\"<>|\\p{Cntrl}]");

    private final Map<UUID, ComputerFs> computers = new ConcurrentHashMap<>();
    private final long quotaBytes;
    private final AtomicLong handleSequence = new AtomicLong();

    public ComputerFsService() {
        this(DEFAULT_QUOTA_BYTES);
    }

    public ComputerFsService(long quotaBytes) {
        this.quotaBytes = Math.max(1L, quotaBytes);
    }

    /** Creates (or replaces) the isolated filesystem root for a computer without a numeric id. */
    public ComputerFs attachComputer(UUID computerId, Path root) {
        return attachComputer(computerId, -1, root);
    }

    /** Creates (or replaces) the isolated filesystem root for a computer. */
    public ComputerFs attachComputer(UUID computerId, int numericId, Path root) {
        ComputerFs previous = computers.remove(computerId);
        if (previous != null) previous.closeAllHandles();
        Path normalized = root.toAbsolutePath().normalize();
        try {
            Files.createDirectories(normalized);
        } catch (IOException exception) {
            throw new ComputerFsException("Could not create computer filesystem: " + exception.getMessage());
        }
        ComputerFs filesystem = new ComputerFs(computerId, numericId, normalized);
        computers.put(computerId, filesystem);
        return filesystem;
    }

    /** Closes open handles and forgets the computer (does not delete files). */
    public void detachComputer(UUID computerId) {
        ComputerFs filesystem = computers.remove(computerId);
        if (filesystem != null) filesystem.closeAllHandles();
    }

    public ComputerFs require(UUID computerId) {
        ComputerFs filesystem = computers.get(computerId);
        if (filesystem == null) {
            throw new ComputerFsException("The computer filesystem is not available");
        }
        return filesystem;
    }

    public boolean isAttached(UUID computerId) {
        return computers.containsKey(computerId);
    }

    /** Numeric id of the attached filesystem, or {@code -1} when the computer is not attached. */
    public int attachedNumericId(UUID computerId) {
        ComputerFs filesystem = computers.get(computerId);
        return filesystem == null ? -1 : filesystem.numericId();
    }

    long nextHandleId() {
        return handleSequence.incrementAndGet();
    }

    /** Isolated filesystem for a single computer. Thread-safe for endpoint (computer-thread) use. */
    public final class ComputerFs {
        private final UUID computerId;
        private final int numericId;
        private final Path root;
        private final Map<Long, Handle> handles = new ConcurrentHashMap<>();
        private final Object diskLock = new Object();
        private volatile long usedBytes;

        private ComputerFs(UUID computerId, int numericId, Path root) {
            this.computerId = computerId;
            this.numericId = numericId;
            this.root = root;
            this.usedBytes = computeUsedBytes();
        }

        public UUID computerId() {
            return computerId;
        }

        /** Numeric, ComputerCraft-style computer id ({@code -1} when unassigned). */
        public int numericId() {
            return numericId;
        }

        public Path root() {
            return root;
        }

        public long usedBytes() {
            return usedBytes;
        }

        public long freeSpace() {
            return Math.max(0L, quotaBytes - usedBytes);
        }

        public List<String> list(String rawPath) {
            Path directory = resolveExisting(rawPath);
            if (!Files.isDirectory(directory)) {
                throw new ComputerFsException("Not a directory: " + rawPath);
            }
            try (var paths = Files.list(directory)) {
                return paths.filter(Files::exists)
                        .map(path -> path.getFileName().toString())
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .limit(MAX_LISTING)
                        .toList();
            } catch (IOException exception) {
                throw new ComputerFsException("Could not list " + rawPath + ": " + exception.getMessage());
            }
        }

        public boolean exists(String rawPath) {
            return Files.exists(resolve(rawPath));
        }

        public boolean isDirectory(String rawPath) {
            return Files.isDirectory(resolve(rawPath));
        }

        public long size(String rawPath) {
            Path file = resolveExisting(rawPath);
            if (Files.isDirectory(file)) {
                throw new ComputerFsException("Cannot take the size of a directory: " + rawPath);
            }
            try {
                return Files.size(file);
            } catch (IOException exception) {
                throw new ComputerFsException("Could not read size of " + rawPath + ": " + exception.getMessage());
            }
        }

        public void makeDirectory(String rawPath) {
            Path directory = resolve(rawPath);
            try {
                Files.createDirectories(directory);
            } catch (IOException exception) {
                throw new ComputerFsException("Could not create " + rawPath + ": " + exception.getMessage());
            }
        }

        public void move(String rawFrom, String rawTo) {
            copyInternal(rawFrom, rawTo, true);
        }

        public void copy(String rawFrom, String rawTo) {
            copyInternal(rawFrom, rawTo, false);
        }

        public void delete(String rawPath) {
            Path target = resolve(rawPath);
            if (!Files.exists(target)) return;
            synchronized (diskLock) {
                try {
                    if (Files.isDirectory(target)) {
                        try (var paths = Files.walk(target)) {
                            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                                long size = Files.isRegularFile(path) ? Files.size(path) : 0L;
                                Files.deleteIfExists(path);
                                usedBytes = Math.max(0L, usedBytes - size);
                            }
                        }
                    } else {
                        long size = Files.size(target);
                        Files.deleteIfExists(target);
                        usedBytes = Math.max(0L, usedBytes - size);
                    }
                } catch (IOException exception) {
                    throw new ComputerFsException("Could not delete " + rawPath + ": " + exception.getMessage());
                }
            }
        }

        public Handle open(String rawPath, String mode) {
            String normalizedMode = mode == null ? "" : mode.strip().toLowerCase(Locale.ROOT);
            boolean binary;
            boolean writing;
            boolean appending;
            switch (normalizedMode) {
                case "r" -> { binary = false; writing = false; appending = false; }
                case "rb" -> { binary = true; writing = false; appending = false; }
                case "w", "wb" -> { binary = normalizedMode.equals("wb"); writing = true; appending = false; }
                case "a", "ab" -> { binary = normalizedMode.equals("ab"); writing = true; appending = true; }
                default -> throw new ComputerFsException("Unsupported mode: " + mode);
            }
            Path file = resolve(rawPath);
            synchronized (diskLock) {
                if (writing) {
                    byte[] existing = new byte[0];
                    if (Files.isRegularFile(file)) {
                        if (appending) {
                            try {
                                existing = Files.readAllBytes(file);
                            } catch (IOException exception) {
                                throw new ComputerFsException("Could not open " + rawPath + ": " + exception.getMessage());
                            }
                        } else {
                            long previous = safeSize(file);
                            try {
                                Files.delete(file);
                            } catch (IOException exception) {
                                throw new ComputerFsException("Could not truncate " + rawPath + ": " + exception.getMessage());
                            }
                            usedBytes = Math.max(0L, usedBytes - previous);
                        }
                    }
                    try {
                        Files.createDirectories(file.getParent() == null ? root : file.getParent());
                    } catch (IOException exception) {
                        throw new ComputerFsException("Could not create parent of " + rawPath);
                    }
                    Handle handle = new Handle(nextHandleId(), rawPath, normalizedMode, binary, writing, appending, existing);
                    handles.put(handle.id(), handle);
                    return handle;
                }
                if (!Files.isRegularFile(file)) {
                    throw new ComputerFsException("No such file: " + rawPath);
                }
                byte[] content;
                try {
                    content = Files.readAllBytes(file);
                } catch (IOException exception) {
                    throw new ComputerFsException("Could not open " + rawPath + ": " + exception.getMessage());
                }
                Handle handle = new Handle(nextHandleId(), rawPath, normalizedMode, binary, false, false, content);
                handles.put(handle.id(), handle);
                return handle;
            }
        }

        Handle requireHandle(long handleId) {
            Handle handle = handles.get(handleId);
            if (handle == null) {
                throw new ComputerFsException("Invalid or closed file handle");
            }
            return handle;
        }

        void closeHandle(long handleId) {
            Handle handle = handles.remove(handleId);
            if (handle != null && handle.writing()) handle.flushToDisk();
        }

        void flushHandle(long handleId) {
            requireHandle(handleId).flushToDisk();
        }

        void closeAllHandles() {
            for (Handle handle : List.copyOf(handles.values())) {
                try {
                    handle.close();
                } catch (ComputerFsException ignored) {
                    // Unloading the computer still closes every handle.
                }
            }
            handles.clear();
        }

        private void copyInternal(String rawFrom, String rawTo, boolean move) {
            Path from = resolveExisting(rawFrom);
            Path to = resolve(rawTo);
            synchronized (diskLock) {
                try {
                    if (Files.isDirectory(from)) {
                        throw new ComputerFsException("Directory copy is not supported: " + rawFrom);
                    }
                    if (!Files.isRegularFile(from)) {
                        throw new ComputerFsException("No such file: " + rawFrom);
                    }
                    byte[] content = Files.readAllBytes(from);
                    long previousTarget = Files.isRegularFile(to) ? Files.size(to) : 0L;
                    ensureQuota(content.length - previousTarget);
                    Files.createDirectories(to.getParent() == null ? root : to.getParent());
                    Files.write(to, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                    usedBytes += content.length - previousTarget;
                    if (move) {
                        long size = Files.size(from);
                        Files.delete(from);
                        usedBytes = Math.max(0L, usedBytes - size);
                    }
                } catch (IOException exception) {
                    throw new ComputerFsException(
                            (move ? "Could not move " : "Could not copy ") + rawFrom + ": " + exception.getMessage());
                }
            }
        }

        private Path resolve(String rawPath) {
            String normalized = normalize(rawPath);
            Path resolved = normalized.isEmpty() ? root : root.resolve(normalized).normalize();
            if (!resolved.startsWith(root)) {
                throw new ComputerFsException("Invalid path: " + rawPath);
            }
            return resolved;
        }

        private Path resolveExisting(String rawPath) {
            Path resolved = resolve(rawPath);
            if (!Files.exists(resolved)) {
                throw new ComputerFsException("No such path: " + rawPath);
            }
            return resolved;
        }

        private String normalize(String rawPath) {
            String path = rawPath == null ? "" : rawPath.replace('\\', '/').strip();
            if (path.length() > MAX_PATH_LENGTH) {
                throw new ComputerFsException("Path is too long");
            }
            List<String> segments = new ArrayList<>();
            for (String rawSegment : path.split("/")) {
                if (rawSegment.isEmpty() || rawSegment.equals(".")) continue;
                if (rawSegment.equals("..")) {
                    if (!segments.isEmpty()) segments.removeLast();
                    continue;
                }
                segments.add(requireSegment(rawSegment));
            }
            return String.join("/", segments);
        }

        private String requireSegment(String rawSegment) {
            if (rawSegment.length() > MAX_SEGMENT_LENGTH) {
                throw new ComputerFsException("Path segment is too long");
            }
            String segment = ILLEGAL_SEGMENT.matcher(rawSegment).replaceAll("_").strip();
            if (segment.isBlank() || segment.equals(".") || segment.equals("..")) {
                throw new ComputerFsException("Invalid path segment: " + rawSegment);
            }
            return segment;
        }

        private void ensureQuota(long delta) {
            if (usedBytes + delta > quotaBytes) {
                throw new ComputerFsException("Out of space (quota " + quotaBytes + " bytes)");
            }
        }

        private long computeUsedBytes() {
            if (!Files.isDirectory(root)) return 0L;
            long total = 0L;
            try (var paths = Files.walk(root)) {
                for (Path path : paths.toList()) {
                    if (Files.isRegularFile(path)) total += Files.size(path);
                }
            } catch (IOException ignored) {
                return 0L;
            }
            return total;
        }

        private long safeSize(Path file) {
            try {
                return Files.isRegularFile(file) ? Files.size(file) : 0L;
            } catch (IOException ignored) {
                return 0L;
            }
        }

        /** Open file handle with in-memory buffering; writes flush on flush/close. */
        public final class Handle {
            private final long id;
            private final String path;
            private final String mode;
            private final boolean binary;
            private final boolean writing;
            private final boolean appending;
            private byte[] content;
            private int position;
            private boolean closed;

            private Handle(long id, String path, String mode, boolean binary, boolean writing, boolean appending, byte[] content) {
                this.id = id;
                this.path = path;
                this.mode = mode;
                this.binary = binary;
                this.writing = writing;
                this.appending = appending;
                this.content = content;
                this.position = appending ? content.length : 0;
            }

            public long id() {
                return id;
            }

            public synchronized int position() {
                return position;
            }

            public synchronized boolean atEnd() {
                return position >= content.length;
            }

            public String path() {
                return path;
            }

            public String mode() {
                return mode;
            }

            public boolean binary() {
                return binary;
            }

            public boolean writing() {
                return writing;
            }

            public synchronized String readLine() {
                requireOpen();
                if (writing) throw new ComputerFsException("Handle is not readable");
                if (position >= content.length) return null;
                int start = position;
                while (position < content.length && content[position] != (byte) '\n') position++;
                int end = position;
                if (position < content.length) position++;
                String line = new String(content, start, end - start, StandardCharsets.UTF_8);
                if (!line.isEmpty() && line.charAt(line.length() - 1) == '\r') {
                    line = line.substring(0, line.length() - 1);
                }
                return line;
            }

            public synchronized String readAll() {
                requireOpen();
                if (writing) throw new ComputerFsException("Handle is not readable");
                String text = new String(content, position, content.length - position, StandardCharsets.UTF_8);
                position = content.length;
                return text;
            }

            public synchronized String read(int count) {
                requireOpen();
                if (writing) throw new ComputerFsException("Handle is not readable");
                if (count <= 0) return "";
                int available = content.length - position;
                if (available <= 0) return null;
                int take = Math.min(count, available);
                String text = new String(content, position, take, StandardCharsets.UTF_8);
                position += take;
                return text;
            }

            public synchronized byte[] readBytes(int count) {
                requireOpen();
                if (writing) throw new ComputerFsException("Handle is not readable");
                if (count <= 0) return new byte[0];
                int available = content.length - position;
                if (available <= 0) return null;
                int take = Math.min(count, available);
                byte[] bytes = new byte[take];
                System.arraycopy(content, position, bytes, 0, take);
                position += take;
                return bytes;
            }

            public synchronized void write(String text) {
                requireOpen();
                if (!writing) throw new ComputerFsException("Handle is not writable");
                writeBytesInternal(text == null ? new byte[0] : text.getBytes(StandardCharsets.UTF_8));
            }

            public synchronized void writeLine(String text) {
                requireOpen();
                if (!writing) throw new ComputerFsException("Handle is not writable");
                String value = text == null ? "" : text;
                writeBytesInternal((value + "\n").getBytes(StandardCharsets.UTF_8));
            }

            public synchronized void writeBytes(byte[] bytes) {
                requireOpen();
                if (!writing) throw new ComputerFsException("Handle is not writable");
                writeBytesInternal(bytes == null ? new byte[0] : bytes);
            }

            public synchronized void seek(String whence, int offset) {
                requireOpen();
                int base = switch (whence == null ? "set" : whence) {
                    case "set" -> 0;
                    case "cur" -> position;
                    case "end" -> content.length;
                    default -> throw new ComputerFsException("Invalid seek whence: " + whence);
                };
                int next = base + offset;
                if (next < 0 || next > content.length) {
                    throw new ComputerFsException("Seek out of range");
                }
                position = next;
            }

            public synchronized void flush() {
                requireOpen();
                if (writing) flushToDisk();
            }

            public synchronized void close() {
                if (closed) return;
                closed = true;
                handles.remove(id);
                if (writing) flushToDisk();
            }

            private void writeBytesInternal(byte[] bytes) {
                int nextLength = position + bytes.length;
                if (nextLength > content.length) {
                    byte[] expanded = new byte[nextLength];
                    System.arraycopy(content, 0, expanded, 0, position);
                    content = expanded;
                }
                System.arraycopy(bytes, 0, content, position, bytes.length);
                position = nextLength;
            }

            private void flushToDisk() {
                Path file = resolve(path);
                synchronized (diskLock) {
                    try {
                        Files.createDirectories(file.getParent() == null ? root : file.getParent());
                        long previous = Files.isRegularFile(file) ? Files.size(file) : 0L;
                        ensureQuota(content.length - previous);
                        Files.write(file, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                        usedBytes += content.length - previous;
                    } catch (IOException exception) {
                        throw new ComputerFsException("Could not write " + path + ": " + exception.getMessage());
                    }
                }
            }

            private void requireOpen() {
                if (closed) throw new ComputerFsException("File handle is closed");
            }
        }
    }
}
