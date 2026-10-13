package dev.propulsionteam.computed.lua.fs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ComputerCraft-style numeric computer id allocation persisted in
 * {@code <world>/computed/ids.json}. The file stores the last assigned id
 * ({@code {"computer": N}}), so the next computer receives {@code N + 1}
 * and the very first computer receives {@code 0}. Pure nio — unit tests
 * never load Minecraft classes.
 */
public final class ComputerIdAssigner {
    private static final Logger LOGGER = LoggerFactory.getLogger(ComputerIdAssigner.class);
    private static final Pattern COMPUTER_ID = Pattern.compile("\"computer\"\\s*:\\s*(-?\\d+)");

    private ComputerIdAssigner() {}

    /**
     * Allocates the next computer id and persists it inside {@code computedDirectory}.
     * Returns {@code -1} when the id cannot be persisted (the caller keeps the
     * computer unassigned instead of crashing).
     */
    public static synchronized int allocate(Path computedDirectory) {
        int lastAssigned = readLastAssigned(computedDirectory.resolve("ids.json"));
        int highestFolder = scanHighestComputerFolder(computedDirectory.resolve("computer"));
        int next = Math.max(lastAssigned, highestFolder) + 1;
        if (next < 0) {
            LOGGER.warn("Computer id allocation overflowed; leaving computer unassigned");
            return -1;
        }
        Path idsFile = computedDirectory.resolve("ids.json");
        try {
            Files.createDirectories(computedDirectory);
            writeIdsFile(idsFile, next);
        } catch (IOException exception) {
            LOGGER.warn("Could not persist computer id {}: {}", next, exception.toString());
            return -1;
        }
        return next;
    }

    /**
     * Advances the persisted counter to at least {@code id} so future allocations skip a manually
     * chosen id (the Set Computer ID dialog). Lower or equal ids are already covered.
     *
     * @return whether the counter was persisted successfully
     */
    public static synchronized boolean reserve(Path computedDirectory, int id) {
        if (id < 0) {
            return false;
        }
        Path idsFile = computedDirectory.resolve("ids.json");
        if (readLastAssigned(idsFile) >= id) {
            return true;
        }
        try {
            Files.createDirectories(computedDirectory);
            writeIdsFile(idsFile, id);
            return true;
        } catch (IOException exception) {
            LOGGER.warn("Could not reserve computer id {}: {}", id, exception.toString());
            return false;
        }
    }

    private static int readLastAssigned(Path idsFile) {
        try {
            if (!Files.isRegularFile(idsFile)) {
                return -1;
            }
            Matcher matcher = COMPUTER_ID.matcher(Files.readString(idsFile, StandardCharsets.UTF_8));
            if (!matcher.find()) {
                return -1;
            }
            return Math.max(-1, Integer.parseInt(matcher.group(1)));
        } catch (IOException | NumberFormatException exception) {
            return -1;
        }
    }

    private static int scanHighestComputerFolder(Path computersDirectory) {
        if (!Files.isDirectory(computersDirectory)) {
            return -1;
        }
        int highest = -1;
        try (var paths = Files.list(computersDirectory)) {
            for (Path path : paths.toList()) {
                if (Files.isDirectory(path)) {
                    highest = Math.max(highest, parseNumber(path.getFileName().toString()));
                }
            }
        } catch (IOException ignored) {
            // Folder scan is a fallback; the ids file is authoritative when readable.
        }
        return highest;
    }

    private static int parseNumber(String name) {
        try {
            return Integer.parseInt(name);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static void writeIdsFile(Path idsFile, int id) throws IOException {
        String content = "{\n  \"computer\": " + id + "\n}";
        Path staging = idsFile.resolveSibling(idsFile.getFileName() + ".new");
        Files.writeString(staging, content, StandardCharsets.UTF_8);
        try {
            Files.move(staging, idsFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(staging, idsFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
