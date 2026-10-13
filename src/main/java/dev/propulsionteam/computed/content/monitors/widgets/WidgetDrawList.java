package dev.propulsionteam.computed.content.monitors.widgets;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Ordered set of widgets to draw on a monitor. Computes a stable hash so the block entity can avoid
 * sending a sync packet when nothing visually changed between ticks.
 */
public final class WidgetDrawList {
    public static final WidgetDrawList EMPTY = new WidgetDrawList(List.of());

    private final List<Widget> widgets;
    private final long hash;

    public WidgetDrawList(List<Widget> widgets) {
        this.widgets = Collections.unmodifiableList(new ArrayList<>(widgets));
        this.hash = computeHash(this.widgets);
    }

    public List<Widget> widgets() { return widgets; }

    public long hash() { return hash; }

    public boolean isEmpty() { return widgets.isEmpty(); }

    private static long computeHash(List<Widget> ws) {
        if (ws.isEmpty()) return 0L;
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            for (Widget w : ws) w.write(buf);
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            long h = 1469598103934665603L;
            for (byte b : bytes) {
                h ^= (b & 0xFFL);
                h *= 1099511628211L;
            }
            return h;
        } finally {
            buf.release();
        }
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(widgets.size());
        for (Widget w : widgets) w.write(buf);
    }

    public static WidgetDrawList read(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<Widget> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) list.add(Widget.read(buf));
        return new WidgetDrawList(list);
    }

    /** NBT representation for chunk-load round trip. Stored as a raw byte array of the wire encoding. */
    public CompoundTag toNbt() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            write(buf);
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            CompoundTag tag = new CompoundTag();
            tag.putByteArray("Bytes", bytes);
            return tag;
        } finally {
            buf.release();
        }
    }

    public static WidgetDrawList fromNbt(CompoundTag tag) {
        if (tag == null || !tag.contains("Bytes")) return EMPTY;
        byte[] bytes = tag.getByteArray("Bytes");
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
        try {
            return read(buf);
        } finally {
            buf.release();
        }
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WidgetDrawList other)) return false;
        return hash == other.hash && Objects.equals(widgets, other.widgets);
    }

    @Override public int hashCode() { return (int) (hash ^ (hash >>> 32)); }
}
