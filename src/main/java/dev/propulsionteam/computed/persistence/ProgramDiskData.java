package dev.propulsionteam.computed.persistence;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Content of a program disk item: one package (graph archive or node source) carried as a plain
 * byte encoding so players can share programs without matching computer IDs. Pure Java — unit
 * tests never load Minecraft classes; the item component glue lives in {@code ProgramDiskItem}.
 *
 * <p>Wire format: version byte, kind id, UTF-8 name (with extension), length-prefixed content.
 */
public record ProgramDiskData(int kind, String name, byte[] content) {
    private static final byte VERSION = 1;
    /** Upper bound for the stored file name (including the extension). */
    public static final int MAX_NAME_LENGTH = 128;

    public ProgramDiskData {
        PackageTransferService.kind(kind);
        if (name == null || name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Invalid program name");
        }
        byte[] bytes = content == null ? new byte[0] : content;
        if (bytes.length > PackageTransferService.MAX_PACKAGE_BYTES) {
            throw new IllegalArgumentException(
                    "Program is too large (max " + PackageTransferService.MAX_PACKAGE_BYTES + " bytes)");
        }
        content = bytes;
    }

    public byte[] encode() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(content.length + 16);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(VERSION);
            out.writeInt(kind);
            out.writeUTF(name);
            out.writeInt(content.length);
            out.write(content);
        } catch (IOException exception) {
            throw new IllegalStateException("Program disk encoding failed", exception);
        }
        return bytes.toByteArray();
    }

    /**
     * @throws IllegalArgumentException when the bytes are not a complete program disk payload
     */
    public static ProgramDiskData decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Program disk is empty");
        }
        try {
            return decodeChecked(bytes);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Program disk data is damaged", exception);
        }
    }

    private static ProgramDiskData decodeChecked(byte[] bytes) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
        int version = in.readUnsignedByte();
        if (version != VERSION) {
            throw new IllegalArgumentException("Unsupported program disk version " + version);
        }
        int kind = in.readInt();
        PackageTransferService.kind(kind);
        String name = in.readUTF();
        int length = in.readInt();
        if (length < 0 || length > PackageTransferService.MAX_PACKAGE_BYTES || in.available() != length) {
            throw new IllegalArgumentException("Program disk data is damaged");
        }
        byte[] content = new byte[length];
        in.readFully(content);
        return new ProgramDiskData(kind, name, content);
    }
}
