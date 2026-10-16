package dev.propulsionteam.computed.content.monitors.widgets;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/**
 * Renderable element placed on a monitor. Each widget carries a stable {@link #id()} for hit-testing
 * and for routing user input from the client back to the originating node on the server.
 */
public sealed interface Widget permits
        TextWidget, ClockWidget, ButtonWidget, CheckboxWidget, SliderWidget, ProgressBarWidget,
        TerminalWidget, PanelWidget, TextBlockWidget, TableWidget, TextInputWidget, NumberWidget,
        LayoutManagedWidget {

    UUID id();

    int x();

    int y();

    int w();

    int h();

    WidgetType type();

    /** Encode body fields after the type ordinal + id + x/y/w/h have been written by {@link #write(FriendlyByteBuf)}. */
    void encodeBody(FriendlyByteBuf buf);

    default void write(FriendlyByteBuf buf) {
        buf.writeByte(type().ordinal());
        buf.writeUUID(id());
        buf.writeVarInt(x());
        buf.writeVarInt(y());
        buf.writeVarInt(w());
        buf.writeVarInt(h());
        encodeBody(buf);
    }

    static Widget read(FriendlyByteBuf buf) {
        WidgetType type = WidgetType.byOrdinal(buf.readByte() & 0xFF);
        return type.decode(buf);
    }

    /** Helper used by record decoders to consume the common header. Returns [id, x, y, w, h]. */
    static Header readHeader(FriendlyByteBuf buf) {
        return new Header(buf.readUUID(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    record Header(UUID id, int x, int y, int w, int h) {}
}
