package dev.propulsionteam.computed.persistence;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.propulsionteam.computed.persistence.ComputedPackageStorage.Kind;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ProgramDiskDataTest {
    private static int kindId(Kind kind) {
        return PackageTransferService.id(kind);
    }

    @Test
    void roundTripsAGraphArchive() {
        byte[] content = "graph-bytes".getBytes(StandardCharsets.UTF_8);
        ProgramDiskData data = new ProgramDiskData(kindId(Kind.GRAPHS), "mission.computed", content);

        ProgramDiskData decoded = ProgramDiskData.decode(data.encode());

        assertEquals(kindId(Kind.GRAPHS), decoded.kind());
        assertEquals("mission.computed", decoded.name());
        assertArrayEquals(content, decoded.content());
    }

    @Test
    void roundTripsANodeSourceWithUtf8Content() {
        byte[] content = "return { label = \"Übergabe ☃\" }".getBytes(StandardCharsets.UTF_8);
        ProgramDiskData data = new ProgramDiskData(kindId(Kind.NODES), "helper.lua", content);

        ProgramDiskData decoded = ProgramDiskData.decode(data.encode());

        assertEquals(kindId(Kind.NODES), decoded.kind());
        assertEquals("helper.lua", decoded.name());
        assertArrayEquals(content, decoded.content());
    }

    @Test
    void roundTripsEmptyContent() {
        ProgramDiskData data = new ProgramDiskData(kindId(Kind.GRAPHS), "empty.computed", new byte[0]);

        ProgramDiskData decoded = ProgramDiskData.decode(data.encode());

        assertEquals(0, decoded.content().length);
    }

    @Test
    void rejectsEmptyAndNullPayloads() {
        assertThrows(IllegalArgumentException.class, () -> ProgramDiskData.decode(null));
        assertThrows(IllegalArgumentException.class, () -> ProgramDiskData.decode(new byte[0]));
    }

    @Test
    void rejectsTruncatedPayload() {
        byte[] encoded = new ProgramDiskData(kindId(Kind.GRAPHS), "mission.computed", new byte[64])
                .encode();

        byte[] truncated = java.util.Arrays.copyOf(encoded, encoded.length - 10);

        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> ProgramDiskData.decode(truncated));
        assertTrue(error.getMessage().contains("damaged"), error.getMessage());
    }

    @Test
    void rejectsTrailingBytes() {
        byte[] encoded = new ProgramDiskData(kindId(Kind.GRAPHS), "mission.computed", new byte[8])
                .encode();
        byte[] withTrailing = java.util.Arrays.copyOf(encoded, encoded.length + 1);

        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> ProgramDiskData.decode(withTrailing));
        assertTrue(error.getMessage().contains("damaged"), error.getMessage());
    }

    @Test
    void rejectsUnknownVersion() {
        byte[] encoded = new ProgramDiskData(kindId(Kind.NODES), "helper.lua", new byte[4]).encode();
        encoded[0] = 99;

        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> ProgramDiskData.decode(encoded));
        assertTrue(error.getMessage().contains("version"), error.getMessage());
    }

    @Test
    void rejectsUnknownKind() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(1);
            out.writeInt(42);
            out.writeUTF("mission.computed");
            out.writeInt(0);
        }

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class, () -> ProgramDiskData.decode(bytes.toByteArray()));
        assertTrue(error.getMessage().contains("kind"), error.getMessage());
    }

    @Test
    void rejectsInvalidValuesOnConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new ProgramDiskData(-1, "a.computed", new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> new ProgramDiskData(0, "", new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> new ProgramDiskData(0, null, new byte[0]));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProgramDiskData(0, "a".repeat(ProgramDiskData.MAX_NAME_LENGTH + 1), new byte[0]));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProgramDiskData(
                        0, "big.computed", new byte[PackageTransferService.MAX_PACKAGE_BYTES + 1]));
    }
}
