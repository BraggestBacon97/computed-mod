package dev.propulsionteam.computed.lua.fs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ComputerFsServiceTest {
    @TempDir Path temporaryDirectory;

    private ComputerFsService.ComputerFs attach(ComputerFsService service, String name) {
        UUID id = UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
        return service.attachComputer(id, temporaryDirectory.resolve(name));
    }

    @Test
    void isolatesFilesystemsBetweenComputers() {
        ComputerFsService service = new ComputerFsService();
        ComputerFsService.ComputerFs alpha = attach(service, "alpha");
        ComputerFsService.ComputerFs beta = attach(service, "beta");

        alpha.makeDirectory("/logs");
        ComputerFsService.ComputerFs.Handle handle = alpha.open("/logs/note.txt", "w");
        handle.writeLine("secret");
        handle.close();

        assertTrue(alpha.exists("/logs/note.txt"));
        assertFalse(beta.exists("/logs/note.txt"));
        assertTrue(beta.list("/").isEmpty());
        assertThrows(ComputerFsException.class, () -> beta.open("/logs/note.txt", "r"));
    }

    @Test
    void blocksPathTraversalOutsideTheRoot() {
        ComputerFsService service = new ComputerFsService();
        ComputerFsService.ComputerFs fs = attach(service, "traversal");

        fs.open("/ok.txt", "w").close();
        assertTrue(fs.exists("/ok.txt"));
        // ".." clamps at the root instead of escaping.
        assertTrue(fs.exists("/../ok.txt"));
        assertFalse(fs.exists("/../../etc/passwd"));
        assertThrows(ComputerFsException.class, () -> fs.open("x".repeat(80), "w"));
    }

    @Test
    void enforcesQuotaOnFlush() {
        ComputerFsService service = new ComputerFsService(64);
        ComputerFsService.ComputerFs fs = attach(service, "quota");

        ComputerFsService.ComputerFs.Handle small = fs.open("/small.txt", "w");
        small.write("0123456789");
        small.close();
        assertEquals(10, fs.usedBytes());
        assertTrue(fs.freeSpace() >= 54);

        ComputerFsService.ComputerFs.Handle large = fs.open("/large.txt", "w");
        large.write("x".repeat(100));
        assertThrows(ComputerFsException.class, large::flush);
        assertEquals(10, fs.usedBytes());
        assertThrows(ComputerFsException.class, large::close);
        assertFalse(fs.exists("/large.txt"));
    }

    @Test
    void supportsTextReadWriteAndLineEndings() throws Exception {
        ComputerFsService service = new ComputerFsService();
        ComputerFsService.ComputerFs fs = attach(service, "text");

        ComputerFsService.ComputerFs.Handle writer = fs.open("/log.txt", "w");
        writer.writeLine("first");
        writer.write("second");
        writer.close();
        assertEquals(12, fs.size("/log.txt"));

        ComputerFsService.ComputerFs.Handle reader = fs.open("/log.txt", "r");
        assertEquals("first", reader.readLine());
        assertEquals("second", reader.readAll());
        assertNull(reader.readLine());
        reader.close();

        ComputerFsService.ComputerFs.Handle append = fs.open("/log.txt", "a");
        append.writeLine("third");
        append.close();
        assertEquals("first\nsecondthird\n", Files.readString(fs.root().resolve("log.txt")));
    }

    @Test
    void rejectsModeMismatchesAndClosedHandles() {
        ComputerFsService service = new ComputerFsService();
        ComputerFsService.ComputerFs fs = attach(service, "modes");
        fs.open("/data.txt", "w").close();

        ComputerFsService.ComputerFs.Handle reader = fs.open("/data.txt", "r");
        assertThrows(ComputerFsException.class, () -> reader.write("nope"));
        reader.close();
        assertThrows(ComputerFsException.class, reader::readAll);

        ComputerFsService.ComputerFs.Handle writer = fs.open("/out.txt", "w");
        assertThrows(ComputerFsException.class, writer::readAll);
        writer.close();

        assertThrows(ComputerFsException.class, () -> fs.open("/data.txt", "rw"));
        assertThrows(ComputerFsException.class, () -> fs.open("/missing.txt", "r"));
    }

    @Test
    void movesCopiesAndDeletesFiles() {
        ComputerFsService service = new ComputerFsService();
        ComputerFsService.ComputerFs fs = attach(service, "ops");

        ComputerFsService.ComputerFs.Handle handle = fs.open("/a.txt", "w");
        handle.write("payload");
        handle.close();

        fs.copy("/a.txt", "/b.txt");
        assertTrue(fs.exists("/a.txt"));
        assertTrue(fs.exists("/b.txt"));
        assertEquals(7, fs.size("/b.txt"));

        fs.move("/b.txt", "/c.txt");
        assertFalse(fs.exists("/b.txt"));
        assertTrue(fs.exists("/c.txt"));

        fs.delete("/c.txt");
        assertFalse(fs.exists("/c.txt"));
        fs.delete("/missing.txt"); // missing paths succeed
        fs.makeDirectory("/logs/deep");
        assertTrue(fs.isDirectory("/logs/deep"));
        fs.delete("/logs");
        assertFalse(fs.exists("/logs"));
    }

    @Test
    void seeksInBinaryHandles() {
        ComputerFsService service = new ComputerFsService();
        ComputerFsService.ComputerFs fs = attach(service, "binary");

        ComputerFsService.ComputerFs.Handle writer = fs.open("/blob.bin", "wb");
        writer.writeBytes(new byte[] {1, 2, 3, 4});
        writer.close();

        ComputerFsService.ComputerFs.Handle reader = fs.open("/blob.bin", "rb");
        assertEquals(2, reader.readBytes(2)[1]);
        reader.seek("set", 0);
        assertEquals(1, reader.readBytes(1)[0]);
        reader.seek("end", -2);
        byte[] tail = reader.readBytes(8);
        assertEquals(2, tail.length);
        assertEquals(3, tail[0]);
        assertNull(reader.readBytes(1));
        reader.close();
    }

    @Test
    void detachingClosesHandlesButKeepsFiles() {
        ComputerFsService service = new ComputerFsService();
        UUID id = UUID.nameUUIDFromBytes("detach".getBytes(StandardCharsets.UTF_8));
        ComputerFsService.ComputerFs fs = service.attachComputer(id, temporaryDirectory.resolve("detach"));

        ComputerFsService.ComputerFs.Handle handle = fs.open("/keep.txt", "w");
        handle.write("kept");
        service.detachComputer(id);
        assertTrue(Files.exists(temporaryDirectory.resolve("detach/keep.txt")));
        assertFalse(service.isAttached(id));
        assertThrows(ComputerFsException.class, handle::flush);
    }

    @Test
    void carriesTheNumericComputerIdOnAttach() {
        ComputerFsService service = new ComputerFsService();
        UUID id = UUID.nameUUIDFromBytes("numeric".getBytes(StandardCharsets.UTF_8));

        ComputerFsService.ComputerFs assigned = service.attachComputer(id, 7, temporaryDirectory.resolve("numeric"));
        assertEquals(7, assigned.numericId());

        UUID anonymous = UUID.nameUUIDFromBytes("anonymous".getBytes(StandardCharsets.UTF_8));
        ComputerFsService.ComputerFs unassigned =
                service.attachComputer(anonymous, temporaryDirectory.resolve("anonymous"));
        assertEquals(-1, unassigned.numericId());
    }

    @Test
    void reportsAttachedNumericIdOrMinusOne() {
        ComputerFsService service = new ComputerFsService();
        UUID id = UUID.nameUUIDFromBytes("attached-id".getBytes(StandardCharsets.UTF_8));
        assertEquals(-1, service.attachedNumericId(id));

        service.attachComputer(id, 7, temporaryDirectory.resolve("attached-id"));
        assertEquals(7, service.attachedNumericId(id));

        service.detachComputer(id);
        assertEquals(-1, service.attachedNumericId(id));
    }
}
