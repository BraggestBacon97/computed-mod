package dev.propulsionteam.computed;

import dev.propulsionteam.computed.persistence.PackageTransferService;
import java.util.List;

/**
 * Set from the physical-client entrypoint so common networking code never references client-only
 * types. Routes world-package transfer results from networking into the open editor screen.
 */
public final class PackageTransferBridge {
    @FunctionalInterface
    public interface ListResultHandler {
        void handle(int kind, String folder, String error, List<PackageTransferService.Entry> entries);
    }

    @FunctionalInterface
    public interface ReadResultHandler {
        void handle(int kind, String path, String error, byte[] data);
    }

    @FunctionalInterface
    public interface WriteResultHandler {
        void handle(int opId, int status, String fileName, String error);
    }

    @FunctionalInterface
    public interface ComputerIdResultHandler {
        void handle(boolean accepted, int computerId, String message);
    }

    private static ListResultHandler listResults;
    private static ReadResultHandler readResults;
    private static WriteResultHandler writeResults;
    private static ComputerIdResultHandler computerIdResults;

    private PackageTransferBridge() {}

    public static void install(
            ListResultHandler listResultHandler,
            ReadResultHandler readResultHandler,
            WriteResultHandler writeResultHandler,
            ComputerIdResultHandler computerIdResultHandler) {
        listResults = listResultHandler;
        readResults = readResultHandler;
        writeResults = writeResultHandler;
        computerIdResults = computerIdResultHandler;
    }

    public static void listResult(
            int kind, String folder, String error, List<PackageTransferService.Entry> entries) {
        if (listResults != null) {
            listResults.handle(kind, folder, error, entries);
        }
    }

    public static void readResult(int kind, String path, String error, byte[] data) {
        if (readResults != null) {
            readResults.handle(kind, path, error, data);
        }
    }

    public static void writeResult(int opId, int status, String fileName, String error) {
        if (writeResults != null) {
            writeResults.handle(opId, status, fileName, error);
        }
    }

    public static void computerIdResult(boolean accepted, int computerId, String message) {
        if (computerIdResults != null) {
            computerIdResults.handle(accepted, computerId, message);
        }
    }
}
