package dev.propulsionteam.computed.content.monitors.widgets;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public record TextInputWidget(
        UUID id, int x, int y, int w, int h, String placeholder, String value, int colorArgb)
        implements Widget {
    @Override public WidgetType type() { return WidgetType.TEXT_INPUT; }

    @Override
    public void encodeBody(FriendlyByteBuf buf) {
        buf.writeUtf(placeholder == null ? "" : placeholder, 256);
        buf.writeUtf(value == null ? "" : value, 256);
        buf.writeInt(colorArgb);
    }

    public static TextInputWidget decode(FriendlyByteBuf buf) {
        Header h = Widget.readHeader(buf);
        String placeholder = buf.readUtf(256);
        String value = buf.readUtf(256);
        int color = buf.readInt();
        return new TextInputWidget(h.id(), h.x(), h.y(), h.w(), h.h(), placeholder, value, color);
    }
}
