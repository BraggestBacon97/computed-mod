package net.minecraft.nbt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.UUID;

/**
 * Minimal binary NBT writer/reader that matches vanilla's unnamed-tag layout
 * ({@code byte id, writeUTF("") , payload} with compound entries terminated by {@code TAG_END}), so
 * package round-trip tests produce byte-compatible {@code .computed} payloads without loading the
 * game runtime.
 */
public final class NbtIo {
    private NbtIo() {}

    public static void write(CompoundTag tag, DataOutput output) throws IOException {
        output.writeByte(Tag.TAG_COMPOUND);
        output.writeUTF("");
        writeCompoundPayload(tag, output);
    }

    public static CompoundTag read(DataInput input) throws IOException {
        byte id = input.readByte();
        if (id != Tag.TAG_COMPOUND) {
            throw new IOException("Root tag must be a named compound tag");
        }
        input.readUTF();
        return readCompoundPayload(input);
    }

    /** Mirrors the vanilla overload; the shim does not track NBT size accounting. */
    public static CompoundTag read(DataInput input, NbtAccounter accounter) throws IOException {
        return read(input);
    }

    private static void writeCompoundPayload(CompoundTag tag, DataOutput output) throws IOException {
        for (String key : tag.getAllKeys()) {
            Tag value = tag.get(key);
            byte id = value.getId();
            output.writeByte(id);
            output.writeUTF(key);
            writeValue(id, key, tag, value, output);
        }
        output.writeByte(Tag.TAG_END);
    }

    private static void writeValue(byte id, String key, CompoundTag owner, Tag value, DataOutput output)
            throws IOException {
        switch (id) {
            case Tag.TAG_BYTE -> output.writeByte((Byte) CompoundTag.scalarValue(value));
            case Tag.TAG_INT -> output.writeInt((Integer) CompoundTag.scalarValue(value));
            case Tag.TAG_LONG -> output.writeLong((Long) CompoundTag.scalarValue(value));
            case Tag.TAG_DOUBLE -> output.writeDouble((Double) CompoundTag.scalarValue(value));
            case Tag.TAG_STRING -> output.writeUTF(((StringTag) value).getAsString());
            case Tag.TAG_LIST -> writeList((ListTag) value, output);
            case Tag.TAG_COMPOUND -> writeCompoundPayload((CompoundTag) value, output);
            case Tag.TAG_INT_ARRAY -> writeUuid(owner.getUUID(key), output);
            default -> throw new IOException("Unsupported NBT tag id " + id);
        }
    }

    private static void writeList(ListTag list, DataOutput output) throws IOException {
        byte elementType = list.isEmpty() ? 0 : list.get(0).getId();
        output.writeByte(elementType);
        output.writeInt(list.size());
        for (Tag element : list) {
            writeListElement(elementType, element, output);
        }
    }

    private static void writeListElement(byte id, Tag value, DataOutput output) throws IOException {
        switch (id) {
            case Tag.TAG_BYTE -> output.writeByte((Byte) CompoundTag.scalarValue(value));
            case Tag.TAG_INT -> output.writeInt((Integer) CompoundTag.scalarValue(value));
            case Tag.TAG_LONG -> output.writeLong((Long) CompoundTag.scalarValue(value));
            case Tag.TAG_DOUBLE -> output.writeDouble((Double) CompoundTag.scalarValue(value));
            case Tag.TAG_STRING -> output.writeUTF(((StringTag) value).getAsString());
            case Tag.TAG_COMPOUND -> writeCompoundPayload((CompoundTag) value, output);
            default -> throw new IOException("Unsupported list element tag id " + id);
        }
    }

    private static void writeUuid(UUID value, DataOutput output) throws IOException {
        long most = value.getMostSignificantBits();
        long least = value.getLeastSignificantBits();
        output.writeInt(4);
        output.writeInt((int) (most >>> 32));
        output.writeInt((int) most);
        output.writeInt((int) (least >>> 32));
        output.writeInt((int) least);
    }

    private static CompoundTag readCompoundPayload(DataInput input) throws IOException {
        CompoundTag tag = new CompoundTag();
        while (true) {
            byte id = input.readByte();
            if (id == Tag.TAG_END) {
                return tag;
            }
            String key = input.readUTF();
            readEntry(tag, key, id, input);
        }
    }

    private static void readEntry(CompoundTag owner, String key, byte id, DataInput input) throws IOException {
        switch (id) {
            case Tag.TAG_BYTE -> owner.put(key, CompoundTag.scalar(Tag.TAG_BYTE, input.readByte()));
            case Tag.TAG_INT -> owner.put(key, CompoundTag.scalar(Tag.TAG_INT, input.readInt()));
            case Tag.TAG_LONG -> owner.put(key, CompoundTag.scalar(Tag.TAG_LONG, input.readLong()));
            case Tag.TAG_DOUBLE -> owner.put(key, CompoundTag.scalar(Tag.TAG_DOUBLE, input.readDouble()));
            case Tag.TAG_STRING -> owner.put(key, StringTag.valueOf(input.readUTF()));
            case Tag.TAG_LIST -> owner.put(key, readList(input));
            case Tag.TAG_COMPOUND -> owner.put(key, readCompoundPayload(input));
            case Tag.TAG_INT_ARRAY -> owner.putUUID(key, readUuid(input));
            default -> throw new IOException("Unsupported NBT tag id " + id);
        }
    }

    private static Tag readList(DataInput input) throws IOException {
        byte elementType = input.readByte();
        int size = input.readInt();
        if (size < 0) {
            throw new IOException("Negative NBT list size " + size);
        }
        ListTag list = new ListTag();
        for (int index = 0; index < size; index++) {
            list.add(readListElement(elementType, input));
        }
        return list;
    }

    private static Tag readListElement(byte id, DataInput input) throws IOException {
        return switch (id) {
            case Tag.TAG_BYTE -> CompoundTag.scalar(Tag.TAG_BYTE, input.readByte());
            case Tag.TAG_INT -> CompoundTag.scalar(Tag.TAG_INT, input.readInt());
            case Tag.TAG_LONG -> CompoundTag.scalar(Tag.TAG_LONG, input.readLong());
            case Tag.TAG_DOUBLE -> CompoundTag.scalar(Tag.TAG_DOUBLE, input.readDouble());
            case Tag.TAG_STRING -> StringTag.valueOf(input.readUTF());
            case Tag.TAG_COMPOUND -> readCompoundPayload(input);
            default -> throw new IOException("Unsupported list element tag id " + id);
        };
    }

    private static UUID readUuid(DataInput input) throws IOException {
        int length = input.readInt();
        if (length != 4) {
            throw new IOException("Unsupported UUID int array length " + length);
        }
        int mostHigh = input.readInt();
        int mostLow = input.readInt();
        int leastHigh = input.readInt();
        int leastLow = input.readInt();
        return new UUID(
                ((long) mostHigh << 32) | (mostLow & 0xFFFFFFFFL),
                ((long) leastHigh << 32) | (leastLow & 0xFFFFFFFFL));
    }
}
